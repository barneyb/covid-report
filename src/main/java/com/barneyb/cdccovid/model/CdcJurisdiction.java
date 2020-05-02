package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CdcJurisdiction {

    @JsonProperty("Jurisdiction")
    private String name;
    @JsonProperty("Range")
    private String range;
    @JsonProperty("Cases Reported")
    private Integer cases;
    @JsonProperty("Deaths")
    private Integer deaths;
    @JsonProperty("Community Transmission")
    private String community;
    @JsonProperty("URL")
    private String url;

}
