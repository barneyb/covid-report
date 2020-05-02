package com.barneyb.cdccovid;

import com.barneyb.cdccovid.model.Jurisdiction;
import lombok.val;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TsvEmitter implements Emitter {

    @Override
    public void emit(List<Jurisdiction> db, OutputStream outStr) {
        val out = outStr instanceof PrintStream
                ? (PrintStream) outStr
                : new PrintStream(outStr);

        db = new ArrayList<>(db);
        db.sort(Comparator.comparing(Jurisdiction::getName));
        val dates = db.get(0)
                .getDatesWithData();
        db.forEach(j ->
                dates.retainAll(j.getDatesWithData()));

        var sb = new StringBuilder();
        out.print("Jurisdiction\t");
        for (val d : dates) {
            out.print(d);
            out.print('\t');
            out.print('\t');
        }
        out.println(sb);

        sb = new StringBuilder();
        out.print('\t');
        for (val d : dates) {
            out.print("Cases\t");
            out.print("Deaths\t");
        }
        out.println(sb);

        for (val j : db) {
            sb = new StringBuilder();
            out.print(j.getName());
            out.print('\t');
            for (val d : dates) {
                val p = j.getData(d);
                out.print(p.getCases());
                out.print('\t');
                out.print(p.getDeaths());
                out.print('\t');
            }
            out.println(sb);
        }
    }

}
