package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;
import com.barneyb.covid.hopkins.csv.GlobalTimeSeries;
import lombok.Getter;
import lombok.val;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexedWorld {

    public static final String WORLDWIDE = "Worldwide";

    @Getter
    private final TimeSeries worldwide;
    private final Collection<TimeSeries> cover;
    private final UniqueIndex<String, TimeSeries> byCountry;
    private final UniqueIndex<Pair<String>, TimeSeries> byCountryAndState;
    private final Index<String, TimeSeries> statesOfCountry;

    public IndexedWorld(IndexedDemographics demographics, Collection<GlobalTimeSeries> rawGlobal, String[] dateHeaders) {
        cover = rawGlobal.stream()
                .map(it -> new TimeSeries(
                        it.isCountry()
                                ? demographics.getByCountry(it.getCountry())
                                : demographics.getByCountryAndState(it.getCountry(), it.getState()),
                        dateHeaders,
                        it))
                .collect(Collectors.toUnmodifiableList());
        byCountry = new UniqueIndex<>(
                cover.stream()
                        .filter(it -> it.getDemographics().isCountry()),
                it -> it.getDemographics().getCountry());
        val wwDemo = new Demographics();
        wwDemo.setCombinedKey(WORLDWIDE);
        wwDemo.setPopulation(cover()
                .map(TimeSeries::getDemographics)
                .map(Demographics::getPopulation)
                .reduce(0L, Long::sum));
        worldwide = cover()
                .reduce(TimeSeries::plus)
                .orElseThrow();
        worldwide.setDemographics(wwDemo);
        byCountryAndState = new UniqueIndex<>(
                cover.stream()
                        .filter(it -> it.getDemographics().isState()),
                it -> new Pair<>(it.getDemographics().getCountry(), it.getDemographics().getState()));

        // this also ends up with protectorates (e.g., Greenland of Denmark)
        statesOfCountry = new Index<>(
                cover.stream()
                        .filter(it -> it.getDemographics().isState()),
                it -> it.getDemographics().getCountry());

        // for countries which are broken down, roll up a total
        statesOfCountry.getKeys().stream()
                .filter(Predicate.not(byCountry::containsKey))
                .map(country -> cover.stream()
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

    public Stream<TimeSeries> cover() {
        return cover.stream();
    }

    public Stream<TimeSeries> countries() {
        return byCountry.values();
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
