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
    private Map<String, Jurisdiction> jurisdictions;

    public Store() {
        this(DEFAULT_STORE_PATH);
    }

    public Store(Path storePath) {
        this.storePath = storePath;
        bootstrap();
    }

    @SneakyThrows
    private void bootstrap() {
        jurisdictions = new HashMap<>();
        if (!Files.exists(storePath)) return;
        val mapper = new ObjectMapper();
        try (val in = Files.newInputStream(storePath)) {
            for (val j : mapper.readValue(in, Jurisdiction[].class)) {
                jurisdictions.put(j.getName(), j);
            }
        }
    }

    @SneakyThrows
    public void flush() {
        val mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        val list = new ArrayList<Jurisdiction>(jurisdictions.size());
        new TreeSet<>(jurisdictions.keySet()).forEach(n ->
                list.add(jurisdictions.get(n)));
        try (val out = Files.newOutputStream(storePath)) {
            mapper.writeValue(out, list);
        }
    }

    public void close() {
        if (jurisdictions == null) return;
        flush();
        jurisdictions = null;
    }

    public Jurisdiction createJurisdiction(String name, Integer population) {
        findJurisdiction(name)
                .ifPresent(j -> {
                    throw new IllegalArgumentException("A '" + name + "' jurisdiction already exists");
                });
        val j = new Jurisdiction();
        j.setName(name);
        j.setPopulation(population);
        jurisdictions.put(j.getName(), j);
        return j;
    }

    public Optional<Jurisdiction> findJurisdiction(String name) {
        return Optional.ofNullable(jurisdictions.get(name));
    }

    public Jurisdiction getJurisdiction(String name) {
        return findJurisdiction(name)
                .orElseThrow(() -> new IllegalArgumentException("No '" + name + "' jurisdiction is known"));
    }

    public Collection<Jurisdiction> getJurisdictionList() {
        return Collections.unmodifiableCollection(jurisdictions.values());
    }
}
