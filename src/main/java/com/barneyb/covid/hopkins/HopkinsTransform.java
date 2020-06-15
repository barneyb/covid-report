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
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
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
        demographics.createWorldwide(idxGlobal
                .countries()
                .map(CombinedTimeSeries::getDemographics)
                .map(Demographics::getPopulation)
                .reduce(0L, Long::sum));
        idxGlobal.createWorldwide(demographics.getWorldwide());
        logStep("Global series loaded and indexed");

        val idxUs = new IndexedUS(demographics, loadUSData(US_CASES_FILE), loadUSData(US_DEATHS_FILE), dateHeaders);
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

        @AllArgsConstructor
        class Slice {
            final String key;
            final Stream<CombinedTimeSeries> stream;
        }

        Pattern NON_LETTER = Pattern.compile("[^a-z]+");
        // worldwide & countries
        var slices = Stream.of(new Slice("global", Stream.concat(
                Stream.of(idxGlobal.getWorldwide()),
                idxGlobal.countries()
        )));
        // e.g., china & provinces
        slices = Stream.concat(slices, idxGlobal.countriesWithStates()
                .map(c -> new Slice(c, Stream.concat(
                        Stream.of(idxGlobal.getByCountry(c)),
                        idxGlobal.getStatesOfCountry(c)
                ))));
        // us & states
        slices = Stream.concat(slices, Stream.of(new Slice("us", Stream.concat(
                Stream.of(idxGlobal.getByCountry("US")),
                idxUs.statesAndDC()
        ))));
        // e.g., oregon & counties
        slices = Stream.concat(slices, demographics.usStatesAndDC()
                .map(Demographics::getState)
                .map(s -> new Slice(s, Stream.concat(
                        Stream.of(idxUs.getByState(s)),
                        idxUs.getLocalitiesOfState(s)
                ))));

        Function<TimeSeries, String> labeler = s -> NON_LETTER.matcher(s
                .getDemographics()
                .getCombinedKey()
                .toLowerCase()
        ).replaceAll("-");
        val hotDemos = new TreeSet<>(Comparator.comparing(CombinedTimeSeries::getDemographics));
        slices.forEach(slice -> {
            val key = NON_LETTER.matcher(slice.key.toLowerCase()).replaceAll("-");
            try (Writer cw = Files.newBufferedWriter(outputDir.resolve(key + "-cases.csv"));
                 Writer dw = Files.newBufferedWriter(outputDir.resolve(key + "-deaths.csv"))) {
                slice.stream
                        .filter(it -> it.getTotalCases() > 0)
                        .forEach(s -> {
                            hotDemos.add(s);
                            try {
                                cw.write(asUidIntCsv(s.getCasesSeries(), labeler));
                                dw.write(asUidIntCsv(s.getDeathsSeries(), labeler));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        logStep("Slices rebuilt");
        try (Writer w = Files.newBufferedWriter(outputDir.resolve("jurisdictions.csv"))) {
            new StatefulBeanToCsvBuilder<J>(w)
                    .withApplyQuotesToAll(false)
                    .build()
                    .write(hotDemos
                            .stream()
                            .map(p -> new J(
                                    p.getDemographics(),
                                    p.getTotalCases(),
                                    p.getTotalDeaths())));
        }
        logStep("Jurisdictions rebuilt");

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

    private String asUidIntCsv(TimeSeries s, Function<TimeSeries, String> labeler) {
        val sb = new StringBuilder();
        sb.append(s.getDemographics().getUid());
        sb.append(',').append(labeler.apply(s));
        for (val d : s.getData()) {
            sb.append(',').append((int) (d + 0.5));
        }
        sb.append('\n');
        return sb.toString();
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
