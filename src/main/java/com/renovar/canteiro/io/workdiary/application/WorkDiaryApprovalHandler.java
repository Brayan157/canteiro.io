package com.renovar.canteiro.io.workdiary.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import org.springframework.stereotype.Component;

@Component
public class WorkDiaryApprovalHandler implements ChangeRequestApprovalHandler {
    private final WorkDiaryService diaries;
    private final DiaryPlanService plans;

    public WorkDiaryApprovalHandler(WorkDiaryService diaries, DiaryPlanService plans) {
        this.diaries = diaries;
        this.plans = plans;
    }

    @Override
    public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.WORK_DIARY
                && (WorkDiaryService.ENTITY_TYPE.equals(request.getEntityType())
                    || DiaryPlanService.ENTITY_TYPE.equals(request.getEntityType()));
    }

    @Override
    public void apply(ChangeRequest request) {
        if (DiaryPlanService.ENTITY_TYPE.equals(request.getEntityType())) {
            plans.applyApproved(request);
        } else {
            diaries.applyApproved(request);
        }
    }
}
