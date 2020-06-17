package com.barneyb.covid.hopkins;

import com.barneyb.covid.Store;
import com.barneyb.covid.hopkins.csv.Demographics;
import com.barneyb.covid.model.DataPoint;
import com.barneyb.covid.model.Jurisdiction;
import lombok.val;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StoreBuilder<I> {

    final LocalDate[] dates;
    I index;
    final Function<Demographics, String> extractName;
    final BiFunction<I, Demographics, CombinedTimeSeries> extractData;

    StoreBuilder(LocalDate[] dates,
                 I index,
                 Function<Demographics, String> extractName,
                 BiFunction<I, Demographics, CombinedTimeSeries> extractData
    ) {
        this.dates = dates;
        this.index = index;
        this.extractName = extractName;
        this.extractData = extractData;
    }

    Jurisdiction buildJurisdiction(Demographics d) {
        val j = new Jurisdiction();
        j.setName(extractName.apply(d));
        j.setPopulation(d.getPopulation());
        val cts = extractData.apply(this.index, d);
        j.setPoints(buildPoints(
                dates,
                cts.getCasesSeries().getData(),
                cts.getDeathsSeries().getData()));
        return j;
    }

    private ArrayList<DataPoint> buildPoints(LocalDate[] dates, double[] cases, double[] deaths) {
        val days = (int) ChronoUnit.DAYS.between(
                LocalDate.of(2020, 3, 6),
                dates[dates.length - 1]) / 7 * 7 + 1;
        assert cases.length == deaths.length : "case and death series are different lengths";
        val points = new ArrayList<DataPoint>();
        for (int i = cases.length - days, l = cases.length; i < l; i += 7) {
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
