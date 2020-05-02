package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder("as_of")
public class DataPoint {

    @JsonProperty("as_of")
    private LocalDate asOf;
    private Integer cases;
    private Integer deaths;

}
