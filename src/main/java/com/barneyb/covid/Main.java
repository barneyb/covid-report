package com.barneyb.covid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Component
public class Main implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Value("${covid-report.output.dir}")
    Path outputDir;

    @Autowired
    Loader loader;

    @Autowired
    BlockBuilder blockBuilder;

    @Autowired
    IndexBuilder indexBuilder;

    private long _prev;
    private void logStep(String message) {
        long now = System.currentTimeMillis();
        if (_prev == 0) {
            logger.info(message);
        } else {
            logger.info(message + " (" + (now - _prev) + " ms)");
        }
        _prev = now;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void prepareTarget() {
        var out = outputDir.toFile();
        if (!out.exists()) {
            out.mkdirs();
        } else {
            // This feels like the wrong way to do it. It does work though.
            for (var f : Objects.requireNonNull(out.listFiles())) {
                f.delete();
            }
            logStep("clean complete");
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logStep("enter main");
        prepareTarget();

        var theWorld = loader.loadWorld();
        logStep("World loaded");
        try (Writer w = Files.newBufferedWriter(outputDir.resolve("last-update.txt"))) {
            // add a day for the UTC/LocalDate dance
            w.write(theWorld.getTodaysDate().plusDays(1).toString());
        }

        blockBuilder.emit(outputDir, theWorld);
        logStep("Blocks emitted");

        indexBuilder.emit(outputDir.resolve("index.json"), theWorld);
        logStep("Index emitted");
    }

}
