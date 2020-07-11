package com.barneyb.covid.hopkins.csv;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GlobalTimeSeries implements CsvTimeSeries {

    // Province/State,Country/Region,Lat,Long,1/22/20,1/23/20,1/24/20,1/25/20

    @CsvBindByName(column = "Country/Region")
    private String country;

    @CsvBindByName(column = "Province/State")
    private String state;

    @CsvBindByName(column = "Lat")
    private Double latitude;
    @CsvBindByName(column = "Long")
    private Double longitude;

    private LocalDate[] dateSequence;

    private int[] data;

    public boolean isCountry() {
        return state == null;
    }

}
