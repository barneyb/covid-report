package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({ "name", "population"})
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

    @JsonIgnore
    public SortedSet<LocalDate> getDatesWithData() {
        return points
                .stream()
                .map(DataPoint::getDate)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public void addDataPoint(LocalDate asOf, Integer cases) {
        addDataPoint(asOf, cases, null);
    }

    public void addDataPoint(LocalDate asOf, Integer cases, Integer deaths) {
        if (this.points == null) this.points = new ArrayList<>();
        this.points.add(new DataPoint(asOf, cases, deaths));
    }
}

