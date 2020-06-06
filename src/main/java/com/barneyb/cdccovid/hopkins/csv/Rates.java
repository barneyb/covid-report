package com.barneyb.cdccovid.hopkins.csv;

import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import com.opencsv.bean.CsvNumber;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class Rates {

    // Date|Worldwide|US|US Except NY|New York, US|Oregon, US

    @CsvBindByName(column = "DATE") // down in the depths, this will be force uppercase :|
    @CsvDate("M/d")
    private LocalDate date;

    @CsvBindAndJoinByName(column = ".*", elementType = Double.class, mapType = HashSetValuedHashMap.class)
    @CsvNumber("0.##")
    private MultiValuedMap<String, Double> jurisdictions;

}
