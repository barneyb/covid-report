package com.barneyb.cdccovid.hopkins;

import com.barneyb.cdccovid.hopkins.csv.GlobalTimeSeries;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexedGlobals {

    private final Collection<TimeSeries> globals;
    private final UniqueIndex<String, TimeSeries> byCountry;
    private final UniqueIndex<Pair<String>, TimeSeries> byCountryAndState;
    private final Index<String, TimeSeries> statesOfCountry;

    public IndexedGlobals(IndexedDemographics demographics, Collection<GlobalTimeSeries> rawGlobal, String[] dateHeaders) {
        this.globals = rawGlobal.stream()
                .map(it -> new TimeSeries(
                        it.isCountry()
                                ? demographics.getByCountry(it.getCountry())
                                : demographics.getByCountryAndState(it.getCountry(), it.getState()),
                        dateHeaders,
                        it))
                .collect(Collectors.toUnmodifiableList());
        byCountry = new UniqueIndex<>(
                globals.stream()
                        .filter(it -> it.getDemographics().isCountry()),
                it -> it.getDemographics().getCountry());
        byCountryAndState = new UniqueIndex<>(
                globals.stream()
                        .filter(it -> it.getDemographics().isState()),
                it -> new Pair<>(it.getDemographics().getCountry(), it.getDemographics().getState()));
        statesOfCountry = new Index<>(
                globals.stream()
                        .filter(it -> it.getDemographics().isState()),
                it -> it.getDemographics().getCountry());

        // for countries which are broken down, roll up a total
        statesOfCountry.getKeys().stream()
                .filter(Predicate.not(byCountry::containsKey))
                .map(country -> globals.stream()
                        .filter(it -> country.equals(
                                it.getDemographics().getCountry()))
                        .reduce(TimeSeries::plus)
                        .map(s -> {
                            s.setDemographics(
                                    demographics.getByCountry(country));
                            return s;
                        })
                        .orElseThrow())
                .forEach(byCountry::add);
    }

    /** Worldwide coverage */
    public Stream<TimeSeries> cover() {
        return globals.stream();
    }

    /** Does NOT include protectorates! */
    public Stream<TimeSeries> allCountries() {
        return byCountry.getKeys().stream()
                .map(byCountry::get);
    }

    public TimeSeries getByCountry(String country) {
        return byCountry.get(country);
    }

    public TimeSeries getByCountryAndState(String country, String state) {
        return byCountryAndState.get(country, state);
    }

    public Stream<TimeSeries> getStatesOfCountry(String country) {
        return statesOfCountry.get(country).stream();
    }

}
