package com.barneyb.covid.model;

import com.barneyb.covid.hopkins.csv.CsvTimeSeries;
import com.barneyb.covid.hopkins.csv.UidLookup;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = { "area" })
@ToString
public class RawSeries implements Series {

    @NonNull
    UidLookup area;

    @NonNull
    @ToString.Exclude
    int[] cases;

    @ToString.Exclude
    int[] deaths;

    public RawSeries(UidLookup area, CsvTimeSeries caseSeries, CsvTimeSeries deathSeries) {
        this.area = area;
        this.cases = caseSeries.getData();
        this.deaths = deathSeries.getData();
        assert this.cases.length == this.deaths.length;
    }

    @Override
    public int getSegmentCount() {
        return 1;
    }

    @ToString.Include(name = "currentCases")
    int getCurrentCases() {
        return getCasesDaysAgo(0);
    }

    @ToString.Include(name = "currentDeaths")
    int getCurrentDeaths() {
        return getDeathsDaysAgo(0);
    }

}
