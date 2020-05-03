function init(rawData) {
    const formatDate = ld => {
        const ps = ld.split("-")
            .map(p => parseInt(p, 10));
        return new Date(ps[0], ps[1] - 1, ps[2])
            .toLocaleDateString("en-US", {
                month: "short",
                day: "numeric",
            });
    };
    const NumFmt = new Intl.NumberFormat("en-US")
    const formatNumber = (v, places=0) =>
        NumFmt.format(v, {
            minimumFractionDigits: places,
            maximumFractionDigits: places,
        });
    const sumUp = sup => rawData.jurisdictions
        .map(sup)
        .reduce((s, n) => s + n, 0);
    const tag = (n, c, attrs) =>
        `<${n}${Object.keys(attrs || {})
            .map(k => ` ${k === "className" ? "class" : k}="${attrs[k]}"`)
            .join('')}>${c || ''}</${n}>`
    const numTag = (n, v) =>
        tag(n, formatNumber(v), {className: "number"});
    const labelPointCells = () => {
        return rawData.points
            .map(p =>
                tag('th', formatDate(p.date), p.deaths ? {colspan: 2} : null))
            .join("");
    };
    const sublabelPointCells = () => {
        return rawData.points
            .flatMap(p => {
                const cs = [
                    'Cases',
                ];
                if (p.deaths) {
                    cs.push('Deaths');
                }
                return cs;
            })
            .map(c => tag('th', c))
            .join("");
    };
    const buildPointCells = (scope, el='td') => {
        return rawData.points
            .flatMap((p, i) => {
                const d = scope.data[i]
                const cells = [
                    d.cases,
                ];
                if (p.deaths) {
                    cells.push(d.deaths)
                }
                return cells;
            })
            .map(c => numTag(el, c))
            .join("");
    };
    const injectRows = (node, rows) =>
        node.innerHTML = rows.map(it => `<tr>${it}</tr>`).join("\n");
    const $ = document.querySelector.bind(document);
    const head = $("#main-table thead");
    const body = $("#main-table tbody");
    const foot = $("#main-table tfoot");
    $("#updated").innerText = formatDate(rawData.date);
    const total = {
        name: "Total",
        population: sumUp(j => j.population),
        data: rawData.points
            .map((p, i) => {
                const data = {
                    date: p.date,
                    cases: sumUp(j => j.data[i].cases),
                }
                if (p.deaths)
                    data.deaths = sumUp(j => j.data[i].deaths);
                if (p.case_delta) {
                    data.since = {
                        days: p.days,
                        cases: sumUp(j => j.data[i].since.cases),
                    };
                    if (p.death_delta)
                        data.since.deaths = sumUp(j => j.data[i].since.deaths);
                }
                return data;
            })
    };
    const render = () => {
        const headRows = [
            tag('th', '', {colspan: 3}),
            tag('th') + tag('th', 'Jurisdiction') + tag('th', 'Population'),
        ];
        let i = 0;
        const bodyRows = rawData.jurisdictions
            .map(j => tag('td', ++i) + tag('td', j.name) + numTag('td', j.population));
        const footRows = [
            tag('th') + tag('th', total.name) + numTag('th', total.population),
        ];
        // header
        headRows[0] += labelPointCells()
        headRows[1] += sublabelPointCells()
        // body
        rawData.jurisdictions
            .forEach((j, i) =>
                bodyRows[i] += buildPointCells(j));
        // footer
        footRows[0] += buildPointCells(total, 'th')
        injectRows(head, headRows);
        injectRows(body, bodyRows);
        injectRows(foot, footRows);
    };
    render();
}
fetch("./report.json")
    .then(resp => resp.json())
    .then(init);