package com.barneyb.covid.model;

import org.apache.commons.lang3.builder.CompareToBuilder;

/**
 * Areas should use their id as the definition of identity. That is, hashCode
 * and equals should rely solely on the area's id. Equals should accept any
 * Area instance, regardless of type. The natural order of areas uses the name
 * and the id, and is thus inconsistent with equals.
 */
public interface Area extends Comparable<Area> {

    int ID_US = 840;
    int ID_WORLDWIDE = 252_000_001;

    int getId();

    long getPopulation();

    String getName();

    boolean isConcrete();

    default int compareTo(Area o) {
        if (o == null) return -1; // nulls last
        return new CompareToBuilder()
                .append(getName(), o.getName())
                .append(getId(), o.getId())
                .toComparison();
    }

}
