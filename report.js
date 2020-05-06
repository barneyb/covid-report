function init(rawData) {
    const HunThou = 100000;
    const Week = 7;
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
            name: "Cases",
            desc: "Total number of reported cases.",
            expr: d => d.cases,
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
            expr: (d, p) => d.new_cases,
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
    ];
    const fTrue = () => true;
    const IDENTITY = v => v;
    series.forEach(s => {
        if (s.test == null) s.test = fTrue;
        if (s.format == null) s.format = formatNumber;
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

    const columns = [
        {
            scope: "jurisdiction",
            name: "Jurisdiction",
            expr: j => j.name,
            format: IDENTITY,
        },
    ].concat(rawData.points
        .map((p, i) => ({p, i}))
        .reverse()
        .flatMap(({p, i}) =>
            series
                .filter(s => s.test(p))
                .map(s => ({
                    group: formatDate(p.date),
                    name: s.name,
                    desc: s.desc,
                    expr: s.expr,
                    format: s.format,
                    p: p,
                    pidx: i,
                }))),
        {
            scope: "jurisdiction",
            name: "Population",
            desc: "US Census population estimate for July 1, 2019.",
            expr: j => j.pop,
            format: formatNumber,
        });
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
        columns
            .map(c =>
                c.scope === "jurisdiction" ? c.expr(rec)
                : c.expr(rec.data[c.pidx], c.p, rec)));

    const $ = document.querySelector.bind(document);
    let state = {};
    window.setState = s =>
        render(state = {...state, ...(typeof s === "function" ? s(state) : s)});
    window.toggleHotRow = jn =>
        setState(s => {
            const idx = s.hotRows.indexOf(jn);
            const hotRows = s.hotRows.slice();
            if (idx < 0) {
                hotRows.push(jn);
            } else {
                hotRows.splice(idx, 1);
            }
            return { hotRows };
        });
    const tag = (el, c, attrs) =>
        `<${el}${Object.keys(attrs || {})
            .map(k => ` ${k === "className" ? "class" : k}="${attrs[k]}"`)
            .join('')}>${c || ''}</${el}>`;
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
    $("#updated").innerText = `Updated ${formatDate(rawData.date)}`;
    const render = state => {
        const labelPointCells = () =>
            tag('th')
            + columnGroups.map(g =>
                tag('th', g.group, {
                    colspan: g.count,
                    className: "new-point",
                }))
                .join("");
        const newPointIdxs = columnGroups
            .reduce((agg, g) => {
                    agg.n += g.count;
                    agg.idxs.push(agg.n);
                    return agg; // tee-hee
                }, {n: 0, idxs: [0]})
            .idxs;
        const sublabelPointCells = () =>
            tag('th')
            + columns.map((c, i) => {
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
            + columns.map((c, i) => {
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
        let comp = typeof rows[1][state.sortCol] === "number"? numComp : strComp;
        if (!state.sortAsc) comp = revComp(comp);
        injectRows(body, rows.slice(1)
            .sort((a, b) =>
                comp(a[state.sortCol], b[state.sortCol]))
            .map((r, i) =>
                renderRow(r, 'td', i + 1))
        );
        injectRows(foot, [
            renderRow(rows[0], 'th'),
            sublabelRow,
            labelRow,
        ]);
    };
    setState({
        sortCol: 0,
        sortAsc: true,
        hotRows: ["Montana", "New York", "Oregon"],
    });
}

fetch("./report.json")
    .then(resp => resp.json())
    .then(init);