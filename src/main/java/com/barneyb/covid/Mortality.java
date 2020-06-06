package com.barneyb.covid;

import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

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

    @Autowired
    JdbcTemplate jdbcTmpl;

    @Autowired
    Store store;

    public void update() {
        val sb = new StringBuilder()
                .append("select state, deaths total, population");
        STATS.forEach(s -> sb
                .append("\n  , (select deaths from chapter_hierarchy where state_code = t.state_code and chapter_code = '")
                .append(s.code)
                .append("') as ")
                .append(s.name));
        sb.append("\nfrom total t");
        jdbcTmpl.query(sb.toString(), rs -> {
            val m = new HashMap<String, Double>();
            val pop = rs.getInt("population");
            m.put("total", 100_000.0 * rs.getInt("total") / pop);
            for (Stat s : STATS) {
                m.put(s.name, 100_000.0 * rs.getInt(s.name) / pop);
            }
            store.findJurisdiction(rs.getString("state"))
                    .ifPresent(it ->
                            it.setMortalityRates(m));
        });
    }


}
