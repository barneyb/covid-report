package com.barneyb.covid.hopkins.csv;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;
import org.apache.commons.lang3.builder.CompareToBuilder;

@Data
public class UidLookup implements Comparable<UidLookup> {

    @CsvBindByName(column = "UID")
    private int uid;
    @CsvBindByName(column = "iso2")
    private String iso2;
    @CsvBindByName(column = "iso3")
    private String iso3;
    @CsvBindByName(column = "code3")
    private String code3;
    @CsvBindByName(column = "FIPS")
    private String fips;

    @CsvBindByName(column = "Country_Region")
    private String country;
    @CsvBindByName(column = "Province_State")
    private String state;
    @CsvBindByName(column = "Admin2")
    private String locality;

    @CsvBindByName(column = "Lat")
    private Double latitude;
    @CsvBindByName(column = "Long_")
    private Double longitude;

    @CsvBindByName(column = "Combined_Key")
    private String combinedKey;

    @CsvBindByName(column = "Population")
    private long population;

    /**
     * I indicate whether this UID corresponds to a concrete administrative area
     * and is not a "completeness" UID (used to avoid losing instance data in
     * the face of incomplete demographic data). Both concrete and non-concrete
     * UIDs must be included to create a correct aggregate, but only concrete
     * UIDs should generally be shown to users.
     */
    public boolean isConcrete() {
        return !isLocality()
                || (!"Unassigned".equals(locality) && !locality.startsWith("Out of "));
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
    public int compareTo(UidLookup o) {
        return new CompareToBuilder()
                .append(country, o.country)
                .append(state, o.state)
                .append(locality, o.locality)
                .toComparison();
    }
}
