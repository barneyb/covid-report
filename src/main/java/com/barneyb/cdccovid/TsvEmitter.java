package com.barneyb.cdccovid;

import com.barneyb.cdccovid.model.DataPoint;
import com.barneyb.cdccovid.model.Jurisdiction;
import lombok.val;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;

public class TsvEmitter implements Emitter {

    @Override
    public void emit(Store store, OutputStream outStr) {
        val out = outStr instanceof PrintStream
                ? (PrintStream) outStr
                : new PrintStream(outStr);

        val jurisdictions = new ArrayList<>(store.getAllJurisdictions());
        jurisdictions.sort(Comparator.comparing(Jurisdiction::getName));
        val dates = store.getDatesWithCases();
        val datesWithDeaths = store.getDatesWithDeaths();

        out.print("Jurisdiction\t");
        out.print("Population\t");
        for (val d : dates) {
            out.print(d);
            out.print('\t');
            if (datesWithDeaths.contains(d)) out.print('\t');
        }
        out.println();

        out.print('\t');
        out.print('\t');
        dates.forEach(d -> {
            out.print("Cases\t");
            if (datesWithDeaths.contains(d)) out.print("Deaths\t");
        });
        out.println();

        jurisdictions.forEach(j -> {
            out.print(j.getName());
            out.print('\t');
            out.print(j.getPopulation());
            out.print('\t');
            dates.forEach(d -> {
                DataPoint p = j.getData(d);
                out.print(p.getCases());
                out.print('\t');
                if (datesWithDeaths.contains(d)) {
                    out.print(p.getDeaths());
                    out.print('\t');
                }
            });
            out.println();
        });
    }

}
