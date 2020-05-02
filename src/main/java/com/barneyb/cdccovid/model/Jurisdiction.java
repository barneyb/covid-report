package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Jurisdiction {

    @JsonProperty("jurisdiction")
    private String name;
    private Integer population;
    private Collection<DataPoint> points;

    public DataPoint getData(LocalDate date) {
        assert date != null;
        return points.stream()
                .filter(p -> date.equals(p.getDate()))
                .findFirst()
                .orElseThrow();
    }

    public void addDataPoint(CdcJurisdiction j) {
        if (this.points == null) this.points = new ArrayList<>();
        this.points.add(DataPoint.from(j));
    }

    @JsonIgnore
    public SortedSet<LocalDate> getDatesWithData() {
        return points
                .stream()
                .map(DataPoint::getDate)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}

