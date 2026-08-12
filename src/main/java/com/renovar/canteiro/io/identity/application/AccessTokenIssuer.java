package com.renovar.canteiro.io.identity.application;

import com.renovar.canteiro.io.identity.domain.User;

public interface AccessTokenIssuer {

    String issue(User user);
}
