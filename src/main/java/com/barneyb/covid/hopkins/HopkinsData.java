package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.*;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public class HopkinsData {

    private Path uidLookupFile;
    private Path globalCasesFile;
    private Path globalDeathsFile;
    private Path usCasesFile;
    private Path usDeathsFile;

    @Value("${covid-report.hopkins.dir}")
    public void setDataDir(Path dataDir) {
        uidLookupFile = dataDir.resolve("UID_ISO_FIPS_LookUp_Table.csv");
        val tsDir = dataDir.resolve("csse_covid_19_time_series");
        globalCasesFile = tsDir.resolve("time_series_covid19_confirmed_global.csv");
        globalDeathsFile = tsDir.resolve("time_series_covid19_deaths_global.csv");
        usCasesFile = tsDir.resolve("time_series_covid19_confirmed_US.csv");
        usDeathsFile = tsDir.resolve("time_series_covid19_deaths_US.csv");
    }

    public Collection<UidLookup> loadUidLookup() {
        return streamUidLookup()
                .collect(Collectors.toList());
    }

    public Stream<UidLookup> streamUidLookup() {
        return streamUids(UidLookup.class);
    }

    private <T extends UidLookup> Stream<T> streamUids(Class<T> clazz) {
        /*
         * Munge Kansas City's counties' populations to avoid double-counting
         * the city's residents, as the city is a only a reporting area for
         * covid, not population. Assume an even distribution of the city across
         * the counties, which is not correct, but it's at least arguable in the
         * face of no better data.
         */
        final long popKansasCity = 488943;
        final long popCass = 105780;
        final long popClay = 249948;
        final long popJackson = 703011;
        final long popPlatte = 104418;
        final long popTotal = popCass + popClay + popJackson + popPlatte;
        final long adjustCass = (long) ((1.0 * popCass / popTotal) * popKansasCity);
        final long adjustClay = (long) ((1.0 * popClay / popTotal) * popKansasCity);
        final long adjustJackson = (long) ((1.0 * popJackson / popTotal) * popKansasCity);
        final long adjustPlatte = (long) ((1.0 * popPlatte / popTotal) * popKansasCity);
        Function<T, T> kansasCity = d -> {
            if (!d.isLocality()) return d;
            if (!"Missouri".equals(d.getState())) return d;
            long adjust = 0;
            switch (d.getLocality()) {
                case "Cass":
                    adjust = adjustCass;
                    break;
                case "Clay":
                    adjust = adjustClay;
                    break;
                case "Jackson":
                    adjust = adjustJackson;
                    break;
                case "Platte":
                    adjust = adjustPlatte;
                    break;
            }
            if (adjust != 0) d.setPopulation(d.getPopulation() - adjust);
            return d;
        };
        /*
         * The Channel Islands are grouped together for reporting, and given the
         * ISO codes of the UK. Instead, use Guernsey's code, so they can be
         * separated from the UK, even though Jersey and Guernsey are still
         * mashed together. Guernsey was selected arbitrarily; just needed
         * something reserved (avoid conflict with new codes) and w/in the
         * Channel Islands (avoid conflict if they start reporting separately).
         */
        Function<T, T> channelIslands = d -> {
            if (!d.isState()) return d;
            if (!"United Kingdom".equals(d.getCountry())) return d;
            if (!"Channel Islands".equals(d.getState())) return d;
            d.setUid(831);
            d.setIso2("GG");
            d.setIso3("GGY");
            d.setCode3("831");
            return d;
        };
        /*
         * The 2020 Summer Olympics are now happening (a year late) and they
         * are outside any sort of normal geo thing. So fake it?
         */
        Function<T, T> olympics = d -> {
            if (!"Summer Olympics 2020".equals(d.getCountry())) return d;
            d.setIso2("20");
            d.setIso3("O20");
            d.setCode3("O20");
            return d;
        };
        return build(uidLookupFile, clazz)
                .stream()
                .filter(d -> isNotCruiseShip(d.getCountry()))
                .map(kansasCity)
                .map(channelIslands)
                .map(olympics);
    }

    public Collection<USTimeSeries> loadUSCases() {
        return loadUS(usCasesFile);
    }

    public Stream<USTimeSeries> streamUSCases() {
        return streamUS(usCasesFile);
    }

    public Collection<USTimeSeries> loadUSDeaths() {
        return loadUS(usDeathsFile);
    }

    public Stream<USTimeSeries> streamUSDeaths() {
        return streamUS(usDeathsFile);
    }

    private Collection<USTimeSeries> loadUS(Path src) {
        return streamUS(src)
                .collect(Collectors.toList());
    }

    private Stream<USTimeSeries> streamUS(Path src) {
        return buildTimeSeries(src, USTimeSeries.class)
                .stream()
                // Exception type 1, such as recovered and Kansas City, ranging from 8407001 to 8407999.
                // Dukes and Nantucket, Mass.
                .filter(d -> {
                    if (!"Massachusetts".equals(d.getState())) return true;
                    switch (d.getLocality()) {
                        case "Dukes":
                        case "Nantucket":
                            return false;
                        default:
                            return true;
                    }
                })
                // Kansas City - adjust the county populations KC is partially in
                // Michigan's state/federal corrections are outside the counties they reside in; they are a legit locality, though without population data.
                // Utah's districts
                .filter(d -> {
                    if (!"Utah".equals(d.getState())) return true;
                    switch (d.getLocality()) {
                        // Bear River
                        case "Box Elder":
                        case "Cache":
                        case "Rich":
                        // Central Utah
                        case "Juab":
                        case "Millard":
                        case "Piute":
                        case "Sanpete":
                        case "Sevier":
                        case "Wayne":
                        // Southeast Utah
                        case "Carbon":
                        case "Emery":
                        case "Grand":
                        // Southwest Utah
                        case "Beaver":
                        case "Garfield":
                        case "Iron":
                        case "Kane":
                        case "Washington":
                        // Tricounty
                        case "Daggett":
                        case "Duchesne":
                        case "Uintah":
                        // Weber-Morgan
                        case "Morgan":
                        case "Weber":
                            return false;
                        default:
                            return true;
                    }
                })
                // Exception type 2, only the New York City, which is replacing New York County and its FIPS code.
                .filter(d -> {
                    if (!"New York".equals(d.getState())) return true;
                    switch (d.getLocality()) {
                        case "Bronx":
                        case "Kings":
                        case "Queens":
                        case "Richmond":
                            return false;
                        default:
                            return true;
                    }
                })
                // Exception type 3, Diamond Princess, US: 84088888; Grand Princess, US: 84099999.
                .filter(d -> isNotCruiseShip(d.getState()));
    }

    public Collection<GlobalTimeSeries> loadGlobalCases() {
        return loadGlobal(globalCasesFile);
    }

    public Stream<GlobalTimeSeries> streamGlobalCases() {
        return streamGlobal(globalCasesFile);
    }

    public Collection<GlobalTimeSeries> loadGlobalDeaths() {
        return loadGlobal(globalDeathsFile);
    }

    public Stream<GlobalTimeSeries> streamGlobalDeaths() {
        return streamGlobal(globalDeathsFile);
    }

    private Collection<GlobalTimeSeries> loadGlobal(Path src) {
        return streamGlobal(src)
                .collect(Collectors.toList());
    }

    private boolean isNotCruiseShip(String name) {
        return !isCruiseShip(name);
    }

    private boolean isCruiseShip(String name) {
        if ("Grand Princess".equals(name)) return true;
        if ("Diamond Princess".equals(name)) return true;
        return "MS Zaandam".equals(name);
    }

    private Stream<GlobalTimeSeries> streamGlobal(Path src) {
        return buildTimeSeries(src, GlobalTimeSeries.class)
                .stream()
                .filter(d -> isNotCruiseShip(d.getCountry()));
    }

    @SneakyThrows
    private <T extends CsvTimeSeries> CsvToBean<T> buildTimeSeries(Path src, Class<T> clazz) {
        return builder(src, clazz)
                .withMappingStrategy(new CsvTimeSeriesMappingStrategy<>(clazz))
                .build();
    }

    @SneakyThrows
    private <T> CsvToBean<T> build(Path src, Class<T> clazz) {
        return builder(src, clazz).build();
    }

    @SneakyThrows
    private <T> CsvToBeanBuilder<T> builder(Path src, Class<T> clazz) {
        return new CsvToBeanBuilder<T>(Files.newBufferedReader(src))
                .withType(clazz)
                .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS);
    }

}
