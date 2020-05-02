package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataPoint {

    @JsonSerialize(converter = StringLocalDateConverter.class)
    @JsonDeserialize(converter = LocalDateStringConverter.class)
    private LocalDate asOf;
    private Integer cases;
    private Integer deaths;

}
