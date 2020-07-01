package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;
import com.barneyb.covid.hopkins.csv.GlobalTimeSeries;
import com.barneyb.covid.hopkins.csv.USTimeSeries;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    public List<Demographics> loadUidLookup() {
        return load(uidLookupFile, Demographics.class);
    }

    public List<USTimeSeries> loadUSCases() {
        return load(usCasesFile, USTimeSeries.class);
    }

    public List<USTimeSeries> loadUSDeaths() {
        return load(usDeathsFile, USTimeSeries.class);
    }

    public List<GlobalTimeSeries> loadGlobalCases() {
        return load(globalCasesFile, GlobalTimeSeries.class);
    }

    public List<GlobalTimeSeries> loadGlobalDeaths() {
        return load(globalDeathsFile, GlobalTimeSeries.class);
    }

    @SneakyThrows
    private <T> List<T> load(Path src, Class<T> clazz) {
        return new CsvToBeanBuilder<T>(Files.newBufferedReader(src))
                .withType(clazz)
                .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS)
                .build()
                .parse();

    }

}
