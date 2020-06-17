package com.barneyb.covid.hopkins.csv;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;
import org.apache.commons.lang3.builder.CompareToBuilder;

@Data
public class Demographics implements Comparable<Demographics> {

    @CsvBindByName(column = "UID")
    private int uid;
    @CsvBindByName(column = "FIPS")
    private String fips;

    @CsvBindByName(column = "Country_Region")
    private String country;
    @CsvBindByName(column = "Province_State")
    private String state;
    @CsvBindByName(column = "Admin2")
    private String locality;

    @CsvBindByName(column = "Combined_Key")
    private String combinedKey;

    @CsvBindByName(column = "Population")
    private long population;

    public boolean isCompleteness() {
        return isLocality()
                && ("Unassigned".equals(locality) || locality.startsWith("Out of "));
    }

    public boolean isCountry() {
        return state == null && locality == null;
    }

    public boolean isState() {
        return state != null && locality == null;
    }

    public boolean isLocality() {
        return locality != null;
    }

    @Override
    public int compareTo(Demographics o) {
        return new CompareToBuilder()
                .append(country, o.country)
                .append(state, o.state)
                .append(locality, o.locality)
                .toComparison();
    }
}
