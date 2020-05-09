package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CdcJurisdiction {

    @JsonProperty("Jurisdiction")
    private String name;
    @JsonProperty("Range")
    private String range;
    @JsonProperty("Cases Reported")
    private Object cases;
    @JsonProperty("Deaths")
    private Object deaths;
    @JsonProperty("Community Transmission")
    private String community;
    @JsonProperty("URL")
    private String url;

    @JsonIgnore
    public Integer getCaseCount() {
        return (Integer) cases;
    }

    @JsonIgnore
    public Integer getDeathCount() {
        return (Integer) deaths;
    }

}
