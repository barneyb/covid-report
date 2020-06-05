package com.barneyb.cdccovid.hopkins;

import com.barneyb.cdccovid.hopkins.csv.CsvTimeSeries;
import com.barneyb.cdccovid.hopkins.csv.Demographics;
import com.barneyb.cdccovid.hopkins.csv.GlobalTimeSeries;
import com.barneyb.cdccovid.hopkins.csv.USTimeSeries;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class HopkinsTransform {

    public static final File DATA_DIR = new File("../COVID-19/csse_covid_19_data");
    public static final File UID_LOOKUP_FILE = new File(DATA_DIR, "UID_ISO_FIPS_LookUp_Table.csv");

    public static final File TIME_SERIES_DIR = new File(DATA_DIR, "csse_covid_19_time_series");
    public static final File GLOBAL_CASES_FILE = new File(TIME_SERIES_DIR, "time_series_covid19_confirmed_global.csv");
    public static final File US_CASES_FILE = new File(TIME_SERIES_DIR, "time_series_covid19_confirmed_US.csv");

    public static final File OUTPUT_DIR = new File("hopkins");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yy");
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
            v / (d.getPopulation() / 100_000);

    @SneakyThrows
    public void transform() {
        if (!OUTPUT_DIR.exists()) {
            //noinspection ResultOfMethodCallIgnored
            OUTPUT_DIR.mkdirs();
        }
        val demographics = loadDemographics();
        val rawGlobal = loadGlobalData();
        val dates = extractDates(rawGlobal.get(0));
        val dateHeaders = buildDateHeaders(dates);

        val globalList = convertRawGlobals(demographics, rawGlobal, dateHeaders);
        val usList = convertRawUs(demographics, loadUSData(), dateHeaders);

        val globalByCountry = new Index<>(globalList, it -> it.getDemographics().getCountry());
        val globalByState = new Index<>(globalList.stream()
                .filter(it -> it.getDemographics().isState()),
                it -> new Pair<>(it.getDemographics().getCountry(), it.getDemographics().getState()));
        val usByState = new Index<>(usList, it -> it.getDemographics().getState());
        val usByCounty = new Index<>(usList.stream()
                .filter(it -> it.getDemographics().isLocal()),
                it -> new Pair<>(it.getDemographics().getState(), it.getDemographics().getLocal()));

        // fake "worldwide" demographics
        val wwDemo = new Demographics();
        wwDemo.setCombinedKey("Worldwide");
        wwDemo.setPopulation(globalList
                .stream()
                .map(TimeSeries::getDemographics)
                .map(Demographics::getPopulation)
                .reduce(0L, Long::sum));

        // sum up globals to get worldwide
        val ww = globalList.stream()
                .reduce(
                        TimeSeries.zeros(wwDemo, dateHeaders),
                        TimeSeries::plus);

        // sum up to get china
        val cnDemo = demographics.getByCountry("China");
        val cn = globalByCountry.get(cnDemo.getCountry()).stream()
                .reduce(
                        TimeSeries.zeros(cnDemo, dateHeaders),
                        TimeSeries::plus);

        val hubei = globalByState.get("China", "Hubei").stream()
                .findFirst().orElseThrow();

        // grab US out of globals
        val us = globalByCountry.get("US").stream()
                .findFirst().orElseThrow();

        // sum up NY counties
        val nyDemo = demographics.getByCountryAndState("US", "New York");
        val ny = usByState.get(nyDemo.getState()).stream()
                .reduce(
                        TimeSeries.zeros(nyDemo, dateHeaders),
                        TimeSeries::plus);

        // US-without-NY
        val usNoNyDemo = new Demographics();
        usNoNyDemo.setCombinedKey("US Except NY");
        usNoNyDemo.setPopulation(us.getDemographics().getPopulation() - ny.getDemographics().getPopulation());
        val usNoNy = TimeSeries.zeros(usNoNyDemo, dateHeaders)
                .plus(us)
                .minus(ny);

        // sum up OR counties
        val orDemo = demographics.getByCountryAndState("US", "Oregon");
        val or = usByState.get(orDemo.getState()).stream()
                .reduce(
                        TimeSeries.zeros(orDemo, dateHeaders),
                        TimeSeries::plus);

        // pull washington county out
        val wash = usByCounty.get("Oregon", "Washington").stream()
                .findFirst().orElseThrow();

        // pull multnomah county out
        val mult = usByCounty.get("Oregon", "Multnomah").stream()
                .findFirst().orElseThrow();

        val marion = usByCounty.get("Oregon", "Marion").stream()
                .findFirst().orElseThrow();

        // sum up CA counties
        val caDemo = demographics.getByCountryAndState("US", "California");
        val ca = usByState.get(caDemo.getState()).stream()
                .reduce(
                        TimeSeries.zeros(caDemo, dateHeaders),
                        TimeSeries::plus);

        // pull san francisco county out
        val sf = usByCounty.get(caDemo.getState(), "San Francisco").stream()
                .findFirst().orElseThrow();

        // pull san mateo county out
        val sm = usByCounty.get(caDemo.getState(), "San Mateo").stream()
                .findFirst().orElseThrow();

        // pull santa clara county out
        val sc = usByCounty.get(caDemo.getState(), "Santa Clara").stream()
                .findFirst().orElseThrow();

        try (PrintWriter out = new PrintWriter(new FileWriter(new File(OUTPUT_DIR, "rates.txt")))) {
            val format = DateTimeFormatter.ofPattern("M/d");
            val countSeries = new LinkedList<>(List.of(ww, us, usNoNy, ny, or, marion, mult, wash, ca, sf, sm, sc, cn, hubei));
            countSeries.addAll(List.of("Italy", "Brazil", "Russia").stream().map(c -> {
                val demo = demographics.getByCountry(c);
                return globalByCountry.get(demo.getCountry()).stream()
                        .reduce(TimeSeries.zeros(demo, dateHeaders), TimeSeries::plus);
            }).collect(Collectors.toList()));
            val series = countSeries.stream()
                    .map(s -> s
                            .map(DELTA)
                            .map(ROLLING_AVERAGE)
                            .transform(PER_100K))
                    .collect(Collectors.toList());
            out.print("Date");
            for (val s : series) {
                out.append('|')
                        .append(s.getDemographics().getCombinedKey());
            }
            out.println();
            for (int i = 0; i < dates.length; i++) {
                LocalDate d = dates[i];
                out.print(d.format(format));
                for (val s : series) {
                    out.append('|').format("%.2f", s.getData()[i]);
                }
                out.println();
            }
        }
    }

    private List<TimeSeries> convertRawUs(IndexedDemographics demographics, List<USTimeSeries> rawUs, String[] dateHeaders) {
        return rawUs.stream()
                .map(it -> new TimeSeries(demographics.getByUid(it.getUid()),
                        dateHeaders,
                        it))
                .collect(Collectors.toList());
    }

    private List<TimeSeries> convertRawGlobals(IndexedDemographics demographics, List<GlobalTimeSeries> rawGlobal, String[] dateHeaders) {
        return rawGlobal.stream()
                .map(it -> new TimeSeries(
                        it.isCountry()
                                ? demographics.getByCountry(it.getCountry())
                                : demographics.getByCountryAndState(it.getCountry(), it.getState()),
                        dateHeaders,
                        it))
                .collect(Collectors.toList());
    }

    private String[] buildDateHeaders(LocalDate[] dates) {
        val dateHeaders = new String[dates.length];
        for (int i = 0, l = dates.length; i < l; i++) {
            dateHeaders[i] = dates[i].format(DATE_FORMAT);
        }
        return dateHeaders;
    }

    private LocalDate[] extractDates(CsvTimeSeries series) {
        return series
                .getDates()
                .keySet()
                .stream()
                .map(s -> DATE_FORMAT.parse(s, LocalDate::from))
                .sorted()
                .toArray(LocalDate[]::new);
    }

    private List<USTimeSeries> loadUSData() throws FileNotFoundException {
        return new CsvToBeanBuilder<USTimeSeries>(new FileReader(US_CASES_FILE))
                .withType(USTimeSeries.class)
                .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS)
                .build()
                .parse();
    }

    private List<GlobalTimeSeries> loadGlobalData() throws FileNotFoundException {
        return new CsvToBeanBuilder<GlobalTimeSeries>(new FileReader(GLOBAL_CASES_FILE))
                .withType(GlobalTimeSeries.class)
                .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS)
                .build()
                .parse();
    }

    private IndexedDemographics loadDemographics() throws FileNotFoundException {
        return new IndexedDemographics(new CsvToBeanBuilder<Demographics>(new FileReader(UID_LOOKUP_FILE))
                .withType(Demographics.class)
                .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS)
                .build()
                .parse());
    }

}
