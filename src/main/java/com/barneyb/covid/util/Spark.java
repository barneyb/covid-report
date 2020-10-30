package com.barneyb.covid.util;

import java.util.Arrays;
import java.util.function.Function;

public final class Spark {
    private Spark() { throw new UnsupportedOperationException("really?"); }

    private static final int DEFAULT_DAYS_DAYS = 14;

    public static double[] rate(int[] data, int days) {
        return spark(data, days, arr ->
                Transform.rollingAverage(Transform.delta(arr)));
    }

    public static double[] spark(int[] data) {
        return spark(data, DEFAULT_DAYS_DAYS);
    }

    public static double[] spark(double[] data) {
        return spark(data, DEFAULT_DAYS_DAYS);
    }

    public static double[] spark(int[] data, int days) {
        return spark(data, days, Transform::toDouble);
    }

    public static double[] spark(double[] data, int days) {
        int l = data.length;
        return Arrays.copyOfRange(data, l - days, l);
    }

    @Deprecated
    public static double[] spark(int[] data, Function<int[], double[]> transform) {
        return spark(data, DEFAULT_DAYS_DAYS, transform);
    }

    @Deprecated
    public static double[] spark(int[] data, int days, Function<int[], double[]> transform) {
        final var len = data.length;
        int l = days + 7;
        var slice = transform.apply(
                Arrays.copyOfRange(data, len - l, len));
        return spark(slice, days);
    }
}
