package com.barneyb.covid.hopkins;

import com.barneyb.covid.Store;
import com.barneyb.covid.hopkins.csv.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.barneyb.covid.hopkins.RatesBuilder.DELTA;
import static com.barneyb.covid.hopkins.RatesBuilder.ROLLING_AVERAGE;

@Component
public class HopkinsTransform {

    private static final Logger logger = LoggerFactory.getLogger(HopkinsTransform.class);

    public static final File DATA_DIR = new File("../COVID-19/csse_covid_19_data");
    public static final File UID_LOOKUP_FILE = new File(DATA_DIR, "UID_ISO_FIPS_LookUp_Table.csv");

    public static final File TIME_SERIES_DIR = new File(DATA_DIR, "csse_covid_19_time_series");
    public static final File GLOBAL_CASES_FILE = new File(TIME_SERIES_DIR, "time_series_covid19_confirmed_global.csv");
    public static final File GLOBAL_DEATHS_FILE = new File(TIME_SERIES_DIR, "time_series_covid19_deaths_global.csv");
    public static final File US_CASES_FILE = new File(TIME_SERIES_DIR, "time_series_covid19_confirmed_US.csv");
    public static final File US_DEATHS_FILE = new File(TIME_SERIES_DIR, "time_series_covid19_deaths_US.csv");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yy");

    @Autowired
    @Qualifier("us")
    Store usStore;

    @Autowired
    @Qualifier("worldwide")
    Store wwStore;

    @Autowired
    ObjectMapper mapper;


    @SneakyThrows
    public void spew(Object model) {
        val out = Files.newOutputStream(Path.of("dashboard.json"));
        mapper
//                .writerWithDefaultPrettyPrinter() // todo: comment out?
                .writeValue(out, model);
        out.close();
    }

    @Data
    @AllArgsConstructor
    private static class Delta {
        String name;
        long pop;
        double delta;
    }

    @Data
    @AllArgsConstructor
    private static class Spark {
        String name;
        int total;
        double[] deltas;
    }

    @Data
    private static class Dash {
        LocalDate date;
        List<Delta> worldCaseRateDeltas;
        List<Delta> usCaseRateDeltas;
        List<Delta> orCaseRateDeltas;
        Spark worldSpark;
        Spark usSpark;
        Spark orSpark;
        Spark washCoSpark;
        Spark multCoSpark;

        private static List<Delta> computeDeltas(Stream<TimeSeries> stream) {
            return stream
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
                        return new Delta(
                                s.getDemographics().getCombinedKey(),
                                s.getDemographics().getPopulation(),
                                delta);
                    })
                    .collect(Collectors.toList());
        }

        private static Spark spark(TimeSeries s) {
            val data = s.getData();
            int len = data.length;
            return new Spark(
                    s.getDemographics().getCombinedKey(),
                    (int) data[len - 1],
                    Optional.of(Arrays.copyOfRange(data, len - 21, len))
                            .map(DELTA)
                            .map(ROLLING_AVERAGE)
                            .map(d -> {
                                int l = d.length;
                                return Arrays.copyOfRange(d, l - 14, l);
                            })
                            .orElseThrow());
        }
    }

    @SneakyThrows
    public void transform() {
        logger.info("Starting transform");
        val demographics = loadDemographics();
        logger.info("Demographics loaded");
        val rawGlobal = loadGlobalData(GLOBAL_CASES_FILE);
        logger.info("Globals loaded");
        val dates = extractDates(rawGlobal.get(0));
        logger.info("Dates extracted");
        val dateHeaders = buildDateHeaders(dates);
        logger.info("Headers built");

        val globalCases = new IndexedWorld(demographics, rawGlobal, dateHeaders);
        val globalDeaths = new IndexedWorld(demographics, loadGlobalData(GLOBAL_DEATHS_FILE), dateHeaders);
        logger.info("Global series loaded and indexed");

        val usCases = new IndexedUS(demographics, loadUSData(US_CASES_FILE), dateHeaders);
        val usDeaths = new IndexedUS(demographics, loadUSData(US_DEATHS_FILE), dateHeaders);
        logger.info("US series loaded and indexed");
        val mortRates = new UniqueIndex<>(
                new CsvToBeanBuilder<MortRates>(new FileReader("mortality.csv"))
                        .withType(MortRates.class)
                        .build()
                        .parse(),
                MortRates::getState);
        logger.info("Mortality rates loaded and indexed");

        val dash = new Dash();
        dash.setDate(dates[dates.length - 1].plusDays(1));
        dash.worldCaseRateDeltas = Dash.computeDeltas(globalCases.countries());
        dash.usCaseRateDeltas = Dash.computeDeltas(usCases.allStates());
        dash.orCaseRateDeltas = Dash.computeDeltas(usCases.getLocalitiesOfState("Oregon"));
        dash.worldSpark = Dash.spark(globalCases.getWorldwide());
        dash.usSpark = Dash.spark(globalCases.getByCountry("US"));
        dash.orSpark = Dash.spark(usCases.getByState("Oregon"));
        dash.washCoSpark = Dash.spark(usCases.getByStateAndLocality("Oregon", "Washington"));
        dash.multCoSpark = Dash.spark(usCases.getByStateAndLocality("Oregon", "Multnomah"));
        spew(dash);
        logger.info("Dashboard rebuilt");

        new StoreBuilder<>(dates,
                usCases,
                usDeaths,
                Demographics::getState,
                (iu, d) -> iu.getByState(d.getState())
        ).updateStore(usStore, demographics.usStatesAndDC(), j ->
                j.setMortalityRates(mortRates.get(j.getName()).unwrapRates()));
        logger.info("US database rebuilt");

        new StoreBuilder<>(dates,
                globalCases,
                globalDeaths,
                Demographics::getCountry,
                (iw, d) -> iw.getByCountry(d.getCountry())
        ).updateStore(wwStore, demographics.countries());
        logger.info("Worldwide database rebuilt");

        new RatesBuilder(dates, globalCases, usCases)
                .emit(new File("rates-cases.txt"));
        logger.info("Case rates rebuilt");

        new RatesBuilder(dates, globalDeaths, usDeaths)
                .emit(new File("rates-deaths.txt"));
        logger.info("Death rates rebuilt");
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
