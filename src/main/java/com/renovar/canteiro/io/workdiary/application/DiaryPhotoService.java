package com.renovar.canteiro.io.workdiary.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import com.renovar.canteiro.io.workdiary.domain.DiaryPhoto;
import com.renovar.canteiro.io.workdiary.domain.DiaryPhotoRepository;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import com.renovar.canteiro.io.workforce.domain.WorkforceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiaryPhotoService {
    private final TenantContextHolder tenants;
    private final WorkforceRepository workforce;
    private final AccessAuthorizationService authorization;
    private final DiaryPhotoRepository photos;
    private final StorageProvider storage;
    private final AuditEventRecorder audit;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UploadResult upload(UUID work, LocalDate date, UploadCommand command) {
        var tenant = requireWriteAccess(work);
        UUID company = tenant.companyId();
        var day = day(company, work, date);
        var target = command.target();
        if (!target.hasItem() || target.hasBoth()) {
            throw invalid("Choose exactly one diary activity or occurrence for the photo");
        }
        var previous = photos.findByOperation(company, work, day.id(), command.operationId()).orElse(null);
        if (previous != null) {
            var content = DiaryPhotoContent.read(command.stream());
            if (previous.revision() != command.expectedRevision() || !previous.uploadedBy().equals(tenant.userId())
                    || !previous.target().equals(target) || !previous.sha256().equals(content.sha256())) {
                throw conflict("The photo operation ID is already used");
            }
            return new UploadResult(previous, true);
        }
        requireOpenRevision(day, command.expectedRevision());
        if (!photos.itemExists(company, work, day.id(), day.revision(), target)) {
            throw notFound();
        }
        var content = DiaryPhotoContent.read(command.stream());
        var photo = new DiaryPhoto(UUID.randomUUID(), day.revision(), command.operationId(), content.contentType(),
                content.bytes().length, content.sha256(), tenant.userId(), clock.instant(), null, null, null, target);
        String key = storageKey(company, day.id(), photo.id());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storage.delete(key);
                }
            }
        });
        storage.store(key, content.bytes());
        photos.insert(company, work, day.id(), photo);
        record(AuditAction.CREATE, work, day.id(), photo);
        return new UploadResult(photo, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<DiaryPhoto> list(UUID work, LocalDate date, Long revision, DiaryPhoto.Target target, int offset, int limit) {
        UUID company = requireReadAccess(work).companyId();
        var day = day(company, work, date);
        if (target.hasBoth()) {
            throw invalid("Filter by either a diary activity or an occurrence");
        }
        if (revision != null && !photos.revisionExists(company, work, day.id(), revision)) {
            throw notFound();
        }
        if (revision == null && target.hasItem() && !photos.itemExists(company, work, day.id(), day.revision(), target)) {
            return List.of();
        }
        return photos.list(company, work, day.id(), revision, target, revision != null, offset, limit);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ContentResult load(UUID work, LocalDate date, UUID photoId) {
        UUID company = requireReadAccess(work).companyId();
        var day = day(company, work, date);
        var photo = photos.find(company, work, day.id(), photoId)
                .filter(found -> found.withdrawnAt() == null).orElseThrow(DiaryPhotoService::notFound);
        byte[] bytes = storage.load(storageKey(company, day.id(), photo.id()));
        if (bytes.length != photo.sizeBytes() || !DiaryPhotoContent.digest(bytes).equals(photo.sha256())) {
            throw new IllegalStateException("Private diary photo does not match its recorded digest");
        }
        record(AuditAction.VIEW_SENSITIVE, work, day.id(), photo);
        return new ContentResult(bytes, photo.contentType());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DiaryPhoto withdraw(UUID work, LocalDate date, UUID photoId, long expectedRevision, String reason) {
        var tenant = requireWriteAccess(work);
        UUID company = tenant.companyId();
        var day = day(company, work, date);
        String explanation = reason.strip();
        var photo = photos.find(company, work, day.id(), photoId).orElseThrow(DiaryPhotoService::notFound);
        if (photo.withdrawnAt() != null) {
            if (tenant.userId().equals(photo.withdrawnBy()) && explanation.equals(photo.withdrawalReason())) {
                return photo;
            }
            throw conflict("The diary photo has already been withdrawn");
        }
        requireOpenRevision(day, expectedRevision);
        photos.withdraw(company, work, day.id(), photoId, tenant.userId(), clock.instant(), explanation);
        var withdrawn = photos.find(company, work, day.id(), photoId).orElseThrow(DiaryPhotoService::notFound);
        record(AuditAction.DEACTIVATE, work, day.id(), withdrawn);
        return withdrawn;
    }

    private TenantContext requireWriteAccess(UUID work) {
        var tenant = tenants.requireCurrentTenant();
        // Diary edits, work grants and photo writes share the work lock.
        if (!workforce.lockWork(tenant.companyId(), work)
                || !workforce.hasGrant(tenant.companyId(), work, tenant.userId(), WorkOperation.UPDATE, clock.instant())) {
            throw notFound();
        }
        if (authorization.requireChangeAuthorization(AccessModule.WORK_DIARY, ChangeOperation.UPDATE)
                != ChangeAuthorizationMode.DIRECT) {
            throw new AccessDeniedException("Diary photo writes require direct update authority");
        }
        return tenant;
    }

    private TenantContext requireReadAccess(UUID work) {
        var tenant = tenants.requireCurrentTenant();
        if (!workforce.hasGrant(tenant.companyId(), work, tenant.userId(), WorkOperation.READ, clock.instant())) {
            throw notFound();
        }
        authorization.requirePermission(AccessModule.WORK_DIARY, AccessAction.READ);
        return tenant;
    }

    private DiaryPhotoRepository.Day day(UUID company, UUID work, LocalDate date) {
        return photos.findDay(company, work, date).orElseThrow(DiaryPhotoService::notFound);
    }

    private static void requireOpenRevision(DiaryPhotoRepository.Day day, long expected) {
        if (day.closed() || day.revision() != expected) {
            throw conflict("An open diary and its current revision are required");
        }
    }

    private void record(AuditAction action, UUID work, UUID diary, DiaryPhoto photo) {
        var metadata = new HashMap<String, Object>(Map.of("workId", work.toString(), "diaryId", diary.toString(),
                "revision", photo.revision()));
        if (photo.target().activityId() != null) {
            metadata.put("activityId", photo.target().activityId().toString());
        }
        if (photo.target().occurrenceId() != null) {
            metadata.put("occurrenceId", photo.target().occurrenceId().toString());
        }
        audit.recordDirectAction(AuditModule.WORK_DIARY, action, "DiaryPhoto", photo.id(), null, null, metadata);
    }

    private static String storageKey(UUID company, UUID diary, UUID photo) {
        return "companies/" + company + "/work-diaries/" + diary + "/photos/" + photo;
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, detail);
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION, detail);
    }

    private static TenantResourceNotFoundException notFound() {
        return new TenantResourceNotFoundException("Diary photo or work reference");
    }

    public record UploadCommand(UUID operationId, long expectedRevision, InputStream stream, DiaryPhoto.Target target) {
    }

    public record UploadResult(DiaryPhoto photo, boolean replay) {
    }

    public record ContentResult(byte[] bytes, String contentType) {
    }
}
