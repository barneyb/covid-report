package com.barneyb.cdccovid.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataPoint {

    private Integer cases;
    private Integer deaths;

}
