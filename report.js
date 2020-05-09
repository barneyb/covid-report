/*
 * Oh hai! Fancy meeting you here. You look fabulous, by the way. Very healthy.
 */
function init(rawData) {
    const HunThou = 100000;
    const Week = 7;
    const fTrue = () => true;
    const IDENTITY = v => v;
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

    // expr is "(d, p, j) => value"
    const series = [
        {
            scope: "jurisdiction",
            name: "Jurisdiction",
            expr: j => j.name,
            format: IDENTITY,
        },
        {
            name: "Cases",
            desc: "Total number of reported cases.",
            expr: d => d.cases,
            cold: true,
        },
        {
            name: "Case Rate",
            desc: "Total cases per 100,000 population.",
            expr: (d, p, j) => d.cases / j.pop * HunThou,
        },
        {
            name: "New Cases",
            desc: "New cases reported this week.",
            test: p => p.case_delta,
            expr: d => d.new_cases,
            cold: true,
        },
        {
            name: "New Cases Rate",
            desc: "New cases reported this week per 100,000 population.",
            test: p => p.case_delta,
            expr: (d, p, j) => d.new_cases / j.pop * HunThou,
        },
        {
            name: "Deaths",
            desc: "Total number of reported deaths.",
            test: p => p.deaths,
            expr: d => d.deaths,
            cold: true,
        },
        {
            name: "Death Rate",
            desc: "Total deaths per 100,000 population.",
            test: p => p.deaths,
            expr: (d, p, j) => d.deaths / j.pop * HunThou,
            format: n => formatNumber(n, 1),
        },
        {
            name: "New Deaths",
            desc: "New deaths reported this week.",
            test: p => p.death_delta,
            expr: d => d.new_deaths,
            cold: true,
        },
        {
            name: "New Death Rate",
            desc: "New deaths reported this week per 100,000 population.",
            test: p => p.death_delta,
            expr: (d, p, j) => d.new_deaths / j.pop * HunThou,
        },
        {
            name: "Case Mortality",
            desc: "Deaths per case.",
            test: p => p.deaths,
            expr: d => d.deaths / d.cases,
            format: formatPercent,
        },
        {
            name: "New Case Mortality",
            desc: "New deaths per new case.",
            test: p => p.death_delta,
            expr: d => d.new_deaths / d.new_cases,
            format: formatPercent,
        },
        {
            scope: "jurisdiction",
            name: "Population",
            desc: "Census's US population estimate for July 1, 2019.",
            expr: j => j.pop,
            format: formatNumber,
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
                    cases: d.cases,
                }
                if (p.deaths) fs.deaths = d.deaths;
                if (p.case_delta) {
                    if (p.days !== Week) throw new Error("Non-week period!");
                    fs.new_cases = d.since.cases;
                    if (p.death_delta) {
                        fs.new_deaths = d.since.deaths;
                    }
                }
                return fs;
            }),
        }));
    const sumUp = supplier => dataRecords
        .map(supplier)
        .reduce((s, n) => s + n, 0);
    dataRecords.unshift({
        name: "Total",
        pop: sumUp(j => j.pop),
        data: rawData.points.map((p, i) => {
            const fs = {
                cases: sumUp(j => j.data[i].cases),
            }
            if (p.deaths) fs.deaths = sumUp(j => j.data[i].deaths);
            if (p.case_delta) {
                fs.new_cases = sumUp(j => j.data[i].new_cases);
                if (p.death_delta) {
                    fs.new_deaths = sumUp(j => j.data[i].new_deaths);
                }
            }
            return fs;
        }),
    });
    for (const rec of dataRecords) {
        rec.groups = rawData.points.reduce((ps, p, pidx) => ({
            ...ps,
            [p.label]: series
                .filter(s => s.scope === "point")
                .filter(s => s.test(p))
                .reduce((ss, s) => ({
                    ...ss,
                    [s.name]: s.expr(rec.data[pidx], p, rec)
                }), {}),
            [undefined]: series
                .filter(s => s.scope === "jurisdiction")
                .reduce((ss, s) => ({
                    ...ss,
                    [s.name]: s.expr(rec)
                }), {}),
        }), {});
    }

    const rebuildTable = state => {
        const columns = [
            series[0],
            ...rawData.points
                .filter(p => !state.coldGroups.includes(p.label))
                .reverse()
                .flatMap(p =>
                    series
                        .filter(s => !state.coldSeries.includes(s.name))
                        .filter(s => s.scope === "point")
                        .filter(s => s.test(p))
                        .map(s => ({
                            group: p.label,
                            ...s,
                            p: p,
                        }))),
            series[series.length - 1]];
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
            ...state,
            columns,
            columnGroups,
            rows,
        }
    }

    const $ = document.querySelector.bind(document);
    let state = {};
    window.setState = s =>
        render(state = rebuildTable({
            ...state,
            ...(typeof s === "function" ? s(state) : s)
        }));
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
    const head = $("#main-table thead");
    const body = $("#main-table tbody");
    const foot = $("#main-table tfoot");
    const sidebar = $("#sidebar .content");
    $("#updated").innerText = `Updated ${formatDate(rawData.date)}`;
    $("#show-sidebar").addEventListener("click", () => setState({sidebar: true}))
    $("#hide-sidebar").addEventListener("click", () => setState({sidebar: false}))
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
        const renderRow = (r, el, num) =>
            tag(el, num)
            + state.columns.map((c, i) => {
                const num = typeof r[i] === "number";
                const hotIdx = state.hotRows.indexOf(r[0])
                return tag(el, c.format(r[i]), {
                    className: [
                        num ? "number" : "",
                        newPointIdxs.includes(i) ? "new-point" : "",
                        hotIdx >= 0 ? `hot hot-${hotIdx}` : "",
                    ].filter(IDENTITY).join(" "),
                    onclick: `toggleHotRow('${r[0]}')`
                })
            })
                .join("");
        const labelRow = labelPointCells()
        const sublabelRow = sublabelPointCells(state)
        injectRows(head, [
            labelRow,
            sublabelRow,
        ]);
        let comp = typeof state.rows[1][state.sortCol] === "number"? numComp : strComp;
        if (!state.sortAsc) comp = revComp(comp);
        injectRows(body, state.rows.slice(1)
            .sort((a, b) =>
                comp(a[state.sortCol], b[state.sortCol]))
            .map((r, i) =>
                renderRow(r, 'td', i + 1))
        );
        injectRows(foot, [
            renderRow(state.rows[0], 'th'),
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
                    tag('h3', 'Series'),
                    ...series
                        .filter(it => it.scope === "point")
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
    setState({
        sortCol: 0,
        sortAsc: true,
        hotRows: ["Oregon", "Montana", "New York"],
        sidebar: false,
        coldGroups: [],
        coldSeries: series
            .filter(it => it.cold)
            .map(it => it.name),
    });
}

fetch("./report.json")
    .then(resp => resp.json())
    .then(init);