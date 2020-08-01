weeklySeries = [{
    key: "cases",
    label: "Cases",
    desc: "Cases reported this week.",
    calc: p => p.weekly_cases,
}, {
    key: "case_rate",
    label: "Case Rate",
    desc: "Average cases reported per day per 100,000 population this week.",
    calc: (p, j) => p.weekly_cases
        ? p.weekly_cases / Week / j.population * HunThou
        : null,
    format: v => formatNumber(v, 1),
}, {
    key: "case_wow",
    label: Delta + "Case Rate",
    desc: "Change in case rate from last week",
    calc: (p, j, pidx) => {
        if (pidx === 0) return null;
        const curr = p.weekly_cases / j.population * HunThou;
        const prev = j.points[pidx-1].weekly_cases / j.population * HunThou;
        return (curr - prev) / prev;
    },
    format: formatPercent,
}, {
    key: "deaths",
    label: "Deaths",
    desc: "Deaths reported this week.",
    calc: p => p.weekly_deaths,
}, {
    key: "death_rate",
    label: "Death Rate",
    desc: "Average deaths reported per day per 100,000 population this week.",
    calc: (p, j) => p.weekly_deaths
        ? p.weekly_deaths / Week / j.population * HunThou
        : null,
    format: formatDeathRateSegment,
}, {
    key: "case_mortality",
    label: "Case Mortality",
    desc: "Deaths this week per case this week.",
    calc: p => p.weekly_deaths && p.weekly_cases
        ? p.weekly_deaths / p.weekly_cases
        : null,
    format: formatPercent,
}];
jurisdictionSeries = [{
    key: "name",
    label: "Jurisdiction",
    calc: j => j.name,
    format: IDENTITY,
    is_number: false,
}, {
    key: "population",
    label: "Population",
    desc: "An estimate of total population.",
    calc: j => j.population,
}, {
    key: "total_cases",
    label: "C19 Cases",
    desc: "Total COVID-19 cases reported.",
    calc: j => j.total_cases,
}, {
    key: "total_case_rate",
    label: "C19 Cases/100K",
    desc: "Total COVID-19 cases per 100,000 population.",
    calc: j => j.total_cases
        ? j.total_cases / j.population * HunThou
        : null,
}, {
    key: "total_deaths",
    label: "C19 Deaths",
    desc: "Total COVID-19 deaths reported.",
    calc: j => j.total_deaths,
}, {
    key: "total_death_rate",
    label: "C19 Deaths/100K",
    desc: "Total COVID-19 deaths per 100,000 population.",
    calc: j => j.total_deaths
        ? j.total_deaths / j.population * HunThou
        : null,
    format: formatDeathRate,
}, {
    key: "mortality",
    label: "C19 Mortality",
    desc: "Total COVID-19 deaths per total COVID-19 cases.",
    calc: j => j.total_deaths && j.total_cases
        ? j.total_deaths / j.total_cases
        : null,
    format: formatPercent,
}];
weeklySeries.concat(jurisdictionSeries).forEach(s => {
    if (!s.hasOwnProperty("format")) s.format = formatNumber;
    if (!s.hasOwnProperty("is_number")) s.is_number = true;
})

state = {
    loading: 0,
};

function setState(s) {
    if (typeof s === "function") {
        s = s(state);
    }
    state = {
        ...state,
        ...s,
    };
    render(state);
}

function render(state) {
    $("#picker").innerHTML = state.blocks
        ? el('select', {
            onchange: "selectBlock(this)",
        }, [
            el('option'),
        ].concat(state.blocks.map(b => {
            const attrs = {value: b.id}
            if (b.id === state.activeBlock) attrs.selected = "selected";
            return el('option', attrs, b.name)
        })))
        : '';

    if (state.block) {
        // this should all be in state, filtered based on the checkboxes
        const cols = [];
        state.dates.forEach(d => {
            const group = formatDate(d);
            weeklySeries.forEach(spec =>
                cols.push({
                    ...spec,
                    group,
                }));
        });
        jurisdictionSeries.forEach(spec => {
            if (spec.key === "name") cols.unshift(spec);
            else cols.push(spec);
        });
        const groups = cols.reduce((gs, c) => {
            if (gs.length === 0 || gs[gs.length - 1].label !== c.group) {
                c.newGroup = true;
                gs.push({
                    label: c.group,
                    cols: [c],
                })
            } else {
                gs[gs.length - 1].cols.push(c);
            }
            return gs;
        }, []);
        groups.forEach(g =>
            g.size = g.cols.length);

        $("#main-table thead").innerHTML = [
            el('tr', [el('th')].concat(groups.map(g => el('th', {colspan: g.size, className: "new-point"}, g.label)))),
            el('tr', [el('th')].concat(cols.map(c => el('th', {className: {"new-point": c.newGroup}}, c.label)))),
        ].join("\n");
        $("#main-table tbody").innerHTML = state.bodyRows
            .map((r, rowNum) => el(
                'tr',
                [el('td', null, rowNum + 1)].concat(cols.map((c, i) => el('td', {className: {
                        "new-point": c.newGroup,
                        "number": c.is_number,
                    }}, c.format(r[i])))),
            )).join("\n");
        $("#main-table tfoot").innerHTML = state.totalRows
            .map(r => el(
                'tr',
                [el('th')].concat(cols.map((c, i) => el('th', {className: {
                        "new-point": c.newGroup,
                        "number": c.is_number,
                    }}, c.format(r[i])))),
            )).join("\n");
    } else {
        $("#main-table thead").innerHTML = "";
        $("#main-table tbody").innerHTML = el('tr', el('td', "Loading..."));
        $("#main-table tfoot").innerHTML = "";
    }
}

