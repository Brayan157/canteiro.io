package com.renovar.canteiro.io.platform.support.application;

import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.User;

public record PlatformSupportUser(User user, PlatformUser platformUser) {
}
