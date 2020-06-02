package com.barneyb.cdccovid.hopkins.csv;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

@Data
public class Demographics {

    private String label;

    @CsvBindByName(column = "UID")
    private int uid;
    @CsvBindByName(column = "FIPS")
    private String fips;

    @CsvBindByName(column = "Country_Region")
    private String country;
    @CsvBindByName(column = "Province_State")
    private String state;
    @CsvBindByName(column = "Admin2")
    private String local;

    @CsvBindByName(column = "Combined_Key")
    private String combinedKey;

    @CsvBindByName(column = "Population")
    private long population;

    public String getLabel() {
        if (label != null) return label;
        return combinedKey;
    }

    public boolean isCompleteness() {
        return isLocal()
                && ("Unassigned".equals(local) || local.startsWith("Out of "));
    }

    public boolean isCountry() {
        return state == null && local == null;
    }

    public boolean isState() {
        return state != null && local == null;
    }

    public boolean isLocal() {
        return local != null;
    }

}