function selectBlock(sel) {
    fetchTableData(parseInt(sel.value))
}

function byDayToByWeek(byDay) {
    const byWeek = [];
    for (let i = byDay.length - 1; i >= 0; i -= 7) {
        byWeek.unshift(byDay[i]);
    }
    return byWeek;
}

function aggArrayKey(items, key) {
    const agg = items[0][key].map(() => 0)
    items.map(it => it[key]).forEach(v => {
        for (let i = agg.length - 1; i >= 0; i--) {
            agg[i] += v[i];
        }
    }, agg);
    return agg;
}

function fetchTableData(id) {
    setState({
        activeBlock: id,
        loading: true,
    });
    fetch("data/block_" + id + ".json")
        .then(resp => resp.json())
        .then(block => {
            const series = block.segments
                .filter(s => s.population > 0) // no people, bah!
                .map(s => {
                    s = {
                        ...s,
                        cases_by_week: byDayToByWeek(s.cases_by_day),
                        deaths_by_week: byDayToByWeek(s.deaths_by_day),
                    };
                    delete s.cases_by_day;
                    delete s.deaths_by_day;
                    return s;
                });
            const total = {
                ...block,
                is_total: true,
                cases_by_week: aggArrayKey(series, 'cases_by_week'),
                deaths_by_week: aggArrayKey(series, 'deaths_by_week'),
            }
            delete total.segments;
            series.push(total);
            // now cull all but one of the leading zeros (if there are any)
            const lastZero = Math.min(
                total.cases_by_week.lastIndexOf(0),
                total.deaths_by_week.lastIndexOf(0),
            );
            if (lastZero > 0) {
                for (const s of series) {
                    s.cases_by_week = s.cases_by_week.slice(lastZero);
                    s.deaths_by_week = s.deaths_by_week.slice(lastZero);
                }
            }
            // everything's all lined up. Time to do the things!
            for (const s of series) {
                s.points = s.cases_by_week.map((c, i) => {
                    const r = {
                        total_cases: c,
                        total_deaths: s.deaths_by_week[i],
                    }
                    if (i > 0) {
                        r.weekly_cases = r.total_cases - s.cases_by_week[i - 1];
                        r.weekly_deaths = r.total_deaths - s.deaths_by_week[i - 1];
                    }
                    return r;
                }).slice(1);
                delete s.cases_by_week;
                delete s.deaths_by_week;
            }
            // get the list of dates; any array will do
            const dates = total.points.reduce(ds => {
                if (ds == null) {
                    const d = window.lastUpdate
                    d.setHours(12); // avoid having to deal with DST :)
                    return [new Date(d.valueOf() - 86400 * 1000)];
                } else {
                    ds.unshift(new Date(ds[0].valueOf() - 7 * 86400 * 1000));
                    return ds;
                }
            }, null);
            // now we can build the table...
            const bodyRows = [];
            const totalRows = []
            series.forEach(s => {
                const r = [];
                dates.forEach((d, i) => {
                    weeklySeries.forEach(spec =>
                        r.push(spec.calc(s.points[i], s, i)));
                });
                jurisdictionSeries.forEach(spec => {
                    const v = spec.calc(s);
                    if (spec.key === "name") r.unshift(v);
                    else r.push(v);
                });
                if (s.is_total) {
                    totalRows.push(r);
                } else {
                    bodyRows.push(r);
                }
            });
            setState({
                dates,
                bodyRows,
                totalRows,
                block,
                loading: false,
            });
        })
}

fetch("data/blocks.json")
    .then(resp => resp.json())
    .then(blocks => {
        setState({
            blocks,
        })
    });
fetchTableData(840/* US */);