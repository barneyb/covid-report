package com.barneyb.covid;

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
public class BlocksEmitter implements Emitter<Collection<? extends Series>> {

    @Autowired
    ObjectMapper objectMapper;

    @SneakyThrows
    @Override
    public void emit(OutputStream out, Collection<? extends Series> model) {
        objectMapper
//                .writerWithDefaultPrettyPrinter() // todo: comment out?
                .writeValue(out, model.stream()
                        .map(Block::new)
                        .collect(Collectors.toList()));
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
        int segmentCount;

        public Block(Series s) {
            this(
                    s.getArea().getId(),
                    s.getArea().getName(),
                    s.getArea().getPopulation(),
                    s.getCurrentCases(),
                    s.getCurrentDeaths(),
                    s.getSegmentCount());
        }

    }

}