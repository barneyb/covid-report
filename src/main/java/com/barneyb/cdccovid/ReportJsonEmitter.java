package com.barneyb.cdccovid;

import com.barneyb.cdccovid.model.DataPoint;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("unused")
public class ReportJsonEmitter implements Emitter {

    public static final int DAYS_PER_WEEK = 7;
    public static final int RATE_POPULATION = 100_000;

    @Autowired
    Store store;

    @Autowired
    ObjectMapper mapper;

    @Override
    @SneakyThrows
    public void emit(OutputStream out) {
        mapper
//                .writerWithDefaultPrettyPrinter()
                .writeValue(out, buildReport());
    }

    private Report buildReport() {
        val dates = store.getDatesWithCases();
        val report = new Report();
        report.date = dates.last();
        report.jurisdictions = store.getAllJurisdictions()
                .stream()
//                .filter(j -> "New York" .equals(j.getName()) || "Oregon" .equals(j.getName()))
                .map(j -> {
                    val r = new Juris();
                    r.name = j.getName();
                    r.population = j.getPopulation();
                    r.mortalityRates = j.getMortalityRates();
                    r.data = dates
                            .stream()
                            .map(j::getData)
                            .map(curr -> j.getPriorData(curr.getAsOf())
                                    .map(prev -> new Data(j.getPopulation(), curr, prev))
                                    .orElseGet(() -> new Data(j.getPopulation(), curr)))
                            .collect(Collectors.toList());
                    return r;
                })
                .collect(Collectors.toList());
        report.points = report.jurisdictions
                .get(0)
                .data
                .stream()
                .map(d -> new Point(
                        d.date,
                        d.hasDeaths(),
                        d.hasDelta() ? d.since.days : null,
                        d.hasDelta(),
                        d.hasDeaths() && d.hasDelta()
                                ? d.since.hasDeaths()
                                : null))
                .collect(Collectors.toList());
        return report;
    }

    @Getter
    private static class Report {
        LocalDate date;
        List<Point> points;
        List<Juris> jurisdictions;
    }

    @Getter
    @AllArgsConstructor
    private static class Point {
        LocalDate date;
        Boolean deaths;
        Integer days;
        Boolean caseDelta;
        Boolean deathDelta;
    }

    @Getter
    private static class Juris {
        String name;
        Integer population;
        Map<String, Double> mortalityRates;
        List<Data> data;
    }

    @Getter
    private abstract static class BaseData {
        @JsonIgnore
        Integer pop;
        LocalDate date;
        Integer cases;
        Integer deaths;

        BaseData(Integer pop, LocalDate date, Integer cases, Integer deaths) {
            this.pop = pop;
            this.date = date;
            this.cases = cases;
            this.deaths = deaths;
        }

        boolean hasDeaths() {
            return deaths != null;
        }

    }

    @Getter
    private static class Data extends BaseData {
        Delta since;

        Data(Integer pop, DataPoint p) {
            super(pop, p.getAsOf(), p.getCases(), p.getDeaths());
        }

        Data(Integer pop, DataPoint curr, DataPoint prev) {
            this(pop, curr);
            this.since = new Delta(pop, curr, prev);
        }

        boolean hasDelta() {
            return since != null;
        }

    }

    @Getter
    private static class Delta extends BaseData {
        Integer days;

        Delta(Integer pop, DataPoint curr, DataPoint prev) {
            super(
                    pop,
                    prev.getAsOf(),
                    curr.getCases() - prev.getCases(),
                    curr.hasDeaths() && prev.hasDeaths()
                            ? curr.getDeaths() - prev.getDeaths()
                            : null);
            this.days = Period.between(prev.getAsOf(), curr.getAsOf()).getDays();
        }
    }

}
