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
import java.util.List;
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

    public List<UidLookup> loadUidLookup() {
        return load(uidLookupFile, UidLookup.class);
    }

    /**
     * I exist as compatibility alias for {@link #loadUidLookup()}. Use that?
     */
    public List<Demographics> loadDemographics() {
        return load(uidLookupFile, Demographics.class);
    }

    public List<USTimeSeries> loadUSCases() {
        return loadUS(usCasesFile);
    }

    public List<USTimeSeries> loadUSDeaths() {
        return loadUS(usDeathsFile);
    }

    private List<USTimeSeries> loadUS(Path src) {
        return stream(src, USTimeSeries.class)
                // todo: Exception type 1, such as recovered and Kansas City, ranging from 8407001 to 8407999.
                // Exception type 2, only the New York City, which is replacing New York County and its FIPS code.
                .filter(d -> {
                    if (!"New York".equals(d.getState())) return true;
                    val l = d.getLocality();
                    if ("Bronx".equals(l)) return false;
                    if ("Kings".equals(l)) return false;
                    if ("Queens".equals(l)) return false;
                    if ("Richmond".equals(l)) return false;
                    return true;
                })
                // todo: Exception type 3, Diamond Princess, US: 84088888; Grand Princess, US: 84099999.
                .collect(Collectors.toList());
    }

    public List<GlobalTimeSeries> loadGlobalCases() {
        return load(globalCasesFile, GlobalTimeSeries.class);
    }

    public List<GlobalTimeSeries> loadGlobalDeaths() {
        return load(globalDeathsFile, GlobalTimeSeries.class);
    }

    private <T> List<T> load(Path src, Class<T> clazz) {
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
