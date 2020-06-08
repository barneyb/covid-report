package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.CsvTimeSeries;
import com.barneyb.covid.hopkins.csv.Demographics;
import lombok.Data;
import lombok.ToString;
import lombok.val;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;

@Data
public class TimeSeries {

    private Demographics demographics;

    @ToString.Exclude
    private double[] data;

    @ToString.Include
    public int getPointCount() {
        return this.data.length;
    }

    @ToString.Include
    public double getCurrent() {
        return this.data[this.data.length - 1];
    }

    private TimeSeries(Demographics demographics, double[] data) {
        this.demographics = demographics;
        this.data = data;
    }

    public TimeSeries(Demographics demographics, String[] dateHeaders, CsvTimeSeries raw) {
        if (demographics == null) {
            throw new RuntimeException("Null demographics for " + raw);
        }
        this.demographics = demographics;
        this.data = new double[dateHeaders.length];
        for (int i = 0, l = this.data.length; i < l; i++) {
            this.data[i] = raw.getDataPoint(dateHeaders[i]);
        }
    }

    public TimeSeries plus(TimeSeries other) {
        return binaryCombine(other, Double::sum);
    }

    public TimeSeries minus(TimeSeries other) {
        return binaryCombine(other, (a, b) -> a - b);
    }

    private TimeSeries binaryCombine(TimeSeries other, ToDoubleBiFunction<Double, Double> op) {
        if (getPointCount() != other.getPointCount()) {
            throw new IllegalArgumentException("Series have different sizes");
        }
        val next = new double[data.length];
        for (int i = 0, l = next.length; i < l; i++) {
            next[i] = op.applyAsDouble(data[i], other.data[i]);
        }
        return new TimeSeries(demographics, next);
    }

    public TimeSeries transform(BiFunction<Demographics, Double, Double> transform) {
        return map(data -> {
            val next = new double[data.length];
            for (int i = 0, l = next.length; i < l; i++) {
                next[i] = transform.apply(demographics, data[i]);
            }
            return next;
        });
    }

    public TimeSeries map(Function<double[], double[]> map) {
        return new TimeSeries(demographics, map.apply(data));
    }

}
