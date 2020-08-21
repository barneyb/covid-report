package com.barneyb.covid.util;

import com.barneyb.covid.model.Series;

import java.util.Arrays;
import java.util.Optional;

public final class Spark {
    private Spark() { throw new UnsupportedOperationException("really?"); }

    private static final int DEFAULT_DAYS_DAYS = 14;

    public static double[] caseRate(Series series) {
        return rate(series.getCases(), DEFAULT_DAYS_DAYS);
    }

    public static double[] deathRate(Series series) {
        return rate(series.getDeaths(), DEFAULT_DAYS_DAYS);
    }

    public static double[] rate(int[] data, int days) {
        final var len = data.length;
        return Optional.of(Arrays.copyOfRange(data, len - days - 7, len))
                .map(Transform::delta)
                .map(Transform::rollingAverage)
                .map(d -> {
                    int l = d.length;
                    return Arrays.copyOfRange(d, l - days, l);
                })
                .orElseThrow();
    }
}
