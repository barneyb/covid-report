package com.barneyb.covid.model;

import lombok.val;

import java.util.Collection;

/**
 * Series should use their area as the definition of identity. That is, hashCode
 * and equals should rely solely on the area. Equals should accept any Series
 * instance, regardless of type.
 */
public interface Series {

    Area getArea();

    int[] getCases();

    int[] getDeaths();

    int getSegmentCount();

    default boolean isAggregate() {
        return getSegmentCount() > 1;
    }

    default Collection<Series> getSegments() { // segments?
        throw new UnsupportedOperationException();
    }

    default int getPointCount() {
        val data = getCases();
        if (data == null) return 0;
        return data.length;
    }

    private int getDaysAgo(int[] data, int daysAgo) {
        if (data == null) return 0;
        return data[data.length - 1 - daysAgo];

    }

    default int getCasesDaysAgo(int daysAgo) {
        return getDaysAgo(getCases(), daysAgo);
    }

    default int getDeathsDaysAgo(int daysAgo) {
        return getDaysAgo(getDeaths(), daysAgo);
    }
}
