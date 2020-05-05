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
            expr: d => d.cases,
        },
        {
            name: "Case Rate",
            expr: (d, p, j) => d.cases / j.pop * HunThou,
        },
        {
            name: "New Cases",
            test: p => p.case_delta,
            expr: d => d.new_cases,
        },
        {
            name: "New Cases Rate",
            test: p => p.case_delta,
            expr: (d, p, j) => d.new_cases / p.days * Week / j.pop * HunThou,
        },
        {
            name: "Deaths",
            test: p => p.deaths,
            expr: d => d.cases,
        },
        {
            name: "Death Rate",
            test: p => p.deaths,
            expr: (d, p, j) => d.deaths / j.pop * HunThou,
        },
        {
            name: "New Deaths",
            test: p => p.death_delta,
            expr: d => d.new_deaths,
        },
        {
            name: "Case Mortality",
            test: p => p.deaths,
            expr: d => d.deaths / d.cases,
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
        {
            scope: "jurisdiction",
            name: "Population",
            expr: j => j.pop,
            format: formatNumber,
        },
    ].concat(rawData.points
        .flatMap((p, i) =>
            series
                .filter(s => s.test(p))
                .map(s => ({
                    group: formatDate(p.date),
                    name: s.name,
                    expr: s.expr,
                    format: s.format,
                    p: p,
                    pidx: i,
                }))));
    const columnGroups = columns.reduce((gs, c) => {
        const gn = c.group || "";
        return {
            ...gs,
            [gn]: (gs[gn] || 0) + 1,
        };
    }, {});
    const rows = dataRecords.map(rec =>
        columns
            .map(c =>
                c.scope === "jurisdiction" ? c.expr(rec)
                : c.expr(rec.data[c.pidx], c.p, rec)));

    const $ = document.querySelector.bind(document);
    let state = {};
    window.setState = s =>
        render(state = {...state, ...(typeof s === "function" ? s(state) : s)});
    const tag = (el, c, attrs) =>
        `<${el}${Object.keys(attrs || {})
            .map(k => ` ${k === "className" ? "class" : k}="${attrs[k]}"`)
            .join('')}>${c || ''}</${el}>`
    const numTag = (el, v, fmt = formatNumber) =>
        tag(el, fmt(v), {className: "number"});
    const labelPointCells = () =>
        Object.keys(columnGroups)
            .map(gn =>
                tag('th', gn, {
                    colspan: columnGroups[gn] + (gn === "" ? 1 : 0),
                }));
    const sublabelPointCells = (state) =>
        tag('th')
        + columns.map((c, i) =>
            tag('th', c.name, state.sortCol === i ? {
                className: "sortable sorted",
                onclick: `setState(s => ({sortCol:${i},sortAsc:!s.sortAsc}))`,
            } : {
                className: "sortable",
                onclick: `setState({sortCol:${i}})`,
            }))
            .join("");
    const renderRow = (r, el, num) =>
        tag(el, num)
        + columns.map((c, i) =>
            (typeof r[i] === "number" ? numTag : tag)(el, r[i], c.format))
            .join("");
    const injectRows = (node, rows) =>
        node.innerHTML = rows
            .map(r =>
                `<tr>${r.join ? r.join("") : r}</tr>`)
            .join("\n");
    const numComp = (a, b) => a - b;
    const strComp = (a, b) => a < b ? -1 : a > b ? 1 : 0;
    const revComp = sort => (a, b) => sort(b, a);
    const head = $("#main-table thead");
    const body = $("#main-table tbody");
    const foot = $("#main-table tfoot");
    $("#updated").innerText = `Updated ${formatDate(rawData.date)}`;
    const render = state => {
        injectRows(head, [
            labelPointCells(),
            sublabelPointCells(state),
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
        ]);
    };
    setState({
        sortCol: 11,
        sortAsc: false,
    });
}

fetch("./report.json")
    .then(resp => resp.json())
    .then(init);