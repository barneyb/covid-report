package com.barneyb.cdccovid.hopkins;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Pair<T> {
    private final T first;
    private final T second;
}
