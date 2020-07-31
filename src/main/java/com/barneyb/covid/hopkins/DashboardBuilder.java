package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.barneyb.covid.hopkins.RatesBuilder.DELTA;
import static com.barneyb.covid.hopkins.RatesBuilder.ROLLING_AVERAGE;

@Component
public class DashboardBuilder {

    @Autowired
    ObjectWriter writer;

    private Collection<Area> areaAndItsHotSegments(
            CombinedTimeSeries main,
            Supplier<Stream<CombinedTimeSeries>> segmentSupplier,
            Function<CombinedTimeSeries, Optional<Stream<CombinedTimeSeries>>> subsegmentSupplier
    ) {
        val segments = new LinkedHashSet<CombinedTimeSeries>();
        // hottest segment overall
        segmentSupplier.get()
                .filter(s -> !"US".equals(s.getDemographics().getCombinedKey()))
                .filter(s -> s.getCasesSeries().getNewThisWeek() > 0)
                .sorted(Comparator.comparing(CombinedTimeSeries::getTotalCaseRate).reversed())
                .limit(1)
                .forEach(segments::add);
        // the three hottest segments this week
        segmentSupplier.get()
                .filter(s -> s.getCasesSeries().getNewThisWeek() > 0)
                .sorted(Comparator
                        .comparing((CombinedTimeSeries s) ->
                                s.getCasesSeries().getNewThisWeekRate()
                        ).reversed())
                .limit(3)
                .forEach(segments::add);
        val areas = new ArrayList<Area>();
        areas.add(Area.ofCases(main, segmentSupplier.get()));
        segments.forEach(s -> areas.add(subsegmentSupplier.apply(s)
                .map(segs -> Area.ofCases(s, segs))
                .orElseGet(() -> Area.ofCases(s))));
        return areas;
    }

    @SneakyThrows
    public void emit(IndexedWorld idxGlobal, IndexedUS idxUs, Path destination) {
        val dash = new Dash();
        dash.section("Worldwide", () -> areaAndItsHotSegments(
                idxGlobal.getWorldwide(),
                idxGlobal::countries,
                c -> {
                    val country = c.getDemographics().getCountry();
                    if (!idxGlobal.hasStates(country)) return Optional.empty();
                    return Optional.of(idxGlobal.getStatesOfCountry(country));
                }
        ));
        dash.section("United States", () -> areaAndItsHotSegments(
                idxGlobal.getByCountry("US"),
                idxUs::statesAndDC,
                s -> Optional.of(idxUs.getLocalitiesOfState(s.getDemographics().getState()))
        ));
        dash.section("Oregon, US", () -> {
            val wash = idxUs.getByStateAndLocality("Oregon", "Washington");
            val mult = idxUs.getByStateAndLocality("Oregon", "Multnomah");
            return List.of(
                    Area.ofCases(idxUs.getByState("Oregon"), idxUs.getLocalitiesOfState("Oregon")),
                    Area.ofCases(idxUs.getByStateAndLocality("Oregon", "Portland Metro"),
                            Stream.of(
                                    idxUs.getByStateAndLocality("Oregon", "Clackamas"),
                                    mult,
                                    wash
                            )),
                    Area.ofCases(mult),
                    Area.ofCases(wash)
            );
        });

        try (val out = Files.newOutputStream(destination)) {
            writer.writeValue(out, dash);
        }
    }

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(of = {"name"})
    public static class Segment {
        String name;
        long pop;
        double delta;
    }

    @Data
    @RequiredArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = {"name"})
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

        private static Area ofCases(CombinedTimeSeries series) {
            return of(series.getCasesSeries());
        }

        private static Area ofCases(CombinedTimeSeries series, Stream<CombinedTimeSeries> breakdown) {
            return of(series.getCasesSeries(), breakdown
                    .map(CombinedTimeSeries::getCasesSeries));
        }

        private static Area of(TimeSeries series, Stream<TimeSeries> breakdown) {
            val area = of(series);
            area.segments = breakdown
                    .map(s -> new Segment(
                            s.getDemographics().getCombinedKey(),
                            s.getDemographics().getPopulation(),
                            s.getWeekOverWeek()))
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
                    (int) series.getCurrent(),
                    (int) (series.getNewThisWeek() / 7),
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
    @RequiredArgsConstructor
    private static class Section {
        @NonNull
        final String label;
        final List<String> areas = new LinkedList<>();
    }

    @Data
    private static class Dash {
        List<Section> sections = new LinkedList<>();
        Map<String, Area> lookup = new LinkedHashMap<>();

        private void section(String name, Supplier<Collection<Area>> supplier) {
            section(name, supplier.get());
        }

        private void section(String name, Collection<Area> areas) {
            val sec = new Section(name);
            areas.forEach(a -> {
                sec.areas.add(a.name);
                lookup.putIfAbsent(a.name, a);
            });
            sections.add(sec);
        }

    }
}
