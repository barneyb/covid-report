package com.barneyb.covid.hopkins.csv;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

import java.time.LocalDate;

@Data
public class USTimeSeries implements CsvTimeSeries {

    // UID,iso2,iso3,code3,FIPS,Admin2,Province_State,Country_Region,Lat,Long_,Combined_Key,1/22/20,1/23/20,1/24/20,1/25/20

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

    @CsvBindByName(column = "Province_State")
    private String state;

    @CsvBindByName(column = "Admin2")
    private String locality;

    @CsvBindByName(column = "Lat")
    private Double latitude;
    @CsvBindByName(column = "Long_")
    private Double longitude;

    private LocalDate[] dateSequence;

    private int[] data;

}
