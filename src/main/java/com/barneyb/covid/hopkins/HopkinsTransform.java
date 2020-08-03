package com.barneyb.covid.hopkins;

import com.barneyb.covid.Store;
import com.barneyb.covid.hopkins.csv.Demographics;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

@Component
public class HopkinsTransform {

    @Value("${covid-report.output.dir}")
    Path outputDir;

    @Autowired
    HopkinsData hopkinsData;

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
    DashboardBuilder dashboardBuilder;

    @SneakyThrows
    public void transform(Consumer<String> logStep) {
        logStep.accept("Starting HopkinsTransform");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        } else if (!Files.isDirectory(outputDir)) {
            throw new RuntimeException("Non-directory '" + outputDir + "' found.");
        }
        val demographics = new IndexedDemographics(hopkinsData.loadDemographics());
        logStep.accept("Demographics loaded and indexed");

        val rawGlobal = hopkinsData.loadGlobalCases();
        val idxGlobal = new IndexedWorld(demographics, rawGlobal, hopkinsData.loadGlobalDeaths());
        demographics.createWorldwide(idxGlobal
                .countries()
                .map(CombinedTimeSeries::getDemographics)
                .map(Demographics::getPopulation)
                .reduce(0L, Long::sum));
        idxGlobal.createWorldwide(demographics.getWorldwide());
        logStep.accept("Global series loaded and indexed");

        val idxUs = new IndexedUS(demographics, hopkinsData.loadUSCases(), hopkinsData.loadUSDeaths());
        demographics.createUsExceptNy(idxUs
                .statesAndDC()
                .map(CombinedTimeSeries::getDemographics)
                .filter(d -> !"New York".equals(d.getState()))
                .map(Demographics::getPopulation)
                .reduce(0L, Long::sum));
        idxUs.createUsExceptNy(demographics.getUsExceptNy());
        logStep.accept("US series loaded and indexed");

        dashboardBuilder.emit(
                idxGlobal,
                idxUs,
                outputDir.resolve("dashboard.json"));
        logStep.accept("Dashboard rebuilt");
    }

}
