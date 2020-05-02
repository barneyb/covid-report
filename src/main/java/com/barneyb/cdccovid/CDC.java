package com.barneyb.cdccovid;

import com.barneyb.cdccovid.model.CdcJurisdiction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.val;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class CDC {

    public static final String DEFAULT_JSON_URL = "https://www.cdc.gov/coronavirus/2019-ncov/json/us-cases-map-data.json";
    public static final Path DEFAULT_CACHE_PATH = Path.of("cdc-data.json");

    private final String jsonUrl;
    private final Path cachePath;

    public CDC() {
        this(DEFAULT_JSON_URL, DEFAULT_CACHE_PATH);
    }

    public CDC(String jsonUrl, Path cachePath) {
        this.jsonUrl = jsonUrl;
        this.cachePath = cachePath;
    }

    public void update(Store store, LocalDate asOf) {
        loadCdcData(asOf).forEach(j ->
                store.findJurisdiction(j.getName())
                        .ifPresent(it ->
                                it.addDataPoint(
                                        asOf,
                                        j.getCases(),
                                        j.getDeaths())));
    }

    @SneakyThrows
    private List<CdcJurisdiction> loadCdcData(LocalDate asOf) {
        refreshCdcData();
        val mapper = new ObjectMapper();
        try (val in = Files.newInputStream(cachePath)) {
            List<CdcJurisdiction> js = Arrays.asList(mapper.readValue(in, CdcJurisdiction[].class));
            js.forEach(j -> j.setDate(asOf));
            return js;
        }
    }

    @SneakyThrows
    private void refreshCdcData() {
        val dataUrl = new URL(jsonUrl);
        try (val in = (InputStream) dataUrl.getContent()) {
            Files.copy(in, cachePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
