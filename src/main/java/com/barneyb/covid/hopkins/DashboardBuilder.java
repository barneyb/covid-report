package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.barneyb.covid.hopkins.RatesBuilder.DELTA;
import static com.barneyb.covid.hopkins.RatesBuilder.ROLLING_AVERAGE;

@Component
public class DashboardBuilder {

    @Autowired
    ObjectMapper mapper;

    @SneakyThrows
    public void emit(IndexedWorld idxGlobal, IndexedUS idxUs, Path destination) {
        val dash = new Dash();
        dash.addArea(idxGlobal.getWorldwide(), idxGlobal.countries());
        dash.addArea(idxGlobal.getByCountry("US"), idxUs.statesAndDC());
        dash.addArea(idxUs.getByState("Oregon"), idxUs.getLocalitiesOfState("Oregon"));
        val wash = idxUs.getByStateAndLocality("Oregon", "Washington");
        val mult = idxUs.getByStateAndLocality("Oregon", "Multnomah");
        dash.addArea(idxUs.getByStateAndLocality("Oregon", "Portland Metro"),
                Stream.of(
                        idxUs.getByStateAndLocality("Oregon", "Clackamas"),
                        mult,
                        wash
                ));
        dash.addArea(mult);
        dash.addArea(wash);
        List.of("Arizona", "California", "Florida", "Texas").forEach(s ->
            dash.addArea(idxUs.getByState(s), idxUs.getLocalitiesOfState(s)));
        try (val out = Files.newOutputStream(destination)) {
            mapper
//                    .writerWithDefaultPrettyPrinter() // todo: comment out?
                    .writeValue(out, dash);
        }
    }

    @Data
    @AllArgsConstructor
    public static class Segment {
        String name;
        long pop;
        double delta;
    }

    @Data
    @RequiredArgsConstructor
    @AllArgsConstructor
    private static class Area {
        @NonNull
        String name;
        @NonNull
        long pop;
        @NonNull
        int total;
        @NonNull
        int daily;
        @NonNull
        double[] values;
        List<Segment> segments;

        private static final int SPARK_DAYS = 21;

        private static Area of(TimeSeries series, Stream<TimeSeries> breakdown) {
            val area = of(series);
            area.segments = breakdown
                    .map(s -> {
                        double[] data = s.getData();
                        final int t2 = data.length - 1,
                                t1 = t2 - 7,
                                t0 = t1 - 7;
                        final double val = data[t2] - data[t1],
                                prev = data[t1] - data[t0],
                                delta = prev == 0
                                        ? 10 // Any increase from zero means tenfold! By fiat!
                                        : (val - prev) / prev;
                        return new Segment(
                                s.getDemographics().getCombinedKey(),
                                s.getDemographics().getPopulation(),
                                delta);
                    })
                    .collect(Collectors.toList());
            return area;
        }

        private static Area of(TimeSeries series) {
            val data = series.getData();
            int len = data.length;
            Demographics demo = series.getDemographics();
            return new Area(
                    demo.getCombinedKey(),
                    demo.getPopulation(),
                    (int) data[len - 1],
                    (int) (data[len - 1] - data[len - 2]),
                    Optional.of(Arrays.copyOfRange(data, len - SPARK_DAYS - 7, len))
                            .map(DELTA)
                            .map(ROLLING_AVERAGE)
                            .map(d -> {
                                int l = d.length;
                                return Arrays.copyOfRange(d, l - SPARK_DAYS, l);
                            })
                            .orElseThrow());
        }
    }

    @Data
    private static class Dash {
        List<Area> areas = new ArrayList<>();

        private void addArea(CombinedTimeSeries s, Stream<CombinedTimeSeries> breakdown) {
            areas.add(Area.of(s.getCasesSeries(), breakdown.map(CombinedTimeSeries::getCasesSeries)));
        }

        private void addArea(CombinedTimeSeries s) {
            areas.add(Area.of(s.getCasesSeries()));
        }
    }
}
