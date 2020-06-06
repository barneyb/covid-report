package com.barneyb.cdccovid.hopkins.csv;

import lombok.val;
import org.apache.commons.collections4.MultiValuedMap;

public interface CsvTimeSeries {

    MultiValuedMap<String, Double> getDates();

    default double getDataPoint(String date) {
        val values = getDates().get(date);
        return values.isEmpty() ? 0 : values.iterator().next();
    }

}
