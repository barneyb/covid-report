package com.barneyb.covid.model;

import com.barneyb.covid.util.UniqueIndex;
import lombok.*;

import java.time.LocalDate;
import java.util.*;

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
    private final LocalDate todaysDate;

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
        final var itr = segments.iterator();
        var first = itr.next();
        this.todaysDate = first.getTodaysDate();
        long pop = first.getArea().getPopulation();
        final int[] cs = Arrays.copyOf(first.getCases(), first.getPointCount());
        final int[] ds = Arrays.copyOf(first.getDeaths(), first.getPointCount());
        while (itr.hasNext()) {
            Series s = itr.next();
            pop += s.getArea().getPopulation();
            pairwisePlus(cs, s.getCases());
            pairwisePlus(ds, s.getDeaths());
        }
        this.area = new AggArea(id, name, pop);
        this.cases = cs;
        this.deaths = ds;
        this.segments = new HashSet<>(segments);
    }

    private static void pairwisePlus(int[] a, int[] b) {
        if (a == null) throw new IllegalArgumentException("Can't add pairs of a null array");
        if (b == null) return;
        assert a.length == b.length;
        for (int i = 0, l = a.length; i < l; i++) {
            a[i] += b[i];
        }
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
