package com.barneyb.cdccovid.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataPoint {

    private LocalDate asOf;
    private Integer cases;
    private Integer deaths;

}
