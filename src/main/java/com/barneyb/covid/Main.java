package com.barneyb.covid;

import com.barneyb.covid.hopkins.HopkinsTransform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Component
public class Main implements ApplicationRunner {

    @Value("${covid-report.output.dir}")
    Path outputDir;

    @Autowired
    @Qualifier("worldwide")
    Store wwStore;

    @Autowired
    @Qualifier("us")
    Store usStore;

    @Autowired
    @Qualifier("or")
    Store orStore;

    @Autowired
    Mortality mortality;

    @Autowired
    HopkinsTransform hopkinsTransform;

    @Autowired
    ReportJsonEmitter tableJson;

    @Autowired
    TsvEmitter tableTsv;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.containsOption("clean")) {
            // This feels like the wrong way to do it. It does work.
            for (var f : Objects.requireNonNull(outputDir.toFile().listFiles())) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }

        if (args.containsOption("mortality")) {
            mortality.emit(Files.newBufferedWriter(Path.of("mortality.csv")));
        }

        if (args.containsOption("hopkins")) {
            hopkinsTransform.transform();
        }

        tableJson.emit(Files.newOutputStream(outputDir.resolve("table-ww.json")), wwStore);
        tableTsv.emit(Files.newOutputStream(outputDir.resolve("table-ww.tsv")), wwStore);

        tableJson.emit(Files.newOutputStream(outputDir.resolve("table-us.json")), usStore);
        tableTsv.emit(Files.newOutputStream(outputDir.resolve("table-us.tsv")), usStore);

        tableJson.emit(Files.newOutputStream(outputDir.resolve("table-or.json")), orStore);
        tableTsv.emit(Files.newOutputStream(outputDir.resolve("table-or.tsv")), orStore);
    }

}
