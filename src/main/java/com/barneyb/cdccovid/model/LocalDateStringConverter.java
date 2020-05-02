package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.databind.util.StdConverter;

import java.time.LocalDate;

public class LocalDateStringConverter extends StdConverter<String, LocalDate> {
    @Override
    public LocalDate convert(String value) {
        return LocalDate.parse(value);
    }
}
