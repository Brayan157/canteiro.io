package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenerateMeasurementPdfService {
    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final MeasurementDiscountRepository measurementDiscountRepository;
    private final MeasurementPdfGenerator pdfGenerator;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public GeneratedMeasurementPdf generate(UUID measurementId, UUID measurementVersionId) {
        var tenant = tenantContextHolder.requireCurrentTenant();
        authorizationService.requirePermission(AccessModule.MEASUREMENTS, AccessAction.EXPORT);
        Measurement measurement = measurementRepository.findByIdAndCompanyId(measurementId, tenant.companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
        MeasurementVersion version = measurementVersionRepository.findByIdAndCompanyId(measurementVersionId, tenant.companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (!measurement.getId().equals(version.getMeasurementId())) {
            throw new TenantResourceNotFoundException("Measurement version");
        }
        List<MeasurementItem> items = measurementItemRepository
                .findByMeasurementVersionIdAndCompanyId(version.getId(), tenant.companyId());
        MeasurementDiscount discount = measurementDiscountRepository
                .findByMeasurementVersionIdAndCompanyId(version.getId(), tenant.companyId()).orElse(null);
        MeasurementVersionAmounts amounts = MeasurementVersionAmountCalculator.calculate(items, discount);
        byte[] content = pdfGenerator.generate(new MeasurementPdfData(measurement, version, items, discount, amounts));
        String filename = "measurement-" + measurement.getId() + "-v" + version.getVersionNumber() + ".pdf";
        auditEventRecorder.recordDirectAction(AuditModule.MEASUREMENTS, AuditAction.EXPORT, "MeasurementVersion", version.getId(),
                null, Map.of("measurementId", measurement.getId().toString(), "versionNumber", version.getVersionNumber(),
                        "itemCount", items.size(), "netAmount", amounts.netAmount()),
                Map.of("format", "PDF", "filename", filename));
        return new GeneratedMeasurementPdf(filename, content);
    }
}
