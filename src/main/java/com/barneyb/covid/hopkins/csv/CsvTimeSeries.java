package com.barneyb.covid.hopkins.csv;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public interface CsvTimeSeries {

    DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yy");

    default LocalDate getTodaysDate() {
        final var seq = getDateSequence();
        return seq[seq.length - 1];
    }

    LocalDate[] getDateSequence();
    void setDateSequence(LocalDate[] dateSequence);

    int[] getData();
    void setData(int[] data);

}
