package com.barneyb.covid.model;

import com.barneyb.covid.hopkins.csv.CsvTimeSeries;
import com.barneyb.covid.hopkins.csv.UidLookup;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = { "area" })
@ToString
public class RawSeries implements Series {

    @NonNull
    UidLookup area;

    LocalDate todaysDate;

    @NonNull
    @ToString.Exclude
    int[] cases;

    @ToString.Exclude
    int[] deaths;

    public RawSeries(UidLookup area, LocalDate todaysDate, int[] cases, int[] deaths) {
        assert cases.length == deaths.length;
        this.area = area;
        this.todaysDate = todaysDate;
        this.cases = cases;
        this.deaths = deaths;

    }

    public RawSeries(UidLookup area, LocalDate todaysDate, CsvTimeSeries caseSeries, CsvTimeSeries deathSeries) {
        this(area, todaysDate, caseSeries.getData(), deathSeries.getData());
    }

    @Override
    public int getSegmentCount() {
        return 1;
    }

    @ToString.Include(name = "currentCases")
    public int getCurrentCases() {
        return getCasesDaysAgo(0);
    }

    @ToString.Include(name = "currentDeaths")
    public int getCurrentDeaths() {
        return getDeathsDaysAgo(0);
    }

}
