package com.renovar.canteiro.io.shared.api.filter;

public record FilterCriterion(String field, FilterOperator operator, String value) {
}
