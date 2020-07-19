package com.barneyb.covid.model;

import com.barneyb.covid.util.UniqueIndex;
import lombok.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

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

    @Getter
    private final Area area;

    @Getter
    @ToString.Exclude
    private final int[] cases;

    @Getter
    @ToString.Exclude
    private final int[] deaths;

    @Getter
    @ToString.Exclude
    private final Collection<Series> segments;

    public AggSeries(Area area, Collection<? extends Series> segments) {
        this(area.getId(), area.getName(), segments);
    }

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
    public int getCurrentCases() {
        return getCasesDaysAgo(0);
    }

    @ToString.Include(name = "currentDeaths")
    public int getCurrentDeaths() {
        return getDeathsDaysAgo(0);
    }

    @Override
    @ToString.Include(name = "segmentCount")
    public int getSegmentCount() {
        return segments.size();
    }

    public AggSeries plus(Series s) {
        return plus(List.of(s));
    }

    public AggSeries plus(Collection<Series> ss) {
        val segs = new ArrayList<Series>(segments.size() + ss.size());
        segs.addAll(segments);
        segs.addAll(ss);
        return new AggSeries(getArea(), segs);
    }

    @ToString.Exclude
    private UniqueIndex<String, Series> segmentIndex;

    public Series getSegment(String name) {
        if (segmentIndex == null) {
            segmentIndex = new UniqueIndex<>(
                    segments,
                    s -> s.getArea().getName());
        }
        return segmentIndex.get(name);
    }

}
