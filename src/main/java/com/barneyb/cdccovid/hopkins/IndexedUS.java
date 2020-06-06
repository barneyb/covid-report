package com.barneyb.cdccovid.hopkins;

import com.barneyb.cdccovid.hopkins.csv.USTimeSeries;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexedUS {

    private final Collection<TimeSeries> cover;
    private final Index<String, TimeSeries> localsByState;
    private final UniqueIndex<Pair<String>, TimeSeries> byStateAndLocal;
    private final UniqueIndex<String, TimeSeries> byState;

    public IndexedUS(IndexedDemographics demographics, Collection<USTimeSeries> rawUS, String[] dateHeaders) {
        cover = rawUS.stream()
                .map(it -> new TimeSeries(demographics.getByUid(it.getUid()),
                        dateHeaders,
                        it))
                .collect(Collectors.toUnmodifiableList());
        localsByState = new Index<>(cover, it -> it.getDemographics().getState());
        byStateAndLocal = new UniqueIndex<>(cover.stream()
                .filter(it -> it.getDemographics().isLocal()),
                it -> new Pair<>(it.getDemographics().getState(), it.getDemographics().getLocal()));
        byState = new UniqueIndex<>(localsByState.getKeys()
                .stream()
                .map(state -> localsByState.get(state)
                        .stream()
                        .reduce(TimeSeries::plus)
                        .map(ts -> {
                            ts.setDemographics(
                                    demographics.getByCountryAndState("US", state));
                            return ts;
                        })
                        .orElseThrow()
                ),
                it -> it.getDemographics().getState());
    }

    public Stream<TimeSeries> cover() {
        return cover.stream();
    }

    public Stream<TimeSeries> allStates() {
        return byState.getKeys().stream()
                .map(byState::get);
    }

    public TimeSeries getByState(String state) {
        return byState.get(state);
    }

    public TimeSeries getByStateAndLocal(String state, String local) {
        return byStateAndLocal.get(new Pair<>(state, local));
    }

    public Stream<TimeSeries> getCountiesOfState(String state) {
        return localsByState.get(state).stream();
    }
}
