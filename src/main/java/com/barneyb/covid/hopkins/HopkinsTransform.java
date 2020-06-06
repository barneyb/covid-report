package com.barneyb.covid.hopkins;

import com.barneyb.covid.Store;
import com.barneyb.covid.hopkins.csv.*;
import com.barneyb.covid.model.DataPoint;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    public static final File US_DEATHS_FILE = new File(TIME_SERIES_DIR, "time_series_covid19_deaths_US.csv");

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

    public static final String WORLDWIDE = "Worldwide";

    @Autowired
    private Store store;

    @SneakyThrows
    public void transform() {
        if (!OUTPUT_DIR.exists()) {
            //noinspection ResultOfMethodCallIgnored
            OUTPUT_DIR.mkdirs();
        }
        val demographics = loadDemographics();
        val rawGlobal = loadGlobalData(GLOBAL_CASES_FILE);
        val dates = extractDates(rawGlobal.get(0));
        val dateHeaders = buildDateHeaders(dates);

        val globalCases = new IndexedWorld(demographics, rawGlobal, dateHeaders);
        val usCases = new IndexedUS(demographics, loadUSData(US_CASES_FILE), dateHeaders);
        val usDeaths = new IndexedUS(demographics, loadUSData(US_DEATHS_FILE), dateHeaders);

        val idxFirstFriday = Arrays.binarySearch(dates, LocalDate.of(2020, 3, 20));
        store.getAllJurisdictions().forEach(j -> {
            j.setPopulation(demographics.getByCountryAndState(
                    "US",
                    j.getName()).getPopulation());
            val cases = usCases.getByState(j.getName()).getData();
            val deaths = usDeaths.getByState(j.getName()).getData();
            assert cases.length == deaths.length : "case and death series are different lengths";
            val points = new ArrayList<DataPoint>();
            for (int i = idxFirstFriday, l = cases.length; i < l; i += 7) {
                assert dates[i].getDayOfWeek().equals(DayOfWeek.FRIDAY) : "not a friday?";
                points.add(new DataPoint(
                        dates[i],
                        (int) cases[i],
                        (int) deaths[i]));
            }
            j.setPoints(points);
        });
        store.close();

        // fake "worldwide" demographics
        val wwDemo = new Demographics();
        wwDemo.setCombinedKey(WORLDWIDE);
        wwDemo.setPopulation(globalCases.cover()
                .map(TimeSeries::getDemographics)
                .map(Demographics::getPopulation)
                .reduce(0L, Long::sum));

        // sum up globals to get worldwide
        val ww = globalCases.cover()
                .reduce(TimeSeries::plus)
                .orElseThrow();
        ww.setDemographics(wwDemo);

        val hubei = globalCases.getByCountryAndState("China", "Hubei");
        val us = globalCases.getByCountry("US");

        val ny = usCases.getByState("New York");

        // US-without-NY
        val usNoNyDemo = new Demographics();
        usNoNyDemo.setCombinedKey("US Except NY");
        usNoNyDemo.setPopulation(us.getDemographics().getPopulation() - ny.getDemographics().getPopulation());
        val usNoNy = TimeSeries.zeros(usNoNyDemo, dateHeaders)
                .plus(us)
                .minus(ny);

        val countSeries = new LinkedList<>(List.of(ww, usNoNy, hubei));
        List.of("China", "Italy", "Brazil", "France", "Russia", "US")
                .stream()
                .map(globalCases::getByCountry)
                .forEach(countSeries::add);
        List.of("Arizona", "California", "Florida", "Georgia", "Illinois", "Louisiana", "Massachusetts", "Michigan", "New York", "Oregon", "Pennsylvania", "Texas")
                .stream()
                .map(usCases::getByState)
                .forEach(countSeries::add);
        List.of("Alameda", "Contra Costa", "Los Angeles", "Marin", "Napa", "Orange", "San Diego", "San Francisco", "San Mateo", "Santa Clara", "Solano", "Sonoma", "Ventura")
                .stream()
                .map(c -> usCases.getByStateAndLocality("California", c))
                .forEach(countSeries::add);
        List.of("Clackamas", "Marion", "Multnomah", "Washington")
                .stream()
                .map(c -> usCases.getByStateAndLocality("Oregon", c))
                .forEach(countSeries::add);
        List.of("Nassau", "New York City", "Rockland", "Suffolk", "Westchester")
                .stream()
                .map(c -> usCases.getByStateAndLocality("New York", c))
                .forEach(countSeries::add);
        val series = countSeries.stream()
                .map(s -> s
                        .map(DELTA)
                        .map(ROLLING_AVERAGE)
                        .transform(PER_100K))
                .collect(Collectors.toList());

        val rates = new ArrayList<Rates>(dateHeaders.length);
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
            if (WORLDWIDE.equals(a)) return -1;
            if (WORLDWIDE.equals(b)) return 1;
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
        try (Writer out = new BufferedWriter(new FileWriter(new File(OUTPUT_DIR, "rates.txt")))) {
            new StatefulBeanToCsvBuilder<Rates>(out)
                    .withSeparator('|')
                    .withMappingStrategy(strat)
                    .withApplyQuotesToAll(false)
                    .build()
                    .write(rates);
        }
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

    private List<USTimeSeries> loadUSData(File src) throws FileNotFoundException {
        return new CsvToBeanBuilder<USTimeSeries>(new FileReader(src))
                .withType(USTimeSeries.class)
                .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS)
                .build()
                .parse();
    }

    private List<GlobalTimeSeries> loadGlobalData(File src) throws FileNotFoundException {
        return new CsvToBeanBuilder<GlobalTimeSeries>(new FileReader(src))
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
