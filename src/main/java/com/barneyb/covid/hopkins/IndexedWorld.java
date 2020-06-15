package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.GlobalTimeSeries;
import lombok.Getter;
import lombok.val;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexedWorld {

    @Getter
    private final CombinedTimeSeries worldwide;
    private final Collection<CombinedTimeSeries> cover;
    private final UniqueIndex<String, CombinedTimeSeries> byCountry;
    private final UniqueIndex<Pair<String>, CombinedTimeSeries> byCountryAndState;
    private final Index<String, CombinedTimeSeries> statesOfCountry;

    public IndexedWorld(IndexedDemographics demographics,
                        Collection<GlobalTimeSeries> rawCases,
                        Collection<GlobalTimeSeries> rawDeaths,
                        String[] dateHeaders) {
        val casesLookup = rawCases.stream()
                .map(it -> new TimeSeries(
                        it.isCountry()
                                ? demographics.getByCountry(it.getCountry())
                                : demographics.getByCountryAndState(it.getCountry(), it.getState()),
                        dateHeaders,
                        it))
                .collect(Collectors.toMap(TimeSeries::getDemographics, it -> it));
        cover = rawDeaths.stream()
                .map(it -> new TimeSeries(
                        it.isCountry()
                                ? demographics.getByCountry(it.getCountry())
                                : demographics.getByCountryAndState(it.getCountry(), it.getState()),
                        dateHeaders,
                        it))
                .map(ds -> new CombinedTimeSeries(
                        casesLookup.get(ds.getDemographics()),
                        ds
                ))
                .collect(Collectors.toUnmodifiableList());
        byCountry = new UniqueIndex<>(
                cover.stream()
                        .filter(it -> it.getDemographics().isCountry()),
                it -> it.getDemographics().getCountry());
        worldwide = cover()
                .reduce(CombinedTimeSeries::plus)
                .map(it -> it.withDemographics(demographics.getWorldwide()))
                .orElseThrow();
        byCountryAndState = new UniqueIndex<>(
                cover.stream()
                        .filter(it -> it.getDemographics().isState()),
                it -> new Pair<>(it.getDemographics().getCountry(), it.getDemographics().getState()));

        // this also ends up with dependencies (e.g., Greenland of Denmark)
        statesOfCountry = new Index<>(
                cover.stream()
                        .filter(it -> it.getDemographics().isState()),
                it -> it.getDemographics().getCountry());

        // for countries which are broken down, roll up a total
        statesOfCountry.keySet().stream()
                .filter(Predicate.not(byCountry::containsKey))
                .map(country -> cover.stream()
                        .filter(it -> country.equals(
                                it.getDemographics().getCountry()))
                        .reduce(CombinedTimeSeries::plus)
                        .map(s -> s.withDemographics(
                                demographics.getByCountry(country)))
                        .orElseThrow())
                .forEach(byCountry::add);
    }

    public Stream<CombinedTimeSeries> cover() {
        return cover.stream();
    }

    public Stream<CombinedTimeSeries> countries() {
        return byCountry.values();
    }

    public CombinedTimeSeries getByCountry(String country) {
        return byCountry.get(country);
    }

    public CombinedTimeSeries getByCountryAndState(String country, String state) {
        return byCountryAndState.get(country, state);
    }

    public Stream<String> countriesWithStates() {
        return byCountry.keySet()
                .stream()
                .filter(statesOfCountry::containsKey)
                .filter(c -> statesOfCountry.get(c).size() > 1);
    }

    public Stream<CombinedTimeSeries> getStatesOfCountry(String country) {
        return statesOfCountry.get(country).stream();
    }

}
