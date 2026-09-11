package com.renovar.canteiro.io.employees.domain;

import java.util.UUID;

public interface EmployeeRegistrationNumberGenerator {
    String nextRegistrationNumber(UUID companyId, String companyName);
}
