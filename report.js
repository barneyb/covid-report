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
    const formatNumber = (v, places = 0) =>
        NumFmt.format(v, {
            minimumFractionDigits: places,
            maximumFractionDigits: places,
        });
    const sumUp = supplier => rawData.jurisdictions
        .map(supplier)
        .reduce((s, n) => s + n, 0);
    const tag = (el, c, attrs) =>
        `<${el}${Object.keys(attrs || {})
            .map(k => ` ${k === "className" ? "class" : k}="${attrs[k]}"`)
            .join('')}>${c || ''}</${el}>`
    const numTag = (el, v) =>
        tag(el, formatNumber(v), {className: "number"});
    const labelPointCells = () =>
        tag('th', '', {colspan: 3})
        + rawData.points
            .map(p => {
                let cs = 1;
                if (p.deaths) cs += 1;
                if (p.case_delta) {
                    cs += 1;
                    if (p.death_delta) cs += 1;
                }
                return tag(
                    'th',
                    formatDate(p.date),
                    cs > 1 ? {colspan: cs} : null,
                );
            })
            .join("");
    const sublabelPointCells = () =>
        tag('th')
        + tag('th', 'Jurisdiction')
        + tag('th', 'Population')
        + rawData.points
            .flatMap(p => {
                const cs = [
                    'Cases',
                ];
                if (p.case_delta) {
                    cs.push('New')
                }
                if (p.deaths) {
                    cs.push('Deaths');
                    if (p.death_delta) {
                        cs.push('New')
                    }
                }
                return cs;
            })
            .map(c => tag('th', c))
            .join("");
    const renderDataRecord = (rec, el, num) =>
        tag(el, num)
        + tag(el, rec.name)
        + numTag(el, rec.pop)
        + rawData.points
            .flatMap((p, i) => {
                const d = rec.data[i]
                const cs = [
                    d.cases,
                ];
                if (p.case_delta) {
                    cs.push(d.new_cases);
                }
                if (p.deaths) {
                    cs.push(d.deaths)
                    if (p.death_delta) {
                        cs.push(d.new_deaths)
                    }
                }
                return cs;
            })
            .map(c => numTag(el, c))
            .join("");
    const injectRows = (node, rows) =>
        node.innerHTML = rows.map(it => `<tr>${it}</tr>`).join("\n");
    const $ = document.querySelector.bind(document);
    const head = $("#main-table thead");
    const body = $("#main-table tbody");
    const foot = $("#main-table tfoot");
    $("#updated").innerText = "Updated " + formatDate(rawData.date);
    const dataRecords = [{
        name: "Total",
        pop: sumUp(j => j.population),
        data: rawData.points.map((p, i) => {
            const fs = {
                cases: sumUp(j => j.data[i].cases),
            }
            if (p.deaths) fs.deaths = sumUp(j => j.data[i].deaths);
            if (p.case_delta) {
                fs.new_cases = sumUp(j => j.data[i].since.cases);
                if (p.death_delta) {
                    fs.new_deaths = sumUp(j => j.data[i].since.deaths);
                }
            }
            return fs;
        }),
    }, ...rawData.jurisdictions
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
        })),
    ];
    const render = () => {
        injectRows(head, [
            labelPointCells(),
            sublabelPointCells(),
        ]);
        injectRows(body, dataRecords.slice(1)
            .map((j, i) => renderDataRecord(j, 'td', i + 1)));
        injectRows(foot, [
            (renderDataRecord(dataRecords[0], 'th')),
        ]);
    };
    render();
}

fetch("./report.json")
    .then(resp => resp.json())
    .then(init);