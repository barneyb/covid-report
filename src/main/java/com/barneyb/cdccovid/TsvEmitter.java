package com.barneyb.cdccovid;

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

        val jurisdictions = new ArrayList<>(store.getJurisdictionList());
        jurisdictions.sort(Comparator.comparing(Jurisdiction::getName));
        val dates = jurisdictions.get(0)
                .getDatesWithData();
        jurisdictions.forEach(j ->
                dates.retainAll(j.getDatesWithData()));

        out.print("Jurisdiction\t");
        for (val d : dates) {
            out.print(d);
            out.print('\t');
            out.print('\t');
        }
        out.println();

        out.print('\t');
        dates.forEach(d -> {
            out.print("Cases\t");
            out.print("Deaths\t");
        });
        out.println();

        jurisdictions.forEach(j -> {
            out.print(j.getName());
            out.print('\t');
            dates.stream().map(j::getData).forEach(p -> {
                out.print(p.getCases());
                out.print('\t');
                out.print(p.getDeaths());
                out.print('\t');
            });
            out.println();
        });
    }

}
