package com.barneyb.covid.hopkins;

import com.barneyb.covid.Store;
import com.barneyb.covid.hopkins.csv.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

    @Value("${covid-report.output.dir}")
    Path outputDir;

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
        val out = Files.newOutputStream(outputDir.resolve("dashboard.json"));
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

        private static List<Delta> computeDeltas(Stream<CombinedTimeSeries> stream) {
            return stream
                    .map(CombinedTimeSeries::getCasesSeries)
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

        private static Spark spark(CombinedTimeSeries s) {
            val data = s.getCasesSeries().getData();
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

    private static class J {
        @CsvBindByName(column = "UID")
        final int uid;
        @CsvBindByName(column = "COUNTRY")
        final String country;
        @CsvBindByName(column = "STATE")
        final String state;
        @CsvBindByName(column = "LOCALITY")
        final String locality;
        @CsvBindByName(column = "POPULATION")
        final long pop;
        @CsvBindByName(column = "CASES")
        final long cases;
        @CsvBindByName(column = "DEATHS")
        final long deaths;
        // todo: mortality rates?

        J(Demographics demo, double cases, double deaths) {
            this.uid = demo.getUid();
            this.country = demo.getCountry();
            this.state = demo.getState();
            this.locality = demo.getLocality();
            this.pop = demo.getPopulation();
            this.cases = (long) cases;
            this.deaths = (long) deaths;
        }
    }

    private long _prev;
    private void logStep(String message) {
        long now = System.currentTimeMillis();
        if (_prev == 0) {
            logger.info(message);
        } else {
            logger.info(message + " (" + (now - _prev) + " ms)");
        }
        _prev = now;
    }

    @SneakyThrows
    public void transform() {
        logStep("Starting transform");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        } else if (!Files.isDirectory(outputDir)) {
            throw new RuntimeException("Non-directory '" + outputDir + "' found.");
        }
        val demographics = loadDemographics();
        logStep("Demographics loaded and indexed");

        val rawGlobal = loadGlobalData(GLOBAL_CASES_FILE);
        val dates = extractDates(rawGlobal.get(0));
        try (Writer w = Files.newBufferedWriter(outputDir.resolve("last-update.txt"))) {
            // add a day for the UTC/LocalDate dance
            w.write(dates[dates.length - 1].plusDays(1).toString());
        }
        val dateHeaders = buildDateHeaders(dates);
        val idxGlobal = new IndexedWorld(demographics, rawGlobal, loadGlobalData(GLOBAL_DEATHS_FILE), dateHeaders);
        logStep("Global series loaded and indexed");

        val idxUs = new IndexedUS(demographics, loadUSData(US_CASES_FILE), loadUSData(US_DEATHS_FILE), dateHeaders);
        val mortRates = new UniqueIndex<>(
                new CsvToBeanBuilder<MortRates>(new FileReader("mortality.csv"))
                        .withType(MortRates.class)
                        .build()
                        .parse(),
                MortRates::getState);
        logStep("US series loaded and indexed");

        val jurisdictions = demographics
                .cover()
                .filter(d -> !d.isCompleteness())
                .map(d -> {
                    try {
                        if (d.isCountry())
                            return idxGlobal.getByCountry(d.getCountry());
                        if ("US".equals(d.getCountry())) {
                            if (d.isState())
                                return idxUs.getByState(d.getState());
                            if (d.isLocality())
                                return idxUs.getByStateAndLocality(d.getState(), d.getLocality());
                        } else if (d.isState())
                            return idxGlobal.getByCountryAndState(d.getCountry(), d.getState());
                    } catch (UnknownKeyException ignored) {
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .filter(it -> it.getTotalCases() > 0)
                .collect(Collectors.toList());
        jurisdictions.add(0, idxGlobal.getWorldwide());
        try (Writer w = Files.newBufferedWriter(outputDir.resolve("jurisdictions.csv"))) {
            new StatefulBeanToCsvBuilder<J>(w)
                    .withApplyQuotesToAll(false)
                    .build()
                    .write(jurisdictions
                            .stream()
                            .map(p -> new J(
                                    p.getDemographics(),
                                    p.getTotalCases(),
                                    p.getTotalDeaths())));
        }


        val dash = new Dash();
        dash.setDate(dates[dates.length - 1].plusDays(1));
        dash.worldCaseRateDeltas = Dash.computeDeltas(idxGlobal.countries());
        dash.usCaseRateDeltas = Dash.computeDeltas(idxUs.statesAndDC());
        dash.orCaseRateDeltas = Dash.computeDeltas(idxUs.getLocalitiesOfState("Oregon"));
        dash.worldSpark = Dash.spark(idxGlobal.getWorldwide());
        dash.usSpark = Dash.spark(idxGlobal.getByCountry("US"));
        dash.orSpark = Dash.spark(idxUs.getByState("Oregon"));
        dash.washCoSpark = Dash.spark(idxUs.getByStateAndLocality("Oregon", "Washington"));
        dash.multCoSpark = Dash.spark(idxUs.getByStateAndLocality("Oregon", "Multnomah"));
        spew(dash);
        logStep("Dashboard rebuilt");

        new StoreBuilder<>(dates,
                idxUs,
                Demographics::getState,
                (iu, d) -> iu.getByState(d.getState())
        ).updateStore(usStore, demographics.usStatesAndDC(), j ->
                j.setMortalityRates(mortRates.get(j.getName()).unwrapRates()));
        logStep("US database rebuilt");

        new StoreBuilder<>(dates,
                idxGlobal,
                Demographics::getCountry,
                (iw, d) -> iw.getByCountry(d.getCountry())
        ).updateStore(wwStore, demographics.countries());
        logStep("Worldwide database rebuilt");

        new RatesBuilder(dates, CombinedTimeSeries::getCasesSeries, idxGlobal, idxUs)
                .emit(outputDir.resolve("rates-cases.txt"));
        logStep("Case rates rebuilt");

        new RatesBuilder(dates, CombinedTimeSeries::getDeathsSeries, idxGlobal, idxUs)
                .emit(outputDir.resolve("rates-deaths.txt"));
        logStep("Death rates rebuilt");
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
