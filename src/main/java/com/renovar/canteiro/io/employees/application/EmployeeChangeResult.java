package com.renovar.canteiro.io.employees.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;

public record EmployeeChangeResult(Employee employee, ChangeRequest changeRequest, ChangeAuthorizationMode mode) {
}
