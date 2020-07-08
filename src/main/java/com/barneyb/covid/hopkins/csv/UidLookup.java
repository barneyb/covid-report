package com.barneyb.covid.hopkins.csv;

import com.barneyb.covid.model.Area;
import com.opencsv.bean.CsvBindByName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = { "uid" })
public class UidLookup implements Area {

    @CsvBindByName(column = "UID")
    private int uid;

    @CsvBindByName(column = "iso2")
    private String iso2;
    @CsvBindByName(column = "iso3")
    @ToString.Exclude
    private String iso3;
    @CsvBindByName(column = "code3")
    @ToString.Exclude
    private String code3;
    @CsvBindByName(column = "FIPS")
    @ToString.Exclude
    private String fips;

    @CsvBindByName(column = "Country_Region")
    private String country;
    @CsvBindByName(column = "Province_State")
    private String state;
    @CsvBindByName(column = "Admin2")
    private String locality;

    @CsvBindByName(column = "Lat")
    @ToString.Exclude
    private Double latitude;
    @CsvBindByName(column = "Long_")
    @ToString.Exclude
    private Double longitude;

    @CsvBindByName(column = "Combined_Key")
    @ToString.Exclude
    private String combinedKey;

    @CsvBindByName(column = "Population")
    private long population;

    /**
     * I indicate whether this area corresponds ot a concrete physical location
     * (or aggregate thereof), and is not a "completeness" area used to avoid
     * losing instance data in the face of incomplete demographic data. Both
     * concrete and non-concrete areas must be included to create a correct
     * aggregate, but only concrete areas are generally interesting to users.
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
    public int getId() {
        return uid;
    }

    @Override
    public String getName() {
        return combinedKey;
    }

}
