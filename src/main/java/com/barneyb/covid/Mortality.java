package com.barneyb.covid;

import com.barneyb.covid.hopkins.csv.MortRates;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class Mortality {

    private static class Stat {
        final String name;
        final String code;

        private Stat(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }

    public static final List<Stat> STATS =
            Collections.unmodifiableList(List.of(
                    new Stat("cancer", "C00"),
                    new Stat("mental", "F01"),
                    new Stat("circ", "I00"),
                    new Stat("resp", "J00"),
                    new Stat("trans", "V01-V99"),
                    new Stat("non_trans", "W00-X59"),
                    new Stat("self", "X60-X84")
            ));

    @AllArgsConstructor
    private static final class Jur {
        String name;
        long pop;
        Map<String, Double> rates;
    }

    @Autowired
    JdbcTemplate jdbcTmpl;

    @Autowired
    Store store;

    public void update() {
        get().forEach(j ->
                store.findJurisdiction(j.name)
                        .ifPresent(it ->
                                it.setMortalityRates(j.rates)));
    }

    @SneakyThrows
    public void emit() {
        try (Writer out = new BufferedWriter(new FileWriter(new File("mortality.txt")))) {
            new StatefulBeanToCsvBuilder<MortRates>(out)
                    .withApplyQuotesToAll(false)
                    .build()
                    .write(get().stream()
                            .map(j -> {
                                val r = new MortRates();
                                r.setState(j.name);
                                r.setPopulation(j.pop);
                                r.getRates().putAll(j.rates);
                                return r;
                            })
                            .collect(Collectors.toList()));

        }
    }

    private List<Jur> get() {
        val sb = new StringBuilder()
                .append("select state, deaths total, population");
        STATS.forEach(s -> sb
                .append("\n  , (select deaths from chapter_hierarchy where state_code = t.state_code and chapter_code = '")
                .append(s.code)
                .append("') as ")
                .append(s.name));
        sb.append("\nfrom total t");
        return jdbcTmpl.query(sb.toString(), (rs, rowNum) -> {
            val m = new HashMap<String, Double>();
            val pop = rs.getLong("population");
            m.put("total", 100_000.0 * rs.getInt("total") / pop);
            for (Stat s : STATS) {
                m.put(s.name, 100_000.0 * rs.getInt(s.name) / pop);
            }
            store.findJurisdiction(rs.getString("state"))
                    .ifPresent(it ->
                            it.setMortalityRates(m));
            return new Jur(
                    rs.getString("state"),
                    pop,
                    m);
        });
    }

}
