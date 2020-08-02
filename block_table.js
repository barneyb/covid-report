LS_KEY = "covid-table-block";
weeklySeries = [{
    key: "cases",
    label: "Cases",
    desc: "Cases reported this week.",
    calc: p => p.weekly_cases,
    hot: true,
}, {
    key: "case_rate",
    label: "Case Rate",
    desc: "Average cases reported per day per 100,000 population this week.",
    calc: (p, j) => p.weekly_cases
        ? p.weekly_cases / Week / j.population * HunThou
        : null,
    format: v => formatNumber(v, 1),
    hot: true,
}, {
    key: "case_wow",
    label: Delta + "Case Rate",
    desc: "Change in case rate from last week",
    calc: (p, j, pidx) => {
        if (pidx+1 >= j.points.length) return null;
        const curr = p.weekly_cases / j.population * HunThou;
        const lastWeek = j.points[pidx+1].weekly_cases
        if (lastWeek === 0) return null;
        const prev = lastWeek / j.population * HunThou;
        return (curr - prev) / prev;
    },
    format: formatPercent,
    hot: true,
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
    hot: true,
}, {
    key: "population",
    label: "Population",
    desc: "An estimate of total population.",
    calc: j => j.population,
    hot: true,
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
    sortCol: 0,
    sortAsc: true,
    loading: 0,
    hotDateIdxs: [0, 1, 2, 3],
    hotSeries: weeklySeries.concat(jurisdictionSeries)
        .filter(s => s.hot)
        .map(s => s.key),
};
tableState = {};

function setState(s) {
    const prev = state;
    if (typeof s === "function") {
        s = s(prev);
    }
    if (s == null) return;
    state = {
        ...prev,
        ...s,
    };
    if (["dates", "rows", "hotDateIdxs", "hotSeries"].some(k => state[k] !== prev[k])) {
        tableState = buildTable(state);
    }
    render(state, tableState);
}

// This guy will take the raw dates/series and apply filters to build the table
// to render. Sorting will happen during render.
function buildTable(state) {
    if (!state.dates) return {};
    if (!state.rows) return {};
    const allCols = [];
    state.dates
        .forEach((d, i) => {
            const group = formatDate(d)
            const hot = state.hotDateIdxs.indexOf(i) >= 0;
            return weeklySeries.forEach(spec =>
                allCols.push({
                    ...spec,
                    group,
                    hot: hot && state.hotSeries.indexOf(spec.key) >= 0,
                }))
        });
    jurisdictionSeries.forEach(spec => {
        if (spec.key === "name") allCols.unshift({
            ...spec,
            hot: true, // this one's always hot
        });
        else allCols.push({
            ...spec,
            hot: state.hotSeries.indexOf(spec.key) >= 0,
        });
    });
    const [hotCols, indexes] = allCols.map((c, i) => [c, i])
        .filter(([c, ignored]) => c.hot)
        .reduce((pair, [c, i]) => [
            pair[0].concat(c),
            pair[1].concat(i),
        ], [[], []]);
    const groups = hotCols.reduce((gs, c) => {
        if (gs.length === 0 || gs[gs.length - 1].label !== c.group) {
            c.newGroup = true;
            gs.push({
                label: c.group,
                size: 1,
            })
        } else {
            gs[gs.length - 1].size += 1;
        }
        return gs;
    }, []);
    const body = [];
    const total = []
    state.rows.forEach(s => {
        const d = [];
        for (let i = 0; i < indexes.length; i++) {
            d.push(s.data[indexes[i]]);
        }
        if (s.is_total) {
            total.push(d);
        } else {
            body.push(d);
        }
    });
    return {
        columns: hotCols,
        columnGroups: groups,
        bodyRows: body,
        totalRows: total,
    };
}

function render(state, {columns, columnGroups, bodyRows, totalRows}) {
    if (bodyRows) {
        $("#block-name").innerText = state.block.name;

        const sortIdx = isActualNumber(state.sortCol) && state.sortCol >= 0 && state.sortCol < columns.length
            ? state.sortCol
            : 0;
        $("#main-table thead").innerHTML = [
            el('tr', [el('th')].concat(columnGroups.map(g => el('th', {colspan: g.size, className: "new-point"}, g.label)))),
            el('tr', [el('th')].concat(columns.map((c, i) => el('th', {
                className: {
                    "new-point": c.newGroup,
                    sortable: true,
                    sorted: sortIdx === i,
                    "sorted-asc": state.sortAsc,
                },
                onclick: `handleSort(${i})`,
            }, c.label)))),
        ].join("\n");
        let comp = columns[sortIdx].is_number ? numComp : strComp;
        if (!state.sortAsc) comp = revComp(comp);
        $("#main-table tbody").innerHTML = bodyRows
            .sort((a, b) =>
                comp(a[state.sortCol], b[state.sortCol]))
            .map((r, rowNum) => el(
                'tr',
                [el('td', null, rowNum + 1)].concat(columns.map((c, i) => el('td', {className: {
                        "new-point": c.newGroup,
                        "number": c.is_number,
                    }}, c.format(r[i])))),
            )).join("\n");
        $("#main-table tfoot").innerHTML = totalRows
            .map(r => el(
                'tr',
                [el('th')].concat(columns.map((c, i) => el('th', {className: {
                        "new-point": c.newGroup,
                        "number": c.is_number,
                    }}, c.format(r[i])))),
            )).join("\n");
    } else {
        $("#main-table thead").innerHTML = "";
        $("#main-table tbody").innerHTML = el('tr', el('td', "Loading..."));
        $("#main-table tfoot").innerHTML = "";
    }

    if (state.sidebar) {
        const chkbx = (label, checked, attrs, desc) => {
            if (checked) {
                attrs.checked = "checked";
            }
            return el('label', [
                el('input', {
                    ...attrs,
                    type: "checkbox",
                }),
                label,
                desc && el('div', {className: "desc"}, desc),
            ]);
        };
        document.body.classList.add("sidebar");
        const sections = []
        if (state.blocks) {
            sections.push(el('section', [
                el('h3', 'Block'),
                el('select', {
                    onchange: "selectBlock(this)",
                }, state.blocks.map(b => {
                    const attrs = {value: b.id}
                    if (b.id === state.activeBlock) attrs.selected = "selected";
                    return el('option', attrs, b.is_us ? ("&nbsp; " + b.name + ", US") : b.name)
                })),
            ]));
        }
        if (state.dates) {
            sections.push(el('section', [
                el('h3', 'Weeks Ending'),
                el('div', state.dates
                    .map((d, i) =>
                        chkbx(formatDate(d), state.hotDateIdxs.indexOf(i) >= 0, {
                            onclick: `toggleDate(${i})`,
                        })),
                ),
            ]));
        }
        sections.push(el('section', [
            el('h3', 'Weekly Series'),
            el('div', weeklySeries.map(s =>
                chkbx(s.label, state.hotSeries.indexOf(s.key) >= 0, {
                    onclick: `toggleSeries('${s.key}')`
                }, s.desc))),
        ]));
        sections.push(el('section', [
            el('h3', 'Jurisdiction Series'),
            el('div', jurisdictionSeries
                .filter(s => s.key !== "name") // don't allow disabling this one
                .map(s =>
                    chkbx(s.label, state.hotSeries.indexOf(s.key) >= 0, {
                        onclick: `toggleSeries('${s.key}')`
                    }, s.desc))),
        ]));
        sidebar.innerHTML = el('form', sections);
    } else {
        sidebar.innerHTML = "";
        document.body.classList.remove("sidebar");
    }
}

handleSort = idx => setState(s => {
    if (s.sortCol === idx) {
        return {
            sortAsc: !s.sortAsc,
        };
    } else {
        return {
            sortCol: idx,
            sortAsc: idx === 0,
        }
    }
});

selectBlock = sel => {
    fetchTableData(parseInt(sel.value))
}

function _togglerBuilder(key) {
    return idx => {
        setState(s => {
            const next = s[key].slice();
            const i = next.indexOf(idx);
            if (i < 0) {
                next.push(idx);
            } else {
                next.splice(i, 1);
            }
            return {
                [key]: next,
            };
        });
    };
}

toggleDate = _togglerBuilder("hotDateIdxs");
toggleSeries = _togglerBuilder("hotSeries");

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
        block: null,
        dates: null,
        series: null,
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
            delete block.segments;
            const total = {
                ...block,
                is_total: true,
                cases_by_week: aggArrayKey(series, 'cases_by_week'),
                deaths_by_week: aggArrayKey(series, 'deaths_by_week'),
            }
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
            // get the list of dates; any array will do
            const dates = total.cases_by_week.reduce(ds => {
                if (ds == null) {
                    const d = window.lastUpdate
                    d.setHours(12); // avoid having to deal with DST :)
                    return [new Date(d.valueOf() - 86400 * 1000)];
                } else {
                    ds.unshift(new Date(ds[0].valueOf() - 7 * 86400 * 1000));
                    return ds;
                }
            }, null).slice(1).reverse();
            // everything's all lined up. Time to do the things!
            const rows = series.map(s => {
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
                }).slice(1).reverse();
                const data = [];
                dates.forEach((d, i) => {
                    weeklySeries.forEach(spec =>
                        data.push(spec.calc(s.points[i], s, i)));
                });
                jurisdictionSeries.forEach(spec => {
                    const v = spec.calc(s);
                    if (spec.key === "name") data.unshift(v);
                    else data.push(v);
                });
                return {
                    id: s.id,
                    name: s.name,
                    is_total: s.is_total,
                    data,
                }
            });
            setState({
                rows,
                dates,
                block: {
                    id: block.id,
                    name: block.name,
                },
                loading: false,
            });
        })
}

fetch("data/blocks.json")
    .then(resp => resp.json())
    .then(blocks => {
        blocks.forEach(b => {
            b.is_beb = b.id >= ID_BEB;
            b.is_us = Math.floor(b.id / 100000) === ID_US;
        });
        blocks.sort((a, b) => {
            // beb's first
            if (a.is_beb !== b.is_beb) {
                return a.is_beb ? -1 : 1;
            }
            // non-US next
            if (a.is_us !== b.is_us) {
                return a.is_us ? 1 : -1;
            }
            // they're in the same bucket, so alphabetical
            return a.name.localeCompare(b.name);
        });
        setState({
            blocks,
        })
    });
fetchTableData(ID_US);