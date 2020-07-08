package com.barneyb.covid.hopkins;

import lombok.Value;

@Value
public class Pair<T> {
    T first;
    T second;
}
