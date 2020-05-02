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

    private final LocalDate asOf;
    private final String jsonUrl;
    private final Path cachePath;

    public CDC(LocalDate asOf) {
        this(asOf, DEFAULT_JSON_URL, Path.of("cdc-data-" + asOf + ".json"));
    }

    public CDC(LocalDate asOf, String jsonUrl, Path cachePath) {
        this.asOf = asOf;
        this.jsonUrl = jsonUrl;
        this.cachePath = cachePath;
    }

    public void update(Store store) {
        hydrate().forEach(j ->
                store.findJurisdiction(j.getName())
                        .ifPresent(it ->
                                it.addDataPoint(
                                        asOf,
                                        j.getCases(),
                                        j.getDeaths())));
    }

    @SneakyThrows
    private List<CdcJurisdiction> hydrate() {
        if (!Files.exists(cachePath)) download();
        val mapper = new ObjectMapper();
        try (val in = Files.newInputStream(cachePath)) {
            return Arrays.asList(mapper.readValue(in, CdcJurisdiction[].class));
        }
    }

    @SneakyThrows
    private void download() {
        if (!LocalDate.now().equals(asOf)) {
            throw new IllegalStateException("Cowardly refusing to record current data as from " + asOf);
        }
        val dataUrl = new URL(jsonUrl);
        try (val in = (InputStream) dataUrl.getContent()) {
            Files.copy(in, cachePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
