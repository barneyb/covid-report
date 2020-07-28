package com.barneyb.covid;

import com.barneyb.covid.model.AggSeries;
import com.barneyb.covid.model.Series;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class BlockEmitter implements Emitter<AggSeries> {

    @Autowired
    ObjectMapper objectMapper;

    @SneakyThrows
    @Override
    public void emit(OutputStream out, AggSeries model) {
        objectMapper
//                .writerWithDefaultPrettyPrinter() // todo: comment out?
                .writeValue(out, new Block(model));
        out.close();
    }

    @Value
    @RequiredArgsConstructor
    static class Block {
        int id;
        String name;
        long population;
        int totalCases;
        int totalDeaths;
        Collection<Segment> segments;

        public Block(AggSeries s) {
            this(
                    s.getArea().getId(),
                    s.getArea().getName(),
                    s.getArea().getPopulation(),
                    s.getCurrentCases(),
                    s.getCurrentDeaths(),
                    s.getSegments().stream()
                            .map(Segment::new)
                            .collect(Collectors.toList()));
        }

    }

    @Value
    @RequiredArgsConstructor
    static class Segment {
        int id;
        String name;
        long population;
        int totalCases;
        int totalDeaths;
        int[] casesByDay;
        int[] deathsByDay;

        private Segment(Series s) {
            this(
                    s.getArea().getId(),
                    s.getArea().getName(),
                    s.getArea().getPopulation(),
                    s.getCurrentCases(),
                    s.getCurrentDeaths(),
                    s.getCases(),
                    s.getDeaths());
        }

    }

}
