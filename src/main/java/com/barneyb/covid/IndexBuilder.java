package com.barneyb.covid;

import com.barneyb.covid.model.AggSeries;
import com.barneyb.covid.model.Series;
import com.barneyb.covid.util.Spark;
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
                                new ListTile<>("Daily Case Rate (per 100k)", scope, s ->
                                        new Stat<>(s, (s.getCurrentCases() - s.getCasesDaysAgo(7)) / 7.0 * 100_000 / s.getArea().getPopulation(), Spark::caseRate)),
                                new ListTile<>("Daily Cases (7-day Avg)", scope, s ->
                                        new Stat<>(s, (s.getCurrentCases() - s.getCasesDaysAgo(7)) / 7.0, Spark::caseRate)),
                                new ListTile<>("Total Cases", scope, s ->
                                        new Stat<>(s, s.getCurrentCases(), Spark::caseRate)),
                                new ListTile<>("Total Deaths", scope, s ->
                                        new Stat<>(s, s.getCurrentDeaths(), Spark::deathRate))
                        ))
                )
                .collect(Collectors.toList());

        try (val out = Files.newOutputStream(destination)) {
            writer.writeValue(out, dash);
        }
    }

    @Value
    private static class Stat<T extends Comparable<T>> implements Comparable<Stat<T>> {
        Series series;
        T stat;
        Function<Series, double[]> sparkSupplier;

        @Override
        public int compareTo(Stat<T> o) {
            return stat.compareTo(o.stat);
        }

        public double[] getSpark() {
            return sparkSupplier.apply(series);
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
        public static final int SPARK_DAYS = 21;

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
    private static class ListTile<T extends Comparable<T>> implements Tile {

        String type = "list";
        String title;
        List<ListItem<T>> items;

        public ListTile(String title, Series series, Function<Series, Stat<T>> toStat) {
            this(title, series.getSegments().stream()
                    .map(toStat)
                    .filter(s -> !(s.stat instanceof Number) || !Double.isNaN(((Number) s.stat).doubleValue()))
                    .sorted(Comparator.reverseOrder())
                    .limit(10)
                    .collect(Collectors.toList()));
        }

        public ListTile(String title, List<Stat<T>> series) {
            this.title = title;
            this.items = series.stream()
                    .map(ListItem::new)
                    .collect(Collectors.toList());
        }

    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class ListItem<T extends Comparable<T>> {

        int id;
        String name;
        T value;
        double[] spark;

        public ListItem(Stat<T> s) {
            final var a = s.getSeries().getArea();
            this.id = a.getId();
            this.name = a.getName();
            this.value = s.stat;
            this.spark = s.getSpark();
        }

    }

}
