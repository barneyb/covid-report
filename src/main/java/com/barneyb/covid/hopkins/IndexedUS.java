package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;
import com.barneyb.covid.hopkins.csv.USTimeSeries;
import com.barneyb.covid.util.Index;
import com.barneyb.covid.util.UniqueIndex;
import lombok.Getter;
import lombok.val;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexedUS {

    @Getter
    private CombinedTimeSeries usExceptNy;
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
        val cover = rawDeaths.stream()
                .map(it -> new TimeSeries(demographics.getByUid(it.getUid()),
                        dateHeaders,
                        it))
                .map(ds -> new CombinedTimeSeries(
                        casesLookup.get(ds.getDemographics()),
                        ds
                ))
                .collect(Collectors.toUnmodifiableList());
        localsByState = new Index<>(cover.stream()
                .filter(it -> it.getDemographics().isConcrete()),
                it -> it.getDemographics().getState());
        byStateAndLocality = new UniqueIndex<>(cover.stream()
                .filter(it -> it.getDemographics().isConcrete())
                .filter(it -> it.getDemographics().isLocality()),
                it -> new Pair<>(it.getDemographics().getState(), it.getDemographics().getLocality()));
        byStateAndLocality.add(buildPortlandMetro());
        byStateAndLocality.add(buildSalemMetro());
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
    }

    private CombinedTimeSeries buildAggregateLocality(String state, String name, String... localities) {
        val d = new Demographics();
        d.setCountry("US");
        d.setState(state);
        d.setLocality(name);
        d.setCombinedKey(name + ", " + state + ", US");
        return Arrays.stream(localities)
                .map(l -> byStateAndLocality.get(state, l))
                .peek(c -> d.setPopulation(d.getPopulation() + c.getDemographics().getPopulation()))
                .reduce(CombinedTimeSeries::plus)
                .orElseThrow()
                .withDemographics(d);
    }

    private CombinedTimeSeries buildPortlandMetro() {
        return buildAggregateLocality("Oregon", "Portland Metro", "Clackamas", "Multnomah", "Washington");
    }

    private CombinedTimeSeries buildSalemMetro() {
        return buildAggregateLocality("Oregon", "Salem Metro", "Marion", "Polk");
    }

    public void createUsExceptNy(Demographics d) {
        usExceptNy = statesAndDC()
                .reduce(CombinedTimeSeries::plus)
                .map(ts -> ts.withDemographics(d))
                .orElseThrow()
                .minus(byState.get("New York"));
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
