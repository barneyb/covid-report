package com.barneyb.cdccovid;

import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;

@Component
public class Main implements CommandLineRunner {

    @Autowired
    ApplicationContext appCtx;

    public void run(String... args) throws Exception {
//        appCtx.getBean(Mortality.class)
//                .update();

//        appCtx.getBean(Electors.class)
//                .update();

//        appCtx.getBean(CDC.class)
//                .update(java.time.LocalDate.of(2020, 5, 9));

//        appCtx.getBean(TsvEmitter.class)
//                .emit(System.out);

        appCtx.getBean(ReportJsonEmitter.class)
//                .emit(System.out);
                .emit(Files.newOutputStream(Path.of("report.json")));
    }

    @SneakyThrows
    private void initializeFromExisting() {
        val store = appCtx.getBean(Store.class);
        if (store.isOpen()) store.close();
        Files.deleteIfExists(Store.DEFAULT_STORE_PATH);
        val april17 = LocalDate.of(2020, Month.APRIL, 18);
        val april24 = LocalDate.of(2020, Month.APRIL, 25);
        Files.lines(Path.of("existing.txt"))
                .map(l -> l.split("\t"))
                .filter(vs -> !"Jurisdiction" .equals(vs[0]))
                .forEach(vs -> {
                    val j = store.createJurisdiction(vs[0], Integer.parseInt(vs[3]));
                    j.addDataPoint(april17, Integer.parseInt(vs[2]));
                    j.addDataPoint(april24, Integer.parseInt(vs[1]));
                });
    }
}
