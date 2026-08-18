package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.governance.domain.ChangeRequest;

public interface ChangeRequestApprovalHandler {

    boolean supports(ChangeRequest changeRequest);

    void apply(ChangeRequest changeRequest);
}
