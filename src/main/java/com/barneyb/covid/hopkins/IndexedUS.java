package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.USTimeSeries;
import lombok.Getter;
import lombok.val;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexedUS {

    @Getter
    private final CombinedTimeSeries usExceptNy;
    private final Collection<CombinedTimeSeries> cover;
    private final Index<String, CombinedTimeSeries> localsByState;
    private final UniqueIndex<Pair<String>, CombinedTimeSeries> byStateAndLocality;
    private final UniqueIndex<String, CombinedTimeSeries> byState;

    public IndexedUS(IndexedDemographics demographics,
                     Collection<USTimeSeries> rawCases,
                     Collection<USTimeSeries> rawDeaths,
                     String[] dateHeaders) {
        val casesLookup = rawCases.stream()
                .map(it -> new TimeSeries(demographics.getByUid(it.getUid()),
                        dateHeaders,
                        it))
                .collect(Collectors.toMap(TimeSeries::getDemographics, it -> it));
        cover = rawDeaths.stream()
                .map(it -> new TimeSeries(demographics.getByUid(it.getUid()),
                        dateHeaders,
                        it))
                .map(ds -> new CombinedTimeSeries(
                        casesLookup.get(ds.getDemographics()),
                        ds
                ))
                .collect(Collectors.toUnmodifiableList());
        localsByState = new Index<>(cover.stream()
                .filter(it -> !it.getDemographics().isCompleteness()),
                it -> it.getDemographics().getState());
        byStateAndLocality = new UniqueIndex<>(cover.stream()
                .filter(it -> !it.getDemographics().isCompleteness())
                .filter(it -> it.getDemographics().isLocality()),
                it -> new Pair<>(it.getDemographics().getState(), it.getDemographics().getLocality()));
        byState = new UniqueIndex<>(demographics
                .usStatesAndDC()
                .map(d -> localsByState.get(d.getState())
                        .stream()
                        .reduce(CombinedTimeSeries::plus)
                        .map(ts -> ts.withDemographics(
                                demographics.getByCountryAndState("US", d.getState())))
                        .orElseThrow()
                ),
                it -> it.getDemographics().getState());
        usExceptNy = cover.stream()
                .reduce(CombinedTimeSeries::plus)
                .map(ts -> ts.withDemographics(demographics.getUsExceptNy()))
                .orElseThrow()
                .minus(byState.get("New York"));
    }

    public Stream<CombinedTimeSeries> cover() {
        return cover.stream();
    }

    public Stream<CombinedTimeSeries> statesAndDC() {
        return byState.values();
    }

    public CombinedTimeSeries getByState(String state) {
        return byState.get(state);
    }

    public CombinedTimeSeries getByStateAndLocality(String state, String locality) {
        return byStateAndLocality.get(new Pair<>(state, locality));
    }

    public Stream<String> statesWithLocalities() {
        return byState.keySet()
                .stream()
                .filter(s -> localsByState.get(s).size() > 1);
    }

    public Stream<CombinedTimeSeries> getLocalitiesOfState(String state) {
        return localsByState.get(state).stream();
    }
}
