package com.barneyb.covid;

import com.barneyb.covid.model.AggSeries;
import com.barneyb.covid.model.Series;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;

@Service
public class BlockBuilder {

    @Autowired
    BlocksEmitter blocksEmitter;

    @Autowired
    BlockEmitter blockEmitter;

    @SneakyThrows
    public void emit(Path outputDir, AggSeries theWorld) {
        final var blocks = new LinkedList<AggSeries>();
        findBlocks(theWorld, blocks);

        blocksEmitter.emit(Files.newOutputStream(outputDir.resolve("blocks.json")), blocks);
        for (AggSeries b : blocks) {
            blockEmitter.emit(Files.newOutputStream(outputDir.resolve("block_" + b.getArea().getId() + ".json")), b);
        }
    }

    private static void findBlocks(Series s, Collection<AggSeries> blocks) {
        if (!s.isAggregate()) return;
        var aggIsBs = s.getSegments()
                .stream()
                .filter(seg -> seg.getCurrentCases() > 0)
                .limit(2)
                .count() < 2;
        if (aggIsBs) return;
        blocks.add((AggSeries) s);
        s.getSegments().forEach(seg ->
                findBlocks(seg, blocks));
    }

}
