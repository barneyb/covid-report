package com.barneyb.covid.hopkins;

import com.barneyb.covid.Store;
import com.barneyb.covid.hopkins.csv.Demographics;
import com.barneyb.covid.model.DataPoint;
import com.barneyb.covid.model.Jurisdiction;
import lombok.val;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StoreBuilder<I> {

    final LocalDate[] dates;
    final I cases;
    final I deaths;
    final Function<Demographics, String> extractName;
    final BiFunction<I, Demographics, TimeSeries> extractData;

    StoreBuilder(LocalDate[] dates,
                 I cases,
                 I deaths,
                 Function<Demographics, String> extractName,
                 BiFunction<I, Demographics, TimeSeries> extractData
    ) {
        this.dates = dates;
        this.cases = cases;
        this.deaths = deaths;
        this.extractName = extractName;
        this.extractData = extractData;
    }

    Jurisdiction buildJurisdiction(Demographics d) {
        val j = new Jurisdiction();
        j.setName(extractName.apply(d));
        j.setPopulation(d.getPopulation());
        j.setPoints(buildPoints(
                dates,
                extractData.apply(this.cases, d).getData(),
                extractData.apply(this.deaths, d).getData()));
        return j;
    }

    private ArrayList<DataPoint> buildPoints(LocalDate[] dates, double[] cases, double[] deaths) {
        val idxFirstFriday = Arrays.binarySearch(dates, LocalDate.of(2020, 3, 6));
        assert cases.length == deaths.length : "case and death series are different lengths";
        val points = new ArrayList<DataPoint>();
        for (int i = idxFirstFriday, l = cases.length; i < l; i += 7) {
            assert dates[i].getDayOfWeek().equals(DayOfWeek.FRIDAY) : "not a friday?";
            points.add(new DataPoint(
                    dates[i],
                    (int) cases[i],
                    (int) deaths[i]));
        }
        return points;
    }

    public void updateStore(Store store, Stream<Demographics> demographics) {
        updateStore(store, demographics, it -> {});
    }

    public void updateStore(Store store, Stream<Demographics> demographics, Consumer<Jurisdiction> passthroughWork) {
        store.replaceTheWholeThing(demographics
                .map(this::buildJurisdiction)
                .peek(passthroughWork)
                .collect(Collectors.toList()));
        store.flush();
    }

}
