/*
 * Oh hai! Fancy meeting you here. You look fabulous, by the way. Very healthy.
 */
function init(rawData) {
    const HunThou = 100000;
    const Week = 7;
    const fTrue = () => true;
    const IDENTITY = v => v;
    const isNum = v =>
        typeof v === "number" || v instanceof Number;
    const formatDate = ld => {
        const ps = ld.split("-")
            .map(p => parseInt(p, 10));
        return new Date(ps[0], ps[1] - 1, ps[2])
            .toLocaleDateString("en-US", {
                month: "short",
                day: "numeric",
            });
    };
    const nfpMap = new Map();
    const formatNumber = (v, places = 0) => {
        if (!nfpMap.has(places)) {
            nfpMap.set(places, new Intl.NumberFormat("en-US", {
                minimumFractionDigits: places,
                maximumFractionDigits: places,
            }));
        }
        return nfpMap.get(places).format(v);
    };
    const formatPercent = (v, places = 1) =>
        formatNumber(v * 100, places) + "%";
    const formatDeathRate = v => formatNumber(v, 1)
    const formatDeathRateSegment = v => formatNumber(v, 2)

    // expr is "(d, p, j) => value"
    const series = [
        {
            scope: "jurisdiction",
            name: "Jurisdiction",
            expr: j => j.name,
            format: IDENTITY,
            hot: true,
        },
        {
            name: "Total Cases",
            desc: "Total number of reported cases.",
            expr: d => d.total_cases,
            cold: true,
        },
        {
            name: "Total Case Rate",
            desc: "Total cases per 100,000 population.",
            expr: (d, p, j) => d.total_cases / j.pop * HunThou,
            cold: true,
        },
        {
            name: "Cases",
            desc: "Cases reported this week.",
            test: p => p.case_delta,
            expr: d => d.cases,
            cold: true,
            time: true,
        },
        {
            name: "Case Rate",
            desc: "Cases reported this week per 100,000 population.",
            test: p => p.case_delta,
            expr: (d, p, j) => d.cases / j.pop * HunThou,
            time: true,
        },
        {
            name: "Total Deaths",
            desc: "Total number of reported deaths.",
            test: p => p.deaths,
            expr: d => d.total_deaths,
            cold: true,
        },
        {
            name: "Total Death Rate",
            desc: "Total deaths per 100,000 population.",
            test: p => p.deaths,
            expr: (d, p, j) => d.total_deaths / j.pop * HunThou,
            format: formatDeathRate,
            cold: true,
        },
        {
            name: "Deaths",
            desc: "Deaths reported this week.",
            test: p => p.death_delta,
            expr: d => d.deaths,
            cold: true,
            time: true,
        },
        {
            name: "Death Rate",
            desc: "Deaths reported this week per 100,000 population.",
            test: p => p.death_delta,
            expr: (d, p, j) => d.deaths / j.pop * HunThou,
            format: formatDeathRate,
            time: true,
        },
        {
            name: "Total Case Mortality",
            desc: "Total deaths per total case.",
            test: p => p.deaths,
            expr: d => d.total_deaths / d.total_cases,
            format: formatPercent,
            cold: true,
        },
        {
            name: "Case Mortality",
            desc: "Deaths per case.",
            test: p => p.death_delta,
            expr: d => d.deaths / d.cases,
            format: formatPercent,
            time: true,
        },
        {
            scope: "jurisdiction",
            name: "Population",
            desc: "The US Census's estimated population.",
            expr: j => j.pop,
            hot: true,
        },
        {
            scope: "jurisdiction",
            name: "Overall DR",
            desc: "The CDC's expected overall deaths per week per 100,000 population.",
            expr: j => j.rates.total,
            format: formatDeathRate,
        },
        {
            scope: "jurisdiction",
            name: "Cardiac DR",
            desc: "The CDC's expected cardiac disease deaths (I00-I99: Diseases of the circulatory system) per week per 100,000 population.",
            expr: j => j.rates.circ,
            format: formatDeathRateSegment,
        },
        {
            scope: "jurisdiction",
            name: "Cancer DR",
            desc: "The CDC's expected cancer-related deaths (C00-D48: Neoplasms) per week per 100,000 population.",
            expr: j => j.rates.cancer,
            format: formatDeathRateSegment,
        },
        {
            scope: "jurisdiction",
            name: "Respiratory DR",
            desc: "The CDC's expected respiratory disease deaths (J00-J98: Diseases of the respiratory system) per week per 100,000 population.",
            expr: j => j.rates.resp,
            format: formatDeathRateSegment,
        },
        {
            scope: "jurisdiction",
            name: "Mental DR",
            desc: "The CDC's expected mental disorder deaths (F01-F99: Mental and behavioural disorders) per week per 100,000 population.",
            expr: j => j.rates.mental,
            format: formatDeathRateSegment,
        },
        {
            scope: "jurisdiction",
            name: "Non-Trans Accident DR",
            desc: "The CDC's expected non-transportation accidental deaths (W00-X59: Other external causes of accidental injury) per week per 100,000 population.",
            expr: j => j.rates.non_trans,
            format: formatDeathRateSegment,
        },
        {
            scope: "jurisdiction",
            name: "Self-Harm DR",
            desc: "The CDC's expected self-harm deaths (X60-X84: Intentional self-harm) per week per 100,000 population.",
            expr: j => j.rates.self,
            format: formatDeathRateSegment,
        },
        {
            scope: "jurisdiction",
            name: "Transport DR",
            desc: "The CDC's expected transportation-related deaths (V01-V99: Transport accidents) per week per 100,000 population.",
            expr: j => j.rates.trans,
            format: formatDeathRateSegment,
        },
    ];
    series.forEach(s => {
        if (s.scope == null) s.scope = "point";
        if (s.test == null) s.test = fTrue;
        if (s.format == null) s.format = formatNumber;
    });
    rawData.points.forEach(p => {
        p.label = formatDate(p.date);
    });

    const dataRecords = rawData.jurisdictions
        .map(j => ({
            name: j.name,
            pop: j.population,
            data: rawData.points.map((p, i) => {
                const d = j.data[i];
                const fs = {
                    total_cases: d.cases,
                }
                if (p.deaths) fs.total_deaths = d.deaths;
                if (p.case_delta) {
                    if (p.days !== Week) throw new Error("Non-week period!");
                    fs.cases = d.since.cases;
                    if (p.death_delta) {
                        fs.deaths = d.since.deaths;
                    }
                }
                return fs;
            }),
            rates: Object.keys(j.mortality_rates)
                .reduce((rs, k) => ({
                    ...rs,
                    [k]: j.mortality_rates[k] / 365.24 * Week,
                }), {})
        }));
    const sumUp = supplier => dataRecords
        .map(supplier)
        .reduce((s, n) => s + n, 0);
    dataRecords.unshift({
        name: "Total",
        pop: sumUp(j => j.pop),
        data: rawData.points.map((p, i) => {
            const fs = {
                total_cases: sumUp(j => j.data[i].total_cases),
            }
            if (p.deaths) fs.total_deaths = sumUp(j => j.data[i].total_deaths);
            if (p.case_delta) {
                fs.cases = sumUp(j => j.data[i].cases);
                if (p.death_delta) {
                    fs.deaths = sumUp(j => j.data[i].deaths);
                }
            }
            return fs;
        }),
        rates: Object.keys(rawData.jurisdictions[0].mortality_rates)
            .reduce((rs, k) => ({
                ...rs,
                [k]: sumUp(j => j.pop * j.rates[k])
                    / sumUp(j => j.pop),
            }), {})
    });
    for (const rec of dataRecords) {
        rec.groups = rawData.points
            .reduce((agg, p, pidx) => {
                const data = series
                    .filter(s => s.scope === "point")
                    .filter(s => s.test(p))
                    .reduce((ss, s) => {
                        let val = s.expr(rec.data[pidx], p, rec)
                        if (s.time && agg.prev) {
                            const prev = agg.prev[s.name];
                            if (prev) {
                                // noinspection JSPrimitiveTypeWrapperUsage
                                val = new Number(val);
                                val._change = prev === 0
                                    ? 10 // Any increase from zero means tenfold! By fiat!
                                    : (val - prev) / prev;
                            }
                        }
                        return {
                            ...ss,
                            [s.name]: val,
                        }
                    }, {
                        _prev: agg.prev,
                    })
                return {
                    prev: data,
                    ps: {
                        ...agg.ps,
                        [p.label]: data,
                        [undefined]: series
                            .filter(s => s.scope === "jurisdiction")
                            .reduce((ss, s) => ({
                                ...ss,
                                [s.name]: s.expr(rec),
                            }), {}),
                    },
                }
            }, {}).ps;
    }

    const buildTable = state => {
        const hotSeries = series
            .filter(s => !state.coldSeries.includes(s.name))
        const columns = [
            series[0],
            ...rawData.points
                .filter(p => !state.coldGroups.includes(p.label))
                .reverse()
                .flatMap(p =>
                    hotSeries
                        .filter(s => s.scope === "point")
                        .filter(s => s.test(p))
                        .map(s => ({
                            group: p.label,
                            ...s,
                            p: p,
                        }))),
            ...hotSeries
                .filter(it => it.scope === "jurisdiction")
                .slice(1) // name
        ];
        const columnGroups = columns
            .map(c => ({
                group: c.group || "",
                count: 1,
            }))
            .reduce((gs, c) => {
                const last = gs[gs.length - 1];
                if (last && last.group === c.group) {
                    last.count += 1;
                } else {
                    gs.push(c);
                }
                return gs; // tee hee
            }, []);
        const rows = dataRecords.map(rec =>
            columns.map(c =>
                rec.groups[c.group][c.name]));
        return {
            columns,
            columnGroups,
            totalRow: rows[0],
            jRows: rows.slice(1),
        }
    }

    const LS_KEY = "covid-report-state";
    let state = {};
    let tableState = {};
    window.setState = s => {
        const prev = state;
        state = {
            ...state,
            ...(typeof s === "function" ? s(prev) : s),
        };
        window.localStorage.setItem(LS_KEY, JSON.stringify(state));
        if (state.coldGroups !== prev.coldGroups || state.coldSeries !== prev.coldSeries) {
            tableState = buildTable(state);
        }
        render({
            ...state,
            ...tableState,
        });
    };
    const toggleBuilder = cn => it =>
        setState(s => {
            const next = s[cn].slice();
            let idx = next.indexOf(it);
            if (idx < 0) {
                idx = next.indexOf(null);
                idx < 0 ? next.push(it) : (next[idx] = it);
            } else {
                next[idx] = null;
            }
            return { [cn]: next };
        });
    window.toggleHotRow = toggleBuilder('hotRows');
    window.toggleGroup = toggleBuilder('coldGroups');
    window.toggleSeries = toggleBuilder('coldSeries');
    const tag = (el, c, attrs) =>
        `<${el}${Object.keys(attrs || {})
            .map(k => ` ${k === "className" ? "class" : k}="${attrs[k]}"`)
            .join('')}>${c && c.join ? c.filter(IDENTITY).join("") : c || ''}</${el}>`;
    const injectRows = (node, rows) =>
        node.innerHTML = rows
            .map(r =>
                tag('tr', r.join ? r.join("") : r))
            .join("\n");
    const numComp = (a, b) => a - b;
    const strComp = (a, b) => a < b ? -1 : a > b ? 1 : 0;
    const revComp = sort => (a, b) => sort(b, a);
    const $ = document.querySelector.bind(document);
    const head = $("#main-table thead");
    const body = $("#main-table tbody");
    const foot = $("#main-table tfoot");
    const sidebar = $("#sidebar .content");
    $("#updated").innerText = `Updated ${formatDate(rawData.date)}`;
    $("#show-sidebar").addEventListener("click", () => setState({sidebar: true}))
    $("#hide-sidebar").addEventListener("click", () => setState({sidebar: false}))
    $("#reset-to-defaults").addEventListener("click", () => {
        window.localStorage.setItem(LS_KEY, JSON.stringify({sidebar: true}));
        window.location.reload();
    })
    const render = state => {
        const labelPointCells = () =>
            tag('th')
            + state.columnGroups.map(g =>
                tag('th', g.group, {
                    colspan: g.count,
                    className: "new-point",
                }))
                .join("");
        const newPointIdxs = state.columnGroups
            .reduce((agg, g) => {
                    agg.n += g.count;
                    agg.idxs.push(agg.n);
                    return agg; // tee-hee
                }, {n: 0, idxs: [0]})
            .idxs;
        const sublabelPointCells = () =>
            tag('th')
            + state.columns.map((c, i) => {
                const attrs = {
                    className: "sortable",
                };
                if (c.desc) attrs.title = c.desc;
                if (newPointIdxs.includes(i)) {
                    attrs.className += " new-point";
                }
                if (state.sortCol === i) {
                    attrs.onclick = `setState(s => ({sortCol:${i},sortAsc:!s.sortAsc}))`;
                    attrs.className += " sorted";
                } else {
                    attrs.onclick = `setState({sortCol:${i},sortAsc:${i === 0}})`;
                }
                return tag('th', c.name, attrs)
            })
                .join("");
        const classForDelta = val => {
            if (!val.hasOwnProperty("_change")) return null;
            if (val._change === 0) return "delta-equal";
            if (val === 0) return "delta-down-zero";
            if (val._change < 0) {
                return val._change > -0.04 ? "delta-down-smidge"
                    : val._change > -0.2 ? "delta-down-bit"
                    : val._change > -0.4 ? "delta-down-some"
                    : "delta-down-lots";
            } else {
                return val._change < 0.05 ? "delta-up-smidge"
                    : val._change < 0.25 ? "delta-up-bit"
                    : val._change < 0.5 ? "delta-up-some"
                    : val._change < 0.75 ? "delta-up-lots"
                    : "delta-up-wow";
            }
        };
        const renderRow = (r, el, num) =>
            tag(el, num)
            + state.columns.map((c, i) => {
                const val = r[i];
                return tag(el, c.format(val), {
                    className: [
                        isNum(val) ? "number" : "",
                        classForDelta(val),
                        newPointIdxs.includes(i) ? "new-point" : "",
                    ].filter(IDENTITY).join(" "),
                })
            })
                .join("");
        const labelRow = labelPointCells()
        const sublabelRow = sublabelPointCells(state)
        injectRows(head, [
            labelRow,
            sublabelRow,
        ]);
        let comp = isNum(state.totalRow[state.sortCol]) ? numComp : strComp;
        if (!state.sortAsc) comp = revComp(comp);
        body.innerHTML = state.jRows
            .sort((a, b) =>
                comp(a[state.sortCol], b[state.sortCol]))
            .map((r, i) => {
                const hotIdx = state.hotRows.indexOf(r[0])
                return tag('tr', renderRow(r, 'td', i + 1), {
                    className: hotIdx >= 0 ? `hot hot-${hotIdx}` : "",
                    onclick: `toggleHotRow('${r[0]}')`
                })
            })
            .join("\n")
        injectRows(foot, [
            renderRow(state.totalRow, 'th'),
            sublabelRow,
            labelRow,
        ]);
        if (state.sidebar) {
            const chkbx = (label, checked, attrs, desc) => {
                if (checked) {
                    attrs.checked = "checked";
                }
                return tag('label', [
                    tag('input', undefined, {
                        ...attrs,
                        type: "checkbox"
                    }),
                    label,
                    desc && tag('div', desc, {className: "desc"})
                ]);
            };
            document.body.className = "sidebar";
            const sections = [
                tag('section', [
                    tag('h3', 'Dates'),
                    ...rawData.points
                        .map(p => formatDate(p.date))
                        .reverse()
                        .map(l =>
                            chkbx(l, !state.coldGroups.includes(l), {
                                onclick: `toggleGroup('${l}')`
                            }))
                ]),
                tag('section', [
                    tag('h3', 'Date Series'),
                    ...series
                        .filter(it => it.scope === "point")
                        .map(s =>
                            chkbx(s.name, !state.coldSeries.includes(s.name), {
                                onclick: `toggleSeries('${s.name}')`
                            }, s.desc))
                ]),
                tag('section', [
                    tag('h3', 'Jurisdiction Series'),
                    ...series
                        .slice(1) // name
                        .filter(it => it.scope === "jurisdiction")
                        .map(s =>
                            chkbx(s.name, !state.coldSeries.includes(s.name), {
                                onclick: `toggleSeries('${s.name}')`
                            }, s.desc))
                ]),
            ];
            sidebar.innerHTML = tag('form', sections);
        } else {
            sidebar.innerText = "";
            document.body.className = "";
        }
    };
    setState(() => {
        let state = {
            sortCol: 0,
            sortAsc: true,
            hotRows: ["Oregon", "Montana", "New York"],
            sidebar: false,
            coldGroups: [],
            coldSeries: series
                .filter(it =>
                    it.scope === "point" ? it.cold : !it.hot)
                .map(it => it.name),
        };
        try {
            const cache = window.localStorage.getItem(LS_KEY)
            if (cache) {
                state = {
                    ...state,
                    ...JSON.parse(cache),
                };
            }
        } catch (e) {}
        return state;
    });
}

fetch("./report.json")
    .then(resp => resp.json())
    .then(init);