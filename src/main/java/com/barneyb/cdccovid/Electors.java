package com.barneyb.cdccovid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

@Service
@Transactional
public class Electors {

    @Autowired
    JdbcTemplate jdbcTmpl;

    @Autowired
    Store store;

    public void update() {
        jdbcTmpl.query("select state, electors from electors", rs -> {
            store.findJurisdiction(rs.getString(1))
                    .ifPresent(it -> {
                        try {
                            it.setElectors(rs.getInt(2));
                        } catch (SQLException sqle) {
                            throw new RuntimeException(sqle);
                        }
                    });
        });
    }


}
