package com.barneyb.covid;

import com.barneyb.covid.hopkins.HopkinsData;
import com.barneyb.covid.hopkins.Pair;
import com.barneyb.covid.hopkins.csv.CsvTimeSeries;
import com.barneyb.covid.hopkins.csv.UidLookup;
import com.barneyb.covid.model.AggSeries;
import com.barneyb.covid.model.Area;
import com.barneyb.covid.model.RawSeries;
import com.barneyb.covid.model.Series;
import com.barneyb.covid.util.UniqueIndex;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class Loader {

    public static final String COUNTRY_US = "US";

    @Autowired
    HopkinsData hopkinsData;

    private UniqueIndex<String, UidLookup> uidByCountry;
    private UniqueIndex<Pair<String>, UidLookup> uidByCountryState;
    private UniqueIndex<Integer, UidLookup> uidByUid;

    public AggSeries loadWorld() {
        ensureIndexes();
        ensureRaws();
        return new AggSeries(
                Area.ID_WORLDWIDE,
                "Worldwide",
                globalSegments());
    }

    private void ensureIndexes() {
        if (uidByCountry != null) return;
        uidByCountry = new UniqueIndex<>(
                Collections.emptyList(),
                UidLookup::getCountry
        );
        uidByCountryState = new UniqueIndex<>(
                Collections.emptyList(),
                u -> new Pair<>(u.getCountry(), u.getState())
        );
        uidByUid = new UniqueIndex<>(
                Collections.emptyList(),
                UidLookup::getUid
        );
        hopkinsData.loadUidLookup().forEach(it -> {
            uidByUid.add(it);
            if (it.isCountry()) uidByCountry.add(it);
            if (it.isState()) uidByCountryState.add(it);
        });
    }

    private <S extends CsvTimeSeries> Collection<RawSeries> zipAreas(
            Function<S, UidLookup> areaExtractor,
            Stream<S> caseStream,
            Stream<S> deathStream
    ) {
        val caseMap = caseStream
                .collect(Collectors.toMap(
                        areaExtractor,
                        s -> s
                ));
        List<RawSeries> result = deathStream
                .map(it -> {
                    val area = areaExtractor.apply(it);
                    return new RawSeries(
                            area,
                            it.getTodaysDate(),
                            caseMap.get(area),
                            it
                    );
                })
                .collect(Collectors.toList());
        // no duplicate areas!
        assert result.size() == result.stream()
                .map(RawSeries::getArea)
                .collect(Collectors.toSet())
                .size() : "found non-unique areas?!";
        return result;
    }

    private Collection<RawSeries> rawGlobal;
    private Collection<RawSeries> rawUs;

    private void ensureRaws() {
        if (rawGlobal != null) return;
        rawGlobal = zipAreas(
                it -> it.isCountry()
                        ? uidByCountry.get(it.getCountry())
                        : uidByCountryState.get(it.getCountry(), it.getState()),
                hopkinsData.streamGlobalCases(),
                hopkinsData.streamGlobalDeaths());
        rawUs = zipAreas(
                it -> uidByUid.get(it.getUid()),
                hopkinsData.streamUSCases(),
                hopkinsData.streamUSDeaths());

        // move all the US Insular Areas to the global
        var rawUsByIso2 = rawUs.stream()
                .collect(Collectors.groupingBy(s -> s.getArea().getIso2()));
        rawUs = rawUsByIso2.remove(COUNTRY_US);
        rawUsByIso2.values()
                .forEach(rawGlobal::addAll);
    }

    private Collection<Series> globalSegments() {
        // index countries by ISO code. Single-valued codes are a country, multi-valued codes are states thereof
        final var countries = rawGlobal.stream()
                .filter(s ->
                        // pull the US out
                        s.getArea().getId() != Area.ID_US)
                .collect(Collectors.groupingBy(s ->
                        s.getArea().getIso2()))
                .values()
                .stream()
                .map(cs -> {
                    val one = cs.iterator().next();
                    val a = one.getArea();
                    if (cs.size() > 1) {
                        if (COUNTRY_US.equals(a.getCountry())) {
                            // breakdown of a US Insular Area
                            return new AggSeries(uidByCountryState.get(COUNTRY_US, a.getState()), cs);
                        } else {
                            // per-state breakdown of a country
                            return new AggSeries(uidByCountry.get(a.getCountry()), cs);
                        }
                    }
                    if (a.isState()) {
                        // a single-state country is a protectorate-ish thing,
                        // so want to change the way it's labeled
                        return new AggSeries(
                                a.getUid(),
                                a.getState() + " (" + a.getCountry() + ")",
                                cs);
                    }
                    // a simple country
                    return one;
                })
                .collect(Collectors.toList());
        // put the  US back in, aggregated
        countries.add(new AggSeries(Area.ID_US, COUNTRY_US, usSegments()));
        return countries;
    }

    private Collection<Series> usSegments() {
        // create US state aggregates (along with the cruise ships)
        return rawUs.stream()
                .collect(Collectors.groupingBy(s ->
                        s.getArea().getState()))
                .values()
                .stream()
                .map(cs -> {
                    val one = cs.iterator().next();
                    if (cs.size() == 1) {
                        // a cruise ship
                        val a = one.getArea();
                        return new AggSeries(a.getUid(), a.getState() + " (" + a.getCountry() + ")", cs);
                    } else {
                        val a = uidByCountryState.get(COUNTRY_US, one.getArea().getState());
                        return new AggSeries(a.getUid(), a.getState(), cs);
                    }
                })
                .collect(Collectors.toList());
    }

}
