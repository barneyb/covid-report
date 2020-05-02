package com.barneyb.cdccovid.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataPoint {

    @JsonIgnore
    private LocalDate date;
    private Integer cases;
    private Integer deaths;

    public Integer getDt() {
        if (date == null) return null;
        return date.getYear() * 10000
                + date.getMonthValue() * 100
                + date.getDayOfMonth();
    }

    public void setDt(Integer dt) {
        date = dt == null ? null : LocalDate.of(
                dt / 10000,
                dt / 100 % 100,
                dt % 100
        );
    }
}
