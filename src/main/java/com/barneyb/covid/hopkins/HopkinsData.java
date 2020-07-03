package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;
import com.barneyb.covid.hopkins.csv.GlobalTimeSeries;
import com.barneyb.covid.hopkins.csv.USTimeSeries;
import com.barneyb.covid.hopkins.csv.UidLookup;
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
        return loadUids(UidLookup.class);
    }

    /**
     * I exist as compatibility alias for {@link #loadUidLookup()}. Use that?
     */
    public Collection<Demographics> loadDemographics() {
        return loadUids(Demographics.class);
    }

    private <T extends UidLookup> Collection<T> loadUids(Class<T> clazz) {
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
        return stream(uidLookupFile, clazz)
                .map(d -> {
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
                })
                .collect(Collectors.toList());
    }

    public Collection<USTimeSeries> loadUSCases() {
        return loadUS(usCasesFile);
    }

    public Collection<USTimeSeries> loadUSDeaths() {
        return loadUS(usDeathsFile);
    }

    private Collection<USTimeSeries> loadUS(Path src) {
        return stream(src, USTimeSeries.class)
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
                // todo: Exception type 3, Diamond Princess, US: 84088888; Grand Princess, US: 84099999.
                .collect(Collectors.toList());
    }

    public Collection<GlobalTimeSeries> loadGlobalCases() {
        return load(globalCasesFile, GlobalTimeSeries.class);
    }

    public Collection<GlobalTimeSeries> loadGlobalDeaths() {
        return load(globalDeathsFile, GlobalTimeSeries.class);
    }

    private <T> Collection<T> load(Path src, Class<T> clazz) {
        return build(src, clazz).parse();
    }

    private <T> Stream<T> stream(Path src, Class<T> clazz) {
        return build(src, clazz).stream();
    }

    @SneakyThrows
    private <T> CsvToBean<T> build(Path src, Class<T> clazz) {
        return new CsvToBeanBuilder<T>(Files.newBufferedReader(src))
                .withType(clazz)
                .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS)
                .build();
    }

}
