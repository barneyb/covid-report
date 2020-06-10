package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.USTimeSeries;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexedUS {

    private final Collection<TimeSeries> cover;
    private final Index<String, TimeSeries> localsByState;
    private final UniqueIndex<Pair<String>, TimeSeries> byStateAndLocality;
    private final UniqueIndex<String, TimeSeries> byState;

    public IndexedUS(IndexedDemographics demographics, Collection<USTimeSeries> rawUS, String[] dateHeaders) {
        cover = rawUS.stream()
                .map(it -> new TimeSeries(demographics.getByUid(it.getUid()),
                        dateHeaders,
                        it))
                .collect(Collectors.toUnmodifiableList());
        localsByState = new Index<>(cover, it -> it.getDemographics().getState());
        byStateAndLocality = new UniqueIndex<>(cover.stream()
                .filter(it -> it.getDemographics().isLocality()),
                it -> new Pair<>(it.getDemographics().getState(), it.getDemographics().getLocality()));
        byState = new UniqueIndex<>(demographics
                .usStatesAndDC()
                .map(d -> localsByState.get(d.getState())
                        .stream()
                        .reduce(TimeSeries::plus)
                        .map(ts -> {
                            ts.setDemographics(
                                    demographics.getByCountryAndState("US", d.getState()));
                            return ts;
                        })
                        .orElseThrow()
                ),
                it -> it.getDemographics().getState());
    }

    public Stream<TimeSeries> cover() {
        return cover.stream();
    }

    public Stream<TimeSeries> statesAndDC() {
        return byState.values();
    }

    public TimeSeries getByState(String state) {
        return byState.get(state);
    }

    public TimeSeries getByStateAndLocality(String state, String locality) {
        return byStateAndLocality.get(new Pair<>(state, locality));
    }

    public Stream<TimeSeries> getLocalitiesOfState(String state) {
        return localsByState.get(state).stream();
    }
}
