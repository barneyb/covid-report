package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;
import lombok.Getter;

import java.util.Collection;
import java.util.stream.Stream;

public class IndexedDemographics {

    public static final int WORLDWIDE_UID = 0;
    public static final String WORLDWIDE_KEY = "Worldwide";
    public static final int US_EXCEPT_NY_UID = 84099936; // US is 840, NY is 36, 999 is conflict avoidance
    public static final String US_EXCEPT_NY_KEY = "US Except NY";

    @Getter
    private final Demographics worldwide;
    @Getter
    private final Demographics usExceptNy;
    private final Collection<Demographics> cover;
    private final UniqueIndex<Integer, Demographics> byUid;
    private final UniqueIndex<String, Demographics> byCountry;
    private final UniqueIndex<Pair<String>, Demographics> byCountryAndState;
    private final UniqueIndex<String, Demographics> usStates;

    public IndexedDemographics(Collection<Demographics> demographics) {
        this.cover = demographics;
        byUid = new UniqueIndex<>(demographics, Demographics::getUid);
        byCountry = new UniqueIndex<>(
                demographics.stream()
                        .filter(Demographics::isCountry),
                Demographics::getCountry);
        worldwide = new Demographics();
        worldwide.setUid(WORLDWIDE_UID);
        worldwide.setCountry(WORLDWIDE_KEY);
        worldwide.setCombinedKey(WORLDWIDE_KEY);
        worldwide.setPopulation(byCountry.values()
                .map(Demographics::getPopulation)
                .reduce(0L, Long::sum));
        byUid.add(worldwide);
        byCountryAndState = new UniqueIndex<>(
                demographics.stream()
                        .filter(Demographics::isState),
                it -> new Pair<>(it.getCountry(), it.getState()));
        usStates = new UniqueIndex<>(
                demographics.stream()
                    .filter(d -> d.getUid() >= 84000000 && d.getUid() < 84001000),
                Demographics::getState);
        usExceptNy = new Demographics();
        usExceptNy.setUid(US_EXCEPT_NY_UID);
        usExceptNy.setCountry(US_EXCEPT_NY_KEY);
        usExceptNy.setCombinedKey(US_EXCEPT_NY_KEY);
        usExceptNy.setPopulation(byCountry.get("US").getPopulation() - usStates.get("New York").getPopulation());
        byUid.add(usExceptNy);
    }

    public Stream<Demographics> cover() {
        return cover.stream();
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
