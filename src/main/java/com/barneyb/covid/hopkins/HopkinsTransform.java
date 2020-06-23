package com.barneyb.covid.hopkins;

import com.barneyb.covid.Store;
import com.barneyb.covid.hopkins.csv.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.barneyb.covid.hopkins.RatesBuilder.DELTA;
import static com.barneyb.covid.hopkins.RatesBuilder.ROLLING_AVERAGE;

@Component
public class HopkinsTransform implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(HopkinsTransform.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yy");

    @Value("${covid-report.hopkins.dir}")
    Path dataDir;
    private Path uidLookupFile;
    private Path globalCasesFile;
    private Path globalDeathsFile;
    private Path usCasesFile;
    private Path usDeathsFile;

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

    @Override
    public void afterPropertiesSet() {
        uidLookupFile = dataDir.resolve("UID_ISO_FIPS_LookUp_Table.csv");
        val tsDir = dataDir.resolve("csse_covid_19_time_series");
        globalCasesFile = tsDir.resolve("time_series_covid19_confirmed_global.csv");
        globalDeathsFile = tsDir.resolve("time_series_covid19_deaths_global.csv");
        usCasesFile = tsDir.resolve("time_series_covid19_confirmed_US.csv");
        usDeathsFile = tsDir.resolve("time_series_covid19_deaths_US.csv");
    }

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
    @RequiredArgsConstructor
    @AllArgsConstructor
    private static class Spark {
        @NonNull
        String name;
        @NonNull
        int total;
        @NonNull
        int daily;
        @NonNull
        double[] values;
        List<Delta> breakdown;
    }

    @Data
    private static class Dash {
        List<Spark> sparks = new ArrayList<>();

        private void addSpark(CombinedTimeSeries s, Stream<CombinedTimeSeries> breakdown) {
            sparks.add(spark(s, breakdown));
        }

        private void addSpark(CombinedTimeSeries s) {
            sparks.add(spark(s));
        }

        private static final int SPARK_DAYS = 21;

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

        private static Spark spark(CombinedTimeSeries s, Stream<CombinedTimeSeries> breakdown) {
            val spark = spark(s);
            spark.breakdown = computeDeltas(breakdown);
            return spark;
        }

        private static Spark spark(CombinedTimeSeries s) {
            val data = s.getCasesSeries().getData();
            int len = data.length;
            return new Spark(
                    s.getDemographics().getCombinedKey(),
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

        val rawGlobal = loadGlobalData(globalCasesFile);
        val dates = extractDates(rawGlobal.get(0));
        try (Writer w = Files.newBufferedWriter(outputDir.resolve("last-update.txt"))) {
            // add a day for the UTC/LocalDate dance
            w.write(dates[dates.length - 1].plusDays(1).toString());
        }
        val dateHeaders = buildDateHeaders(dates);
        val idxGlobal = new IndexedWorld(demographics, rawGlobal, loadGlobalData(globalDeathsFile), dateHeaders);
        demographics.createWorldwide(idxGlobal
                .countries()
                .map(CombinedTimeSeries::getDemographics)
                .map(Demographics::getPopulation)
                .reduce(0L, Long::sum));
        idxGlobal.createWorldwide(demographics.getWorldwide());
        logStep("Global series loaded and indexed");

        val idxUs = new IndexedUS(demographics, loadUSData(usCasesFile), loadUSData(usDeathsFile), dateHeaders);
        demographics.createUsExceptNy(idxUs
                .statesAndDC()
                .map(CombinedTimeSeries::getDemographics)
                .filter(d -> !"New York".equals(d.getState()))
                .map(Demographics::getPopulation)
                .reduce(0L, Long::sum));
        idxUs.createUsExceptNy(demographics.getUsExceptNy());

        val mortRates = new UniqueIndex<>(
                new CsvToBeanBuilder<MortRates>(new FileReader("mortality.csv"))
                        .withType(MortRates.class)
                        .build()
                        .parse(),
                MortRates::getState);
        logStep("US series loaded and indexed");

        val dash = new Dash();
        dash.addSpark(idxGlobal.getWorldwide(), idxGlobal.countries());
        dash.addSpark(idxGlobal.getByCountry("US"), idxUs.statesAndDC());
        dash.addSpark(idxUs.getByState("Oregon"), idxUs.getLocalitiesOfState("Oregon"));
        val wash = idxUs.getByStateAndLocality("Oregon", "Washington");
        val mult = idxUs.getByStateAndLocality("Oregon", "Multnomah");
        dash.addSpark(idxUs.getByStateAndLocality("Oregon", "Portland Metro"),
                Stream.of(
                        idxUs.getByStateAndLocality("Oregon", "Clackamas"),
                        mult,
                        wash
                ));
        dash.addSpark(mult);
        dash.addSpark(wash);
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

    private List<USTimeSeries> loadUSData(Path src) throws IOException {
        return new CsvToBeanBuilder<USTimeSeries>(Files.newBufferedReader(src))
                .withType(USTimeSeries.class)
                .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS)
                .build()
                .parse();
    }

    private List<GlobalTimeSeries> loadGlobalData(Path src) throws IOException {
        return new CsvToBeanBuilder<GlobalTimeSeries>(Files.newBufferedReader(src))
                .withType(GlobalTimeSeries.class)
                .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS)
                .build()
                .parse();
    }

    private IndexedDemographics loadDemographics() throws IOException {
        return new IndexedDemographics(new CsvToBeanBuilder<Demographics>(Files.newBufferedReader(uidLookupFile))
                .withType(Demographics.class)
                .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS)
                .build()
                .parse());
    }

}
