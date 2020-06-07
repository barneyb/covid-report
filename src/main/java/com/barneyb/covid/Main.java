package com.barneyb.covid;

import com.barneyb.covid.hopkins.HopkinsTransform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class Main implements ApplicationRunner {

    @Autowired
    ApplicationContext appCtx;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.containsOption("hopkins")) {
            appCtx.getBean(HopkinsTransform.class)
                    .transform();
        }

        if (args.containsOption("mortality")) {
            appCtx.getBean(Mortality.class)
                    .emit(Files.newBufferedWriter(Path.of("mortality.csv")));
        }

        appCtx.getBean(ReportJsonEmitter.class)
                .emit(Files.newOutputStream(Path.of("report.json")));
    }

}
