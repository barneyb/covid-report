package com.barneyb.cdccovid;

import com.barneyb.cdccovid.model.CdcJurisdiction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
public class CDC {

    public static final String DEFAULT_JSON_URL = "https://www.cdc.gov/coronavirus/2019-ncov/json/us-cases-map-data.json";

    @Autowired
    Store store;

    private final String jsonUrl;

    public CDC() {
        this(DEFAULT_JSON_URL);
    }

    public CDC(String jsonUrl) {
        this.jsonUrl = jsonUrl;
    }

    public void update(LocalDate asOf) {
        val cachePath = Path.of("cdc-data-" + asOf + ".json");
        hydrate(cachePath, asOf).forEach(j ->
                store.findJurisdiction(j.getName())
                        .ifPresent(it ->
                                it.addDataPoint(
                                        asOf,
                                        j.getCaseCount(),
                                        j.getDeathCount())));
    }

    @SneakyThrows
    private List<CdcJurisdiction> hydrate(Path cachePath, LocalDate asOf) {
        if (!Files.exists(cachePath)) {
            if (!LocalDate.now().equals(asOf)) {
                throw new IllegalStateException("Cowardly refusing to record current data as from " + asOf);
            }
            val conn = (HttpURLConnection) new URL(jsonUrl).openConnection();
            conn.connect();
            try {
                val of = LocalDate.ofInstant(
                        Instant.ofEpochMilli(conn.getLastModified()),
                        ZoneId.systemDefault());
                if (!of.equals(asOf)) {
                    throw new IllegalStateException("Cowardly refusing to record data from " + of + " as from " + asOf);
                }
                try (val in = (InputStream) conn.getContent()) {
                    Files.copy(in, cachePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                conn.disconnect();
            }
        }
        val mapper = new ObjectMapper(); // deliberately using a default one
        try (val in = Files.newInputStream(cachePath)) {
            return Arrays.asList(mapper.readValue(in, CdcJurisdiction[].class));
        }
    }

}
