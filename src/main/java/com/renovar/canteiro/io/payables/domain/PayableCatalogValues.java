package com.renovar.canteiro.io.payables.domain;

import java.util.Locale;

public final class PayableCatalogValues {
    private PayableCatalogValues() { }
    public static String text(String value, int maximum, boolean required) {
        String result=value==null||value.isBlank()?null:value.trim();
        if(required&&result==null||result!=null&&result.length()>maximum) throw new IllegalArgumentException("Invalid payable catalog text length");
        return result;
    }
    public static String document(String value) {
        if(value==null||value.isBlank())return null;
        if(value.length()>80||!value.matches("[A-Za-z0-9 ./\\-]+"))throw new IllegalArgumentException("Supplier document must contain only letters, digits and formatting separators");
        String result=value.replaceAll("[^A-Za-z0-9]","").toUpperCase(Locale.ROOT);
        if(result.isEmpty()||result.length()>50)throw new IllegalArgumentException("Invalid supplier document length");
        return result;
    }
    public static String code(String value) {
        String result=text(value,50,true).toUpperCase(Locale.ROOT);
        if(!result.matches("[A-Z0-9][A-Z0-9_-]{0,49}"))throw new IllegalArgumentException("Category code must contain letters, digits, underscore or hyphen");
        return result;
    }
}
