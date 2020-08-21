package com.barneyb.covid.util;

import java.util.LinkedList;
import java.util.Queue;

public final class Transform {
    private Transform() { throw new UnsupportedOperationException("really?"); }

    public static double[] toDouble(int[] data) {
        var len = data.length;
        var dd = new double[len];
        for (int i = 0; i < len; i++) {
            dd[i] = data[i];
        }
        return dd;
    }

    public static double[] rollingAverage(int[] data) {
        return rollingAverage(toDouble(data));
    }

    public static double[] rollingAverage(double[] data) {
        final var next = new double[data.length];
        Queue<Double> queue = new LinkedList<>();
        double sum = 0;
        for (int i = 0, l = next.length; i < l; i++) {
            queue.add(data[i]);
            sum += data[i];
            while (queue.size() > 7) sum -= queue.remove();
            next[i] = sum / queue.size();
        }
        return next;
    }

    public static int[] delta(int[] data) {
        final var next = new int[data.length];
        int prev = next[0] = data[0];
        for (int i = 1, l = next.length; i < l; i++) {
            next[i] = data[i] - prev;
            prev = data[i];
        }
        return next;
    }

    public static double[] delta(double[] data) {
        final var next = new double[data.length];
        double prev = next[0] = data[0];
        for (int i = 1, l = next.length; i < l; i++) {
            next[i] = data[i] - prev;
            prev = data[i];
        }
        return next;
    }

    public static double[] per100k(long population, int[] data) {
        return per100k(population, toDouble(data));
    }

    public static double[] per100k(long population, double[] data) {
        final var next = new double[data.length];
        for (int i = 0, l = next.length; i < l; i++) {
            next[i] = data[i] * 100_000 / population;
        }
        return next;
    }

}
