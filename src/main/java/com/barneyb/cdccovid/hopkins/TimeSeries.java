package com.barneyb.cdccovid.hopkins;

import com.barneyb.cdccovid.hopkins.csv.CsvTimeSeries;
import com.barneyb.cdccovid.hopkins.csv.Demographics;
import lombok.Data;
import lombok.ToString;
import lombok.val;

import java.util.function.ToLongBiFunction;

@Data
public class TimeSeries {

    public static TimeSeries zeros(Demographics demographics, String[] dateHeaders) {
        return new TimeSeries(
                demographics,
                new long[dateHeaders.length]);
    }

    private Demographics demographics;

    @ToString.Exclude
    private long[] data;

    @ToString.Include
    public int getPointCount() {
        return this.data.length;
    }

    @ToString.Include
    public long getTotalCases() {
        return this.data[this.data.length - 1];
    }

    private TimeSeries(Demographics demographics, long[] data) {
        this.demographics = demographics;
        this.data = data;
    }

    public TimeSeries(Demographics demographics, String[] dateHeaders, CsvTimeSeries raw) {
        if (demographics == null) {
            throw new RuntimeException("Null demographics for " + raw);
        }
        this.demographics = demographics;
        this.data = new long[dateHeaders.length];
        for (int i = 0, l = this.data.length; i < l; i++) {
            this.data[i] = raw.getDataPoint(dateHeaders[i]);
        }
    }

    public TimeSeries plus(TimeSeries other) {
        return binaryCombine(other, Long::sum);
    }

    public TimeSeries minus(TimeSeries other) {
        return binaryCombine(other, (a, b) -> a - b);
    }

    private TimeSeries binaryCombine(TimeSeries other, ToLongBiFunction<Long, Long> op) {
        if (getPointCount() != other.getPointCount()) {
            throw new IllegalArgumentException("Series have different sizes");
        }
        val next = new long[data.length];
        for (int i = 0, l = next.length; i < l; i++) {
            next[i] = op.applyAsLong(data[i], other.data[i]);
        }
        return new TimeSeries(demographics, next);
    }

}
