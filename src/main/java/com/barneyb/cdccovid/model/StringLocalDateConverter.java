package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.databind.util.StdConverter;

import java.time.LocalDate;

public class StringLocalDateConverter extends StdConverter<LocalDate, String> {
    @Override
    public String convert(LocalDate value) {
        return value.toString();
    }
}
