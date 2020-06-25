package com.barneyb.covid;

import com.barneyb.covid.hopkins.HopkinsTransform;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class Main implements ApplicationRunner {

    @Value("${covid-report.output.dir}")
    Path outputDir;

    @Autowired
    ApplicationContext appCtx;

    @Autowired
    @Qualifier("worldwide")
    Store wwStore;

    @Autowired
    @Qualifier("us")
    Store usStore;

    @Autowired
    @Qualifier("or")
    Store orStore;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.containsOption("mortality")) {
            appCtx.getBean(Mortality.class)
                    .emit(Files.newBufferedWriter(Path.of("mortality.csv")));
        }

        if (args.containsOption("hopkins")) {
            appCtx.getBean(HopkinsTransform.class)
                    .transform();
        }

        val json = appCtx.getBean(ReportJsonEmitter.class);
        val tsv = appCtx.getBean(TsvEmitter.class);

        json.emit(Files.newOutputStream(outputDir.resolve("table-ww.json")), wwStore);
        tsv.emit(Files.newOutputStream(outputDir.resolve("table-ww.tsv")), wwStore);

        json.emit(Files.newOutputStream(outputDir.resolve("table-us.json")), usStore);
        tsv.emit(Files.newOutputStream(outputDir.resolve("table-us.tsv")), usStore);

        json.emit(Files.newOutputStream(outputDir.resolve("table-or.json")), orStore);
        tsv.emit(Files.newOutputStream(outputDir.resolve("table-or.tsv")), orStore);
    }

}
