package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;
import com.barneyb.covid.hopkins.csv.Rates;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.barneyb.covid.hopkins.IndexedDemographics.WORLDWIDE_KEY;

public class RatesBuilder {

    public static final Function<double[], double[]> ROLLING_AVERAGE = data -> {
        val next = new double[data.length];
        Queue<Double> queue = new LinkedList<>();
        double sum = 0;
        for (int i = 0, l = next.length; i < l; i++) {
            queue.add(data[i]);
            sum += data[i];
            while (queue.size() > 7) sum -= queue.remove();
            next[i] = sum / queue.size();
        }
        return next;
    };

    public static final Function<double[], double[]> DELTA = data -> {
        val next = new double[data.length];
        double prev = next[0] = data[0];
        for (int i = 1, l = next.length; i < l; i++) {
            next[i] = data[i] - prev;
            prev = data[i];
        }
        return next;
    };

    public static final BiFunction<Demographics, Double, Double> PER_100K = (d, v) ->
            v / (1.0 * d.getPopulation() / 100_000);

    final LocalDate[] dates;
    final IndexedWorld idxWorld;
    final IndexedUS idxUs;

    public RatesBuilder(LocalDate[] dates, IndexedWorld idxWorld, IndexedUS idxUs) {
        this.dates = dates;
        this.idxWorld = idxWorld;
        this.idxUs = idxUs;
    }

    private LinkedList<TimeSeries> build() {
        val countSeries = new LinkedList<>(List.of(idxWorld.getWorldwide(), idxUs.getUsExceptNy(),
                idxWorld.getByCountryAndState("China", "Hubei")));
        idxUs.statesAndDC().forEach(countSeries::add);
        List.of("China", "Italy", "Brazil", "France", "Russia", "US")
                .stream()
                .map(idxWorld::getByCountry)
                .forEach(countSeries::add);
        List.of("Alameda", "Contra Costa", "Los Angeles", "Marin", "Napa", "Orange", "San Diego", "San Francisco", "San Mateo", "Santa Clara", "Solano", "Sonoma", "Ventura")
                .stream()
                .map(c -> idxUs.getByStateAndLocality("California", c))
                .forEach(countSeries::add);
        List.of("Clackamas", "Marion", "Multnomah", "Washington")
                .stream()
                .map(c -> idxUs.getByStateAndLocality("Oregon", c))
                .forEach(countSeries::add);
        List.of("Nassau", "New York City", "Rockland", "Suffolk", "Westchester")
                .stream()
                .map(c -> idxUs.getByStateAndLocality("New York", c))
                .forEach(countSeries::add);
        return countSeries;
    }

    @SneakyThrows
    public void emit(File destination) {
        val series = build().stream()
                .map(s -> s
                        .map(DELTA)
                        .map(ROLLING_AVERAGE)
                        .transform(PER_100K))
                .collect(Collectors.toList());

        val rates = new ArrayList<Rates>(dates.length);
        for (int i = 0; i < dates.length; i++) {
            LocalDate d = dates[i];
            val r = new Rates(d, new ArrayListValuedHashMap<>());
            for (val s : series) {
                r.getJurisdictions()
                        .get(s.getDemographics().getCombinedKey())
                        .add(s.getData()[i]);
            }
            rates.add(r);
        }

        val strat = new HeaderColumnNameMappingStrategy<Rates>();
        strat.setType(Rates.class);
        strat.setColumnOrderOnWrite((a, b) -> {
            if ("DATE".equals(a)) return -1;
            if ("DATE".equals(b)) return 1;
            if (WORLDWIDE_KEY.equals(a)) return -1;
            if (WORLDWIDE_KEY.equals(b)) return 1;
            val a1 = a.indexOf(',');
            val b1 = b.indexOf(',');
            if (a1 < 0 && b1 < 0) return a.compareTo(b);
            if (a1 < 0) return -1;
            if (b1 < 0) return 1;
            val a2 = a.indexOf(',', a1 + 1);
            val b2 = b.indexOf(',', b1 + 1);
            if (a2 < 0 && b2 >= 0) return -1;
            if (a2 >= 0 && b2 < 0) return 1;
            return a.compareTo(b);
        });
        try (Writer out = new BufferedWriter(new FileWriter(destination))) {
            new StatefulBeanToCsvBuilder<Rates>(out)
                    .withSeparator('|')
                    .withMappingStrategy(strat)
                    .withApplyQuotesToAll(false)
                    .build()
                    .write(rates);
        }
    }
}
