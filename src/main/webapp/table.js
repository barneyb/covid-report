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
        const prev = j.points[pidx+1].weekly_cases
        if (prev === 0) return null;
        return (p.weekly_cases - prev) / prev;
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
seriesLookup = {};
weeklySeries.concat(jurisdictionSeries).forEach(s => {
    if (!s.hasOwnProperty("format")) s.format = formatNumber;
    if (!s.hasOwnProperty("is_number")) s.is_number = true;
    seriesLookup[s.key] = s;
})

let tableState = {};
const setState = useState({
    sortCol: 0,
    sortAsc: true,
    loading: 0,
    hotDateIdxs: new Set([0, 1, 2, 3]),
    hotSeries: new Set(weeklySeries.concat(jurisdictionSeries)
        .filter(s => s.hot)
        .map(s => s.key)),
    hotRows: new Set(),
    sidebar: location.search === "?sidebar",
}, (state, prev) => {
    toQS(state);
    if (["dates", "rows", "hotDateIdxs", "hotSeries"].some(k => state[k] !== prev[k])) {
        tableState = buildTable(state);
    }
    render(state, tableState);
});

// This guy will take the raw dates/series and apply filters to build the table
// to render. Sorting will happen during render.
function buildTable(state) {
    if (!state.dates) return {};
    if (!state.rows) return {};
    const allCols = [];
    state.dates
        .forEach((d, i) => {
            const group = formatDate(d)
            const hot = state.hotDateIdxs.has(i);
            return weeklySeries.forEach(spec =>
                allCols.push({
                    ...spec,
                    group,
                    hot: hot && state.hotSeries.has(spec.key),
                }))
        });
    jurisdictionSeries.forEach(spec => {
        if (spec.key === "name") allCols.unshift({
            ...spec,
            hot: true, // this one's always hot
        });
        else allCols.push({
            ...spec,
            hot: state.hotSeries.has(spec.key),
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
        d.id = s.id;
        d.hue = s.hue;
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

$blockName = $("#block-name")
$thead = $("#main-table thead")
$tbody = $("#main-table tbody")
$tfoot = $("#main-table tfoot")
function render(state, {columns, columnGroups, bodyRows, totalRows}) {
    if (bodyRows) {
        $blockName.innerText = state.block.name;
        document.title = $blockName.parentNode.innerText;
        const sortIdx = isActualNumber(state.sortCol) && state.sortCol >= 0 && state.sortCol < columns.length
            ? state.sortCol
            : 0;
        $thead.innerHTML = [
            el('tr', {},
                el('th'),
                ...columnGroups.map(g => el('th', {colspan: g.size, className: "new-point"}, g.label))),
            el('tr', {},
                el('th'),
                ...columns.map((c, i) => el('th', {
                    className: {
                        newPoint: c.newGroup,
                        sortable: true,
                        sorted: sortIdx === i,
                        sortedAsc: state.sortAsc,
                    },
                    title: c.desc,
                    onclick: `handleSort(${i})`,
                }, c.label))),
        ].join("\n");
        let comp = columns[sortIdx].is_number ? numComp : strComp;
        if (!state.sortAsc) comp = revComp(comp);
        $tbody.innerHTML = bodyRows
            .sort((a, b) =>
                comp(a[state.sortCol], b[state.sortCol]))
            .map((r, rowNum) => el('tr', {},
                el('td', {
                    onclick: `toggleRow(${r.id})`,
                }, rowNum + 1),
                ...columns.map((c, i) => el('td', {
                    className: {
                        newPoint: c.newGroup,
                        number: c.is_number,
                    },
                    style: state.hotRows.has(r.id)
                        ? `background-color: ${formatHsl(r.hue, 100, 90)}`
                        : null,
                }, c.format(r[i])))
            )).join("\n");
        $tfoot.innerHTML = totalRows
            .map(r => el('tr', {},
                el('th'),
                ...columns.map((c, i) => el('th', {className: {
                        newPoint: c.newGroup,
                        number: c.is_number,
                    }}, c.format(r[i]))),
            )).join("\n");
    } else {
        $thead.innerHTML = "";
        $tbody.innerHTML = el('tr', el('td', {
            style: {
                height: "40vh",
                width: "60vw",
                textAlign: "center",
            }
        }, "Loading..."));
        $tfoot.innerHTML = "";
    }

    if (state.sidebar) {
        const chkbx = _pickCtrlBuilder("checkbox");
        const sections = [];
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
                el('div', state.dates.map((d, i) =>
                    chkbx(formatDate(d), state.hotDateIdxs.has(i), {
                        onclick: `toggleDate(${i})`,
                    })),
                ),
            ]));
        }
        sections.push(el('section', [
            el('h3', 'Weekly Series'),
            el('div', weeklySeries.map(s =>
                chkbx(s.label, state.hotSeries.has(s.key), {
                    name: 'series',
                    value: s.key,
                    onclick: `toggleSeries('${s.key}')`
                }, s.desc))),
        ]));
        sections.push(el('section', [
            el('h3', 'Jurisdiction Series'),
            el('div', jurisdictionSeries
                .filter(s => s.key !== "name") // don't allow disabling this one
                .map(s =>
                    chkbx(s.label, state.hotSeries.has(s.key), {
                        onclick: `toggleSeries('${s.key}')`
                    }, s.desc))),
        ]));
        $sidebar.innerHTML = el('form', sections);
        document.body.classList.add("sidebar");
    } else {
        $sidebar.innerHTML = "";
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

toggleDate = _togglerBuilder("hotDateIdxs");
toggleSeries = _togglerBuilder("hotSeries");
toggleRow = _togglerBuilder("hotRows");

function byDayToByWeek(byDay) {
    const byWeek = [];
    for (let i = byDay.length - 1; i >= 0; i -= 7) {
        byWeek.unshift(byDay[i]);
    }
    return byWeek;
}

function fetchTableData(id) {
    setState({
        activeBlock: id,
        block: null,
        dates: null,
        segments: null,
        hotSegments: [],
        loading: true,
    });
    document.querySelectorAll("#navbar .block-table").forEach(it =>
        it.classList.remove("active"));
    document.querySelectorAll("#navbar .block-table[data-id='" + id + "']").forEach(it =>
        it.classList.add("active"));
    pushQS({id});
    fetch("data/block_" + id + ".json")
        .then(resp => resp.json())
        .then(block => {
            const rawSegments = getDataSegments(
                block,
                ["cases_by_week", "deaths_by_week"],
                s => {
                    s = {
                        ...s,
                        cases_by_week: byDayToByWeek(s.cases_by_day),
                        deaths_by_week: byDayToByWeek(s.deaths_by_day),
                    };
                    delete s.cases_by_day;
                    delete s.deaths_by_day;
                    return s;
                },
            );
            const rawDates = buildDates(rawSegments[0], "cases_by_week", Week);
            const rows = rawSegments
                .filter(s => s.population > 0) // no people, bah!
                .map(s => {
                    // noinspection JSPrimitiveTypeWrapperUsage
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
                    s.points.forEach((ignored, i) => {
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
                        hue: s.hue,
                        is_total: !!s.is_total,
                        data,
                    }
                });
            setState({
                rows,
                dates: rawDates.slice(1).reverse(),
                block: {
                    id: block.id,
                    name: block.name,
                },
                loading: false,
            });
        })
}

const toQS = state => {
    const qs = {};
    if (state.activeBlock) qs.id = "" + state.activeBlock;
    if (state.hotDateIdxs) qs.ds = [...state.hotDateIdxs].sort(numComp).join(".");
    if (state.hotSeries) qs.ss = [...state.hotSeries].sort().join(".");
    if (state.hotRows) qs.h = [...state.hotRows].sort(numComp).join(".");
    qs.s = (state.sortAsc ? "" : "!") + state.sortCol;
    const curr = history.state;
    return pushQS(qs, curr && curr.id === qs.id);
};

const fromQS = qs =>
    qs && setState(s => {
        if (typeof qs.id === "string") {
            qs.id = parseInt(qs.id);
        }
        if (s.blocks && s.blocks.every(b => b.id !== qs.id)) {
            qs.id = ID_US;
        }
        const next = {
            activeBlock: qs.id,
        };
        if (qs.ds) {
            next.hotDateIdxs = new Set(qs.ds.split(".")
                .map(s => parseInt(s))
                .filter(isActualNumber));
        }
        if (qs.ss) {
            next.hotSeries = new Set(qs.ss.split(".")
                .filter(s => seriesLookup.hasOwnProperty(s)));
        }
        if (qs.s) {
            next.sortAsc = qs.s.charAt(0) !== "!";
            next.sortCol = parseInt(next.sortAsc ? qs.s : qs.s.substr(1));
        }
        if (qs.h) {
            next.hotRows = new Set(qs.h.split(".")
                .map(s => parseInt(s))
                .filter(isActualNumber));
        }
        return next;
    }, (s, p) => {
        if (s.activeBlock !== p.activeBlock) fetchTableData(s.activeBlock);
    });

window.addEventListener("popstate", e => fromQS(e.state));

fromQS(parseQS());
fetch("data/blocks.json")
    .then(resp => resp.json())
    .then(blocks => {
        addFlags(blocks);
        blocks.sort(blockComp);
        setState(s => {
            const next = {
                blocks,
            };
            if (blocks.every(b => b.id !== s.activeBlock)) {
                next.activeBlock = ID_US;
            }
            return next;
        }, s => fetchTableData(s.activeBlock));
    });

