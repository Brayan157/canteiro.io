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
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenerateMeasurementSpreadsheetService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final MeasurementRepository measurementRepository;
    private final MeasurementVersionRepository measurementVersionRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final MeasurementDiscountRepository measurementDiscountRepository;
    private final MeasurementSpreadsheetGenerator spreadsheetGenerator;
    private final AuditEventRecorder auditEventRecorder;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public GeneratedMeasurementSpreadsheet generate(UUID measurementId, UUID measurementVersionId) {
        var tenant = tenantContextHolder.requireCurrentTenant();
        authorizationService.requirePermission(AccessModule.MEASUREMENTS, AccessAction.EXPORT);

        Measurement measurement = measurementRepository.findByIdAndCompanyId(measurementId, tenant.companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement"));
        MeasurementVersion version = measurementVersionRepository.findByIdAndCompanyId(measurementVersionId, tenant.companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Measurement version"));
        if (!measurement.getId().equals(version.getMeasurementId())) {
            throw new TenantResourceNotFoundException("Measurement version");
        }

        MeasurementDiscount discount = measurementDiscountRepository
                .findByMeasurementVersionIdAndCompanyId(version.getId(), tenant.companyId())
                .orElse(null);
        String filename = "measurement-" + measurement.getId() + "-v" + version.getVersionNumber() + ".xlsx";
        String correlationId = MDC.get("X-Correlation-Id");
        MeasurementExportData data = new MeasurementExportData(measurement, version, discount);
        return new GeneratedMeasurementSpreadsheet(filename, output -> write(tenant, correlationId, data, output, filename));
    }

    private void write(TenantContext tenant, String correlationId, MeasurementExportData data,
                       java.io.OutputStream output, String filename) throws java.io.IOException {
        tenantContextHolder.setCurrentTenant(tenant);
        if (correlationId != null) {
            MDC.put("X-Correlation-Id", correlationId);
        }
        try {
            MeasurementExportResult result = spreadsheetGenerator.write(data, pagedItems(tenant, data.version().getId()), output);
            transactionTemplate.executeWithoutResult(status -> auditEventRecorder.recordDirectAction(
                    AuditModule.MEASUREMENTS, AuditAction.EXPORT, "MeasurementVersion", data.version().getId(), null,
                    Map.of("measurementId", data.measurement().getId().toString(),
                            "versionNumber", data.version().getVersionNumber(), "itemCount", result.itemCount(),
                            "netAmount", result.amounts().netAmount()),
                    Map.of("format", "XLSX", "filename", filename, "streamed", true)
            ));
        } finally {
            tenantContextHolder.clear();
            MDC.remove("X-Correlation-Id");
        }
    }

    private MeasurementItemSource pagedItems(TenantContext tenant, UUID versionId) {
        return consumer -> {
            int pageNumber = 0;
            org.springframework.data.domain.Page<MeasurementItem> page;
            do {
                page = measurementItemRepository.findByMeasurementVersionIdAndCompanyId(versionId, tenant.companyId(),
                        PageRequest.of(pageNumber++, 250, Sort.by(Sort.Order.asc("itemNumber"), Sort.Order.asc("id"))));
                for (MeasurementItem item : page) {
                    consumer.accept(item);
                }
            } while (page.hasNext());
        };
    }
}
