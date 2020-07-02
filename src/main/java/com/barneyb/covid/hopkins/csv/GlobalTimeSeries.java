package com.barneyb.covid.hopkins.csv;

import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.CsvBindByName;
import lombok.Data;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

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

    public void setState(String state) {
        // todo: use the web branch's overrides?
        if ("Hong Kong".equals(state) || "Macau".equals(state)) {
            state += " SAR";
        }
        this.state = state;
    }

    // MVM is a bit silly, since the column names are all different, but both
    // this use case and aggregating multiple same-named columns are provided by
    // the same annotation. C'est la vie.
    @CsvBindAndJoinByName(column = "[0-9]+/[0-9]+/[0-9]+", elementType = Integer.class, mapType = HashSetValuedHashMap.class)
    private MultiValuedMap<String, Integer> dateStringMultimap;

    public boolean isCountry() {
        return state == null;
    }

}
