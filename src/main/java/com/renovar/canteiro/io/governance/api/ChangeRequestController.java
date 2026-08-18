package com.renovar.canteiro.io.governance.api;
import com.renovar.canteiro.io.governance.api.request.ChangeRequestDecisionRequest;
import com.renovar.canteiro.io.governance.api.response.ChangeRequestDecisionResponse;
import com.renovar.canteiro.io.governance.application.ApproveChangeRequestCommand;
import com.renovar.canteiro.io.governance.application.ChangeRequestDecisionService;
import com.renovar.canteiro.io.governance.application.RejectChangeRequestCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/company/change-requests") @RequiredArgsConstructor
public class ChangeRequestController {
    private final ChangeRequestDecisionService service;
    @PostMapping("/{id}/approve") public ChangeRequestDecisionResponse approve(@PathVariable UUID id, @RequestBody ChangeRequestDecisionRequest request) { var r = service.approve(new ApproveChangeRequestCommand(id, request.decisionReason())); return new ChangeRequestDecisionResponse(r.getId(), r.getStatus()); }
    @PostMapping("/{id}/reject") public ChangeRequestDecisionResponse reject(@PathVariable UUID id, @RequestBody ChangeRequestDecisionRequest request) { var r = service.reject(new RejectChangeRequestCommand(id, request.decisionReason())); return new ChangeRequestDecisionResponse(r.getId(), r.getStatus()); }
}
