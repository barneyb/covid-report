package com.barneyb.cdccovid;

import com.barneyb.cdccovid.model.DataPoint;
import com.barneyb.cdccovid.model.Jurisdiction;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

@Repository
public class Store implements AutoCloseable {

    public static final Path DEFAULT_STORE_PATH = Path.of("database.json");

    @Autowired
    ObjectMapper mapper;

    private final Path storePath;
    private Map<String, Jurisdiction> jurisdictions;

    public Store() {
        this(DEFAULT_STORE_PATH);
    }

    public Store(Path storePath) {
        this.storePath = storePath;
    }

    public boolean isOpen() {
        return !isClosed();
    }

    @SneakyThrows
    public void open() {
        jurisdictions = new TreeMap<>();
        if (!Files.exists(storePath)) return;
        try (val in = Files.newInputStream(storePath)) {
            for (val j : mapper.readValue(in, Jurisdiction[].class)) {
                jurisdictions.put(j.getName(), j);
            }
        }
    }

    @SneakyThrows
    public void flush() {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try (val out = Files.newOutputStream(storePath)) {
            mapper.writeValue(out, jurisdictions.values());
        }
    }

    public void close() {
        if (isClosed()) return;
        flush();
        jurisdictions = null;
    }

    private boolean isClosed() {
        return jurisdictions == null;
    }

    protected Map<String, Jurisdiction> getJurisdictions() {
        if (isClosed()) open();
        return jurisdictions;
    }

    public Jurisdiction createJurisdiction(String name, Integer population) {
        findJurisdiction(name)
                .ifPresent(j -> {
                    throw new IllegalArgumentException("A '" + name + "' jurisdiction already exists");
                });
        val j = new Jurisdiction();
        j.setName(name);
        j.setPopulation(population);
        getJurisdictions().put(j.getName(), j);
        return j;
    }

    public Optional<Jurisdiction> findJurisdiction(String name) {
        return Optional.ofNullable(getJurisdictions().get(name));
    }

    public Jurisdiction getJurisdiction(String name) {
        return findJurisdiction(name)
                .orElseThrow(() -> new IllegalArgumentException("No '" + name + "' jurisdiction is known"));
    }

    public Collection<Jurisdiction> getAllJurisdictions() {
        return Collections.unmodifiableCollection(getJurisdictions().values());
    }

    public SortedSet<LocalDate> getDatesWithCases() {
        return getDatesWithCases(p -> p.getCases() != null);
    }

    public SortedSet<LocalDate> getDatesWithDeaths() {
        return getDatesWithCases(p -> p.getDeaths() != null);
    }

    private SortedSet<LocalDate> getDatesWithCases(Predicate<DataPoint> test) {
        return getJurisdictions().values()
                .stream()
                .map(j -> j.getDatesWithData(test))
                .reduce((a, b) -> {
                    val r = new TreeSet<>(a);
                    r.retainAll(b);
                    return r;
                })
                .orElseThrow();
    }

}
