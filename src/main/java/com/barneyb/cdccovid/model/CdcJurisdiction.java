package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CdcJurisdiction {

    @JsonProperty("Jurisdiction")
    private String jurisdiction;
    @JsonProperty("Range")
    private String range;
    @JsonProperty("Cases Reported")
    private int cases;
    @JsonProperty("Deaths")
    private int deaths;
    @JsonProperty("Community Transmission")
    private String community;
    @JsonProperty("URL")
    private String url;

}
