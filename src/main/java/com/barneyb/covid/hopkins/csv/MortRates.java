package com.barneyb.covid.hopkins.csv;

import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvNumber;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MortRates {

    @CsvBindByName(column = "STATE")
    private String state;
    @CsvBindByName(column = "POPULATION")
    private long population;

    @CsvBindAndJoinByName(column = ".*", elementType = Double.class, mapType = HashSetValuedHashMap.class)
    @CsvNumber("0.##")
    private MultiValuedMap<String, Double> rates = new HashSetValuedHashMap<>();

    public Map<String, Double> unwrapRates() {
        val r = new HashMap<String, Double>();
        for (var itr = rates.mapIterator(); itr.hasNext();) {
            r.put(itr.next(), itr.getValue());
        }
        return r;
    }
}
