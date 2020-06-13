package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;
import lombok.ToString;

import java.util.function.BiFunction;
import java.util.function.Function;

@ToString
public class CombinedTimeSeries implements WithDemographics<CombinedTimeSeries> {

    @ToString.Exclude
    final private TimeSeries cases;
    @ToString.Exclude
    final private TimeSeries deaths;

    @Override
    public Demographics getDemographics() {
        return cases.getDemographics();
    }

    @ToString.Include
    public int getPointCount() {
        return this.cases.getPointCount();
    }

    @ToString.Include
    public double getTotalCases() {
        return this.cases.getCurrent();
    }

    public double getTotalDeaths() {
        return this.deaths.getCurrent();
    }

    public TimeSeries getCasesSeries() {
        return this.cases;
    }

    public TimeSeries getDeathsSeries() {
        return this.deaths;
    }

    public CombinedTimeSeries(TimeSeries cases, TimeSeries deaths) {
        assert cases.getDemographics() == deaths.getDemographics();
        assert cases.getPointCount() == deaths.getPointCount();
        this.cases = cases;
        this.deaths = deaths;
    }

    public CombinedTimeSeries withDemographics(Demographics demo) {
        return new CombinedTimeSeries(
                cases.withDemographics(demo),
                deaths.withDemographics(demo)
        );
    }

    public CombinedTimeSeries plus(CombinedTimeSeries other) {
        return new CombinedTimeSeries(
                cases.plus(other.cases),
                deaths.plus(other.deaths)
        );
    }

    public CombinedTimeSeries minus(CombinedTimeSeries other) {
        return new CombinedTimeSeries(
                cases.minus(other.cases),
                deaths.minus(other.deaths)
        );
    }

    public CombinedTimeSeries transform(BiFunction<Demographics, Double, Double> transform) {
        return new CombinedTimeSeries(
                cases.transform(transform),
                deaths.transform(transform)
        );
    }

    public CombinedTimeSeries map(Function<double[], double[]> map) {
        return new CombinedTimeSeries(
                cases.map(map),
                deaths.map(map)
        );
    }

}
