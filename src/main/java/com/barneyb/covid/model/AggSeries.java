package com.barneyb.covid.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Collection;
import java.util.HashSet;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = { "area" })
@ToString
public class AggSeries implements Series {

    @Value
    @EqualsAndHashCode(of = { "id" })
    private static class AggArea implements Area {

        int id;

        String name;

        long population;

        @Override
        public boolean isConcrete() {
            return false;
        }
    }

    Area area;

    @NonNull
    @ToString.Exclude
    int[] cases;

    @ToString.Exclude
    int[] deaths;

    @ToString.Exclude
    Collection<Series> segments;

    public AggSeries(int id, String name, Collection<? extends Series> segments) {
        long pop = 0;
        int[] cs = null;
        int[] ds = null;
        for (val s : segments) {
            pop += s.getArea().getPopulation();
            cs = addElementPairs(cs, s.getCases());
            ds = addElementPairs(ds, s.getDeaths());
        }
        this.area = new AggArea(id, name, pop);
        this.cases = cs;
        this.deaths = ds;
        this.segments = new HashSet<>(segments);
    }

    private static int[] addElementPairs(int[] a, int[] b) {
        if (a == null) return b;
        if (b == null) return a;
        assert a.length == b.length;
        val r = new int[a.length];
        for (int i = 0, l = a.length; i < l; i++) {
            r[i] = a[i] + b[i];
        }
        return r;
    }

    @ToString.Include(name = "currentCases")
    int getCurrentCases() {
        return getCasesDaysAgo(0);
    }

    @ToString.Include(name = "currentDeaths")
    int getCurrentDeaths() {
        return getDeathsDaysAgo(0);
    }

    @Override
    @ToString.Include(name = "segmentCount")
    public int getSegmentCount() {
        return segments.size();
    }

}
