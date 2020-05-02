package com.barneyb.cdccovid;

import com.barneyb.cdccovid.model.Jurisdiction;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.val;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;

public class Store implements AutoCloseable {

    public static final Path DEFAULT_STORE_PATH = Path.of("database.json");

    @SneakyThrows
    static void initializeFromExisting() {
        Files.deleteIfExists(DEFAULT_STORE_PATH);
        val store = new Store();
        val april17 = LocalDate.of(2020, Month.APRIL, 18);
        val april24 = LocalDate.of(2020, Month.APRIL, 25);
        Files.lines(Path.of("existing.txt"))
                .map(l -> l.split("\t"))
                .filter(vs -> !"Jurisdiction".equals(vs[0]))
                .forEach(vs -> {
                    val j = store.createJurisdiction(vs[0], Integer.parseInt(vs[3]));
                    j.addDataPoint(april17, Integer.parseInt(vs[2]));
                    j.addDataPoint(april24, Integer.parseInt(vs[1]));
                });
        store.close();
    }

    private final Path storePath;
    private List<Jurisdiction> db;

    public Store() {
        this(DEFAULT_STORE_PATH);
    }

    public Store(Path storePath) {
        this.storePath = storePath;
        bootstrap();
    }

    @SneakyThrows
    private void bootstrap() {
        if (!Files.exists(storePath)) {
            db = new ArrayList<>();
            return;
        }
        val mapper = new ObjectMapper();
        try (val in = Files.newInputStream(storePath)) {
            db = Arrays.asList(mapper.readValue(in, Jurisdiction[].class));
        }
    }

    @SneakyThrows
    public void flush() {
        val mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try (val out = Files.newOutputStream(storePath)) {
            mapper.writeValue(out, db);
        }
    }

    public void close() {
        if (db == null) return;
        flush();
        db = null;
    }

    public Jurisdiction createJurisdiction(String name, Integer population) {
        findJurisdiction(name)
                .ifPresent(j -> {
                    throw new IllegalArgumentException("A '" + name + "' jurisdiction already exists");
                });
        val j = new Jurisdiction();
        j.setName(name);
        j.setPopulation(population);
        db.add(j);
        return j;
    }

    public Optional<Jurisdiction> findJurisdiction(String name) {
        // todo: index this!
        for (val j : db) {
            if (name.equals(j.getName())) return Optional.of(j);
        }
        return Optional.empty();
    }

    public Jurisdiction getJurisdiction(String name) {
        return findJurisdiction(name)
                .orElseThrow(() -> new IllegalArgumentException("No '" + name + "' jurisdiction is known"));
    }

    public List<Jurisdiction> getJurisdictionList() {
        return Collections.unmodifiableList(db);
    }
}
