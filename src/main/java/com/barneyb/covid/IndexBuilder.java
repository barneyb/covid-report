package com.barneyb.covid;

import com.barneyb.covid.model.AggSeries;
import com.barneyb.covid.model.Series;
import com.barneyb.covid.util.Spark;
import com.barneyb.covid.util.Transform;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IndexBuilder {

    @Autowired
    ObjectWriter writer;

    @SneakyThrows
    public void emit(Path destination, AggSeries theWorld) {
        final var scopes = new LinkedList<Series>();
        scopes.add(theWorld);
        final var us = theWorld.getSegment("US");
        scopes.add(us);
        final var or = ((AggSeries) us).getSegment("Oregon");
        scopes.add(or);
        final var dash = scopes.stream()
                .map(scope ->
                        new Section(scope.getArea().getName(), List.of(
                                new StatsTile(scope),
                                new ListTile("Daily Case Rate (per 100k)", scope, s ->
                                        new Stat(s, Spark.spark(s.getCases(), counts ->
                                                Transform.per100k(
                                                        s.getArea().getPopulation(),
                                                        Transform.rollingAverage(
                                                                Transform.delta(counts)))))),
                                new ListTile("Daily Cases", scope, s ->
                                        new Stat(s, Spark.spark(s.getCases(), counts ->
                                                Transform.rollingAverage(Transform.delta(counts))))),
                                new ListTile("Total Cases", scope, s ->
                                        new Stat(s, Spark.spark(s.getCases()))),
                                new ListTile("Total Deaths", scope, s ->
                                        new Stat(s, Spark.spark(s.getDeaths()))),
                                new ListTile("Case Mortality (%)", scope, s ->
                                        new Stat(s, Spark.spark(caseMortality(s))))
                        ))
                )
                .collect(Collectors.toList());

        try (val out = Files.newOutputStream(destination)) {
            writer.writeValue(out, dash);
        }
    }

    private double[] caseMortality(Series s) {
        double[] cases = Transform.rollingAverage(s.getCases());
        double[] deaths = Transform.rollingAverage(s.getDeaths());
        assert cases.length == deaths.length;
        double[] mortality = new double[cases.length];
        for (int i = 0, l = cases.length; i < l; i++) {
            if (cases[i] == 0) continue;
            mortality[i] = 100.0 * deaths[i] / cases[i];
        }
        return mortality;
    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class Stat implements Comparable<Stat> {
        Series series;
        double stat;
        double[] spark;

        public Stat(Series series, double stat, double[] spark) {
            this.series = series;
            this.stat = stat;
            this.spark = spark;
        }

        public Stat(Series series, double[] spark) {
            this(series, spark[spark.length - 1], spark);
        }

        @Override
        public int compareTo(Stat o) {
            return Double.compare(stat, o.stat);
        }

    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @AllArgsConstructor
    private static class Section {
        String label;
        List<Tile> tiles;
    }

    private interface Tile {
        String getTitle();
        String getType();
    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class StatsTile implements Tile {

        String type = "stats";
        int id;
        String title;
        long population;
        DatumStat cases;
        DatumStat deaths;

        public StatsTile(Series series) {
            var a = series.getArea();
            this.id = a.getId();
            this.title = a.getName();
            this.population = a.getPopulation();
            this.cases = new DatumStat(series.getCases());
            this.deaths = new DatumStat(series.getDeaths());
        }
    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @AllArgsConstructor
    private static class DatumStat {
        public static final int SPARK_DAYS = 35;

        int total;
        double daily;
        double[] spark;

        public DatumStat(int[] counts) {
            final var len = counts.length;
            this.total = counts[len - 1];
            this.daily = (this.total - counts[len - 1 - 7]) / 7.0;
            this.spark = Spark.rate(counts, SPARK_DAYS);
        }
    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class ListTile implements Tile {

        String type = "list";
        String title;
        List<ListItem> items;

        public ListTile(String title, Series series, Function<Series, Stat> toStat) {
            this(title, series.getSegments().stream()
                    .map(toStat)
                    .filter(s -> Double.isFinite(s.stat))
                    .sorted(Comparator.reverseOrder())
                    .limit(10)
                    .collect(Collectors.toList()));
        }

        public ListTile(String title, List<Stat> series) {
            this.title = title;
            this.items = series.stream()
                    .map(ListItem::new)
                    .collect(Collectors.toList());
        }

    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class ListItem {

        int id;
        String name;
        double value;
        double[] spark;

        public ListItem(Stat s) {
            final var a = s.getSeries().getArea();
            this.id = a.getId();
            this.name = a.getName();
            this.value = s.stat;
            this.spark = s.getSpark();
        }

    }

}
