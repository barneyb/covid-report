package com.barneyb.cdccovid;

import com.barneyb.cdccovid.model.CdcJurisdiction;
import com.barneyb.cdccovid.model.DataPoint;
import com.barneyb.cdccovid.model.Jurisdiction;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static final String CDC_DATA_URL = "https://www.cdc.gov/coronavirus/2019-ncov/json/us-cases-map-data.json";
    public static final Path PATH_CDC_DATA = Path.of("cdc-data.json");
    public static final Path PATH_DATABASE = Path.of("database.json");

    public static void main(String[] args) throws Exception {
        val db = loadDatabase();

//        val lookup = new HashMap<String, CdcJurisdiction>();
//        loadCdcData(LocalDate.of(2020, Month.MAY, 2)).forEach(j ->
//                lookup.put(j.getName(), j));
//        db.forEach(j ->
//                j.addDataPoint(lookup.get(j.getName())));
//        saveDatabase(db);

        new TsvEmitter().emit(db, System.out);
    }

    private static List<Jurisdiction> loadDatabase() throws IOException {
        val mapper = new ObjectMapper();
        try (val in = Files.newInputStream(PATH_DATABASE)) {
            return Arrays.asList(mapper.readValue(in, Jurisdiction[].class));
        }
    }

    private static void saveDatabase(List<Jurisdiction> db)throws IOException {
        val mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try (val out = Files.newOutputStream(PATH_DATABASE)) {
            mapper.writeValue(out, db);
        }
    }

    private static List<CdcJurisdiction> loadCdcData(LocalDate asOf) throws IOException {
        refreshCdcData();
        val mapper = new ObjectMapper();
        try (val in = Files.newInputStream(PATH_CDC_DATA)) {
            List<CdcJurisdiction> js = Arrays.asList(mapper.readValue(in, CdcJurisdiction[].class));
            js.forEach(j -> j.setDate(asOf));
            return js;
        }
    }

    private static void refreshCdcData() throws IOException {
        val dataUrl = new URL(CDC_DATA_URL);
        try (val in = (InputStream) dataUrl.getContent()) {
            Files.copy(in, PATH_CDC_DATA, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void initializeFromExisting() throws IOException {
        val april17 = LocalDate.of(2020, Month.APRIL, 18);
        val april24 = LocalDate.of(2020, Month.APRIL, 25);
        val data = Files.lines(Path.of("existing.txt"))
                .map(l -> l.split("\t"))
                .filter(vs -> !"Jurisdiction".equals(vs[0]))
                .map(vs -> new Jurisdiction(
                        vs[0],
                        Integer.parseInt(vs[3]),
                        List.of(
                                new DataPoint(
                                        april17,
                                        Integer.parseInt(vs[2]),
                                        null
                                ),
                                new DataPoint(
                                        april24,
                                        Integer.parseInt(vs[1]),
                                        null
                                )
                        )
                ))
                .collect(Collectors.toList());
        saveDatabase(data);
    }

}
