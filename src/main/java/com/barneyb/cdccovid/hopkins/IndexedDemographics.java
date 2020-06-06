package com.barneyb.cdccovid.hopkins;

import com.barneyb.cdccovid.hopkins.csv.Demographics;

import java.util.Collection;

public class IndexedDemographics {

    private final UniqueIndex<Integer, Demographics> byUid;
    private final UniqueIndex<String, Demographics> byCountry;
    private final UniqueIndex<Pair<String>, Demographics> byCountryAndState;

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

}
