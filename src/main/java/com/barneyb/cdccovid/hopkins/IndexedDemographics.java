package com.barneyb.cdccovid.hopkins;

import com.barneyb.cdccovid.hopkins.csv.Demographics;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

public class IndexedDemographics {

    @Data
    @AllArgsConstructor
    private static class CountryAndState {
        private String country;
        private String string;
    }

    private final UniqueIndex<Integer, Demographics> byUid;
    private final UniqueIndex<String, Demographics> byCountry;
    private final UniqueIndex<CountryAndState, Demographics> byCountryAndState;

    public IndexedDemographics(List<Demographics> demographics) {
        byUid = new UniqueIndex<>(demographics, Demographics::getUid);
        byCountry = new UniqueIndex<>(
                demographics.stream()
                        .filter(Demographics::isCountry),
                Demographics::getCountry);
        byCountryAndState = new UniqueIndex<>(
                demographics.stream()
                        .filter(it -> !it.isLocal()),
                it -> new CountryAndState(it.getCountry(), it.getState()));
    }

    public Demographics getByUid(Integer uid) {
        return byUid.get(uid);
    }

    public Demographics getByCountry(String country) {
        return byCountry.get(country);
    }

    public Demographics getByCountryAndState(String country, String state) {
        return byCountryAndState.get(new CountryAndState(country, state));
    }

}
