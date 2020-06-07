package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;

import java.util.Collection;
import java.util.stream.Stream;

public class IndexedDemographics {

    private final UniqueIndex<Integer, Demographics> byUid;
    private final UniqueIndex<String, Demographics> byCountry;
    private final UniqueIndex<Pair<String>, Demographics> byCountryAndState;
    private final UniqueIndex<String, Demographics> usStates;

    public IndexedDemographics(Collection<Demographics> demographics) {
        byUid = new UniqueIndex<>(demographics, Demographics::getUid);
        byCountry = new UniqueIndex<>(
                demographics.stream()
                        .filter(Demographics::isCountry),
                Demographics::getCountry);
        byCountryAndState = new UniqueIndex<>(
                demographics.stream()
                        .filter(Demographics::isState),
                it -> new Pair<>(it.getCountry(), it.getState()));
        usStates = new UniqueIndex<>(
                demographics.stream()
                    .filter(d -> d.getUid() >= 84000000 && d.getUid() < 84001000),
                Demographics::getState);
    }

    public Stream<Demographics> usStatesAndDC() {
        return usStates.values();
    }

    public Stream<Demographics> countries() {
        return byCountry.values();
    }

    public Demographics getByUid(Integer uid) {
        return byUid.get(uid);
    }

    public Demographics getByCountry(String country) {
        return byCountry.get(country);
    }

    public Demographics getByCountryAndState(String country, String state) {
        return byCountryAndState.get(country, state);
    }

    public Demographics getUSState(String state) {
        return usStates.get(state);
    }

}
