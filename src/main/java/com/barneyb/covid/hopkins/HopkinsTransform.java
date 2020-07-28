package com.barneyb.covid.hopkins;

import com.barneyb.covid.Store;
import com.barneyb.covid.hopkins.csv.Demographics;
import com.barneyb.covid.hopkins.csv.GlobalTimeSeries;
import com.barneyb.covid.hopkins.csv.MortRates;
import com.barneyb.covid.util.UniqueIndex;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileReader;
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
        GlobalTimeSeries firstSeries = rawGlobal.iterator().next();
        val dates = firstSeries.getDateSequence();
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

        val mortRates = new UniqueIndex<>(
                new CsvToBeanBuilder<MortRates>(new FileReader("mortality.csv"))
                        .withType(MortRates.class)
                        .build()
                        .parse(),
                MortRates::getState);
        logStep.accept("US series loaded and indexed");

        dashboardBuilder.emit(
                idxGlobal,
                idxUs,
                outputDir.resolve("dashboard.json"));
        logStep.accept("Dashboard rebuilt");

        new StoreBuilder<>(dates,
                idxGlobal,
                Demographics::getCountry,
                (iw, d) -> iw.getByCountry(d.getCountry())
        ).updateStore(wwStore, demographics.countries());
        logStep.accept("Worldwide database rebuilt");

        new StoreBuilder<>(dates,
                idxUs,
                Demographics::getState,
                (iu, d) -> iu.getByState(d.getState())
        ).updateStore(usStore, demographics.usStatesAndDC(), j ->
                j.setMortalityRates(mortRates.get(j.getName()).unwrapRates()));
        logStep.accept("US database rebuilt");

        new StoreBuilder<>(dates,
                idxUs,
                Demographics::getLocality,
                (iu, d) -> iu.getByStateAndLocality("Oregon", d.getLocality())
        ).updateStore(orStore, idxUs.getLocalitiesOfState("Oregon")
                .filter(s -> !s.getDemographics().getLocality().endsWith("Metro"))
                .map(CombinedTimeSeries::getDemographics));
        logStep.accept("OR database rebuilt");

        new RatesBuilder(dates, CombinedTimeSeries::getCasesSeries, idxGlobal, idxUs)
                .emit(outputDir.resolve("rates-cases.txt"));
        logStep.accept("Case rates rebuilt");

        new RatesBuilder(dates, CombinedTimeSeries::getDeathsSeries, idxGlobal, idxUs)
                .emit(outputDir.resolve("rates-deaths.txt"));
        logStep.accept("Death rates rebuilt");
    }

}
