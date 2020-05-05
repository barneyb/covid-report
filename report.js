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
    series.forEach(s => {
        if (s.test == null) s.test = () => true;
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

    const tag = (el, c, attrs) =>
        `<${el}${Object.keys(attrs || {})
            .map(k => ` ${k === "className" ? "class" : k}="${attrs[k]}"`)
            .join('')}>${c || ''}</${el}>`
    const numTag = (el, v, fmt = formatNumber) =>
        tag(el, fmt(v), {className: "number"});
    const labelPointCells = () =>
        tag('th', '', {colspan: 3})
        + rawData.points
            .map(p =>
                tag('th', formatDate(p.date), {
                    className: "point new-point",
                    colspan: series
                        .filter(s => s.test(p))
                        .length,
                }))
            .join("");
    const sublabelPointCells = () =>
        tag('th')
        + tag('th', 'Jurisdiction', {className: "sortable"})
        + tag('th', 'Population', {className: "sortable"})
        + rawData.points
            .flatMap(p => series
                .filter(s => s.test(p))
                .map((s, i) =>
                    tag('th', s.name, {className: "sortable" + (i === 0 ? " new-point" : "")})))
            .join("");
    const renderDataRecord = (rec, el, num) =>
        tag(el, num)
        + tag(el, rec.name)
        + numTag(el, rec.pop)
        + rawData.points
            .flatMap((p, i) =>
                series
                    .filter(s => s.test(p))
                    .map(s => numTag(el, s.expr(rec.data[i], p, rec), s.format)))
            .join("");
    const injectRows = (node, rows) =>
        node.innerHTML = rows.map(it => `<tr>${it}</tr>`).join("\n");
    const $ = document.querySelector.bind(document);
    const head = $("#main-table thead");
    const body = $("#main-table tbody");
    const foot = $("#main-table tfoot");
    $("#updated").innerText = "Updated " + formatDate(rawData.date);
    const render = () => {
        injectRows(head, [
            labelPointCells(),
            sublabelPointCells(),
        ]);
        injectRows(body, dataRecords.slice(1)
            .map((j, i) =>
                renderDataRecord(j, 'td', i + 1)));
        injectRows(foot, [
            renderDataRecord(dataRecords[0], 'th'),
        ]);
    };
    render();
}

fetch("./report.json")
    .then(resp => resp.json())
    .then(init);