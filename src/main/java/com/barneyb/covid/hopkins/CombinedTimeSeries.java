package com.barneyb.covid.hopkins;

import com.barneyb.covid.hopkins.csv.Demographics;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.ToString;

import java.util.function.BiFunction;
import java.util.function.Function;

@Data
public class CombinedTimeSeries implements WithDemographics {

    @Setter(AccessLevel.PRIVATE)
    @ToString.Exclude
    private TimeSeries cases;
    @Setter(AccessLevel.PRIVATE)
    @ToString.Exclude
    private TimeSeries deaths;

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

    public CombinedTimeSeries(TimeSeries cases, TimeSeries deaths) {
        assert cases.getDemographics() == deaths.getDemographics();
        assert cases.getPointCount() == deaths.getPointCount();
        this.cases = cases;
        this.deaths = deaths;
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
