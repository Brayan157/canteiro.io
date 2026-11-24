package com.renovar.canteiro.io.payables.infrastructure;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.payables.domain.PayablePaymentInstruction;
import com.renovar.canteiro.io.payables.domain.PayablePaymentInstructionRepository;
import com.renovar.canteiro.io.payables.domain.PayablePaymentInstructionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository @RequiredArgsConstructor
public class JdbcPayablePaymentInstructionRepository implements PayablePaymentInstructionRepository {
    private final JdbcTemplate jdbc;
    @Override public void lockPayable(UUID company,UUID payable){
        if(jdbc.queryForList("select id from payable where company_id=? and id=? for update",company,payable).isEmpty())
            throw new TenantResourceNotFoundException("Payable");
    }
    @Override public Optional<PayablePaymentInstruction> latest(UUID company,UUID payable){
        return jdbc.query("select * from payable_payment_instruction where company_id=? and payable_id=? order by version desc limit 1",
                (rs,row)->new PayablePaymentInstruction(rs.getObject("id",UUID.class),company,payable,rs.getLong("version"),
                        PayablePaymentInstructionType.valueOf(rs.getString("type")),rs.getString("ciphertext"),rs.getString("pdf_storage_key"),
                        rs.getString("pdf_sha256"),rs.getObject("pdf_size",Long.class),rs.getObject("created_by",UUID.class),
                        rs.getTimestamp("created_at").toInstant()),company,payable).stream().findFirst();
    }
    @Override public PayablePaymentInstruction save(PayablePaymentInstruction r){
        jdbc.update("insert into payable_payment_instruction(id,company_id,payable_id,version,type,ciphertext,pdf_storage_key,pdf_sha256,pdf_size,created_by,created_at) values (?,?,?,?,?,?,?,?,?,?,?)",
                r.id(),r.companyId(),r.payableId(),r.version(),r.type().name(),r.ciphertext(),r.pdfStorageKey(),r.pdfSha256(),r.pdfSize(),r.createdBy(),java.sql.Timestamp.from(r.createdAt()));
        return r;
    }
}
