package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;

public interface WithDemographics<T> {

    Demographics getDemographics();

    T withDemographics(Demographics demographics);

}
