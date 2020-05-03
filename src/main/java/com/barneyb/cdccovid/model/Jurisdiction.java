package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({ "name", "population"})
public class Jurisdiction {

    @JsonProperty("jurisdiction")
    private String name;
    private Integer population;
    @JsonIgnore
    private SortedMap<LocalDate, DataPoint> data;

    public DataPoint getData(LocalDate date) {
        assert date != null;
        return Optional.ofNullable(data.get(date))
                .orElseThrow();
    }

    public Optional<DataPoint> getPriorData(LocalDate date) {
        assert date != null;
        val head = ((SortedSet<LocalDate>) data.keySet()).headSet(date);
        return head.isEmpty()
                ? Optional.empty()
                : Optional.of(data.get(head.last()));
    }

    @JsonIgnore
    public SortedSet<LocalDate> getDatesWithData(Predicate<DataPoint> test) {
        val dates = new TreeSet<LocalDate>();
        data.forEach((d, p) -> {
            if (test.test(p)) dates.add(d);
        });
        return dates;
    }

    public void addDataPoint(LocalDate asOf, Integer cases) {
        addDataPoint(asOf, cases, null);
    }

    public void addDataPoint(LocalDate asOf, Integer cases, Integer deaths) {
        if (this.data == null) this.data = new TreeMap<>();
        this.data.put(asOf, new DataPoint(asOf, cases, deaths));
    }

    @JsonProperty("data")
    public Collection<DataPoint> getPoints() {
        return data.values();
    }

    public void setPoints(Collection<DataPoint> points) {
        data = new TreeMap<>();
        points.forEach(p ->
                data.put(p.getAsOf(), p));
    }

}

