package com.barneyb.covid.hopkins.csv;

import lombok.val;
import org.apache.commons.collections4.MultiValuedMap;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public interface CsvTimeSeries {

    DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yy");

    MultiValuedMap<String, Integer> getDateStringMultimap();

    default int getDataPoint(String date) {
        val values = getDateStringMultimap().get(date);
        return values.isEmpty() ? 0 : values.iterator().next();
    }

    default String[] getDateHeaderSequence() {
        val dates = getDateSequence();
        val dateHeaders = new String[dates.length];
        for (int i = 0, l = dates.length; i < l; i++) {
            dateHeaders[i] = dates[i].format(CsvTimeSeries.DATE_FORMAT);
        }
        return dateHeaders;
    }

    default LocalDate[] getDateSequence() {
        return getDateStringMultimap()
                .keySet()
                .stream()
                .map(s -> DATE_FORMAT.parse(s, LocalDate::from))
                .sorted()
                .toArray(LocalDate[]::new);
    }

    default int[] getData() {
        val result = new int[getDateStringMultimap().size()];
        val dates = getDateSequence();
        for (int i = 0, l = result.length; i < l; i++) {
            result[i] = getDataPoint(DATE_FORMAT.format(dates[i]));
        }
        return result;
    }

}
