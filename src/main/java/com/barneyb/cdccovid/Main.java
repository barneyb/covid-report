package com.barneyb.cdccovid;

import com.barneyb.cdccovid.model.CdcJurisdiction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static final String CDC_DATA_URL = "https://www.cdc.gov/coronavirus/2019-ncov/json/us-cases-map-data.json";
    public static final Path PATH_JURISDICTIONS = Path.of("jurisdictions.txt");
    public static final Path PATH_CDC_DATA = Path.of("cdc-data.json");

    public static void main(String[] args) throws Exception {
        refreshData();
        val lookup = new HashMap<String, CdcJurisdiction>();
        loadData().forEach(j ->
                lookup.put(j.getJurisdiction(), j));
        loadJurisdictions().forEach(n -> {
            val j = lookup.get(n);
            System.out.printf("%s\t%d\t%d\n", n, j.getCases(), j.getDeaths());
        });
    }

    private static List<CdcJurisdiction> loadData() throws IOException {
        val mapper = new ObjectMapper();
        return Arrays.asList(mapper.readValue(
                Files.newInputStream(PATH_CDC_DATA),
                CdcJurisdiction[].class));
    }

    private static Collection<String> loadJurisdictions() throws IOException {
        return Files.readAllLines(PATH_JURISDICTIONS);
    }

    private static void refreshData() throws IOException {
        val dataUrl = new URL(CDC_DATA_URL);
        val dataStream = (InputStream) dataUrl.getContent();
        val dataReader = new BufferedReader(new InputStreamReader(dataStream));
        Files.writeString(PATH_CDC_DATA, dataReader.lines().collect(Collectors.joining()));
    }

}
