package com.barneyb.cdccovid.hopkins.csv;

import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.CsvBindByName;
import lombok.Data;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

@Data
public class USTimeSeries implements CsvTimeSeries {

    // UID,iso2,iso3,code3,FIPS,Admin2,Province_State,Country_Region,Lat,Long_,Combined_Key,1/22/20,1/23/20,1/24/20,1/25/20

    @CsvBindByName(column = "UID")
    private int uid;

    @CsvBindByName(column = "Province_State")
    private String state;

    @CsvBindByName(column = "Admin2")
    private String locality;

    // MVM is a bit silly, since the column names are all different, but both
    // this use case and aggregating multiple same-named columns are provided by
    // the same annotation. C'est la vie.
    @CsvBindAndJoinByName(column = "[0-9]+/[0-9]+/[0-9]+", elementType = Double.class, mapType = HashSetValuedHashMap.class)
    private MultiValuedMap<String, Double> dates;

}
