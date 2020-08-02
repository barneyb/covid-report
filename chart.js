LS_KEY = "covid-chart-block";
pointSeries = [{
    key: "daily_cases",
    label: "Daily Cases",
    desc: "Cases reported per day (7-day rolling average).",
    calc: p => p.daily_cases,
    format: v => formatNumber(v, 1),
}, {
    key: "case_rate",
    label: "Case Rate",
    desc: "Average cases reported per day per 100,000 population (7-day rolling average).",
    calc: (p, j) => p.daily_cases / j.population * HunThou,
    format: v => formatNumber(v, 1),
}, {
    key: "total_cases",
    label: "Total Cases",
    desc: "Total cases reported.",
    calc: p => p.total_cases,
}, {
    key: "daily_deaths",
    label: "Daily Deaths",
    desc: "Deaths reported per day (7-day rolling average).",
    calc: p => p.daily_deaths,
    format: v => formatNumber(v, 1),
}, {
    key: "death_rate",
    label: "Death Rate",
    desc: "Average deaths reported per day per 100,000 population (7-day rolling average).",
    calc: (p, j) => p.daily_deaths / j.population * HunThou,
    format: v => formatNumber(v, 1),
}, {
    key: "total_deaths",
    label: "Total Deaths",
    desc: "Total deaths reported.",
    calc: p => p.total_deaths,
}, {
    key: "case_mortality",
    label: "Case Mortality",
    desc: "Deaths this week per case this week.",
    calc: p => p.daily_cases
        ? p.daily_deaths / p.daily_cases
        : null,
    format: formatPercent,
}];
pointSeries.forEach(s => {
    if (!s.hasOwnProperty("format")) s.format = formatNumber;
})

state = {
    hotSegments: [],
};

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
    render(state);
}

selectBlock = sel =>
    fetchTableData(parseInt(sel.value));

selectSeries = key =>
    setState({
        activeSeries: pointSeries.find(s =>
            s.key === key),
    });

toggleSegment = _togglerBuilder("hotSegments");

$pageHeader = $("#page-header")
$chart = $("#chart")
function render(state) {
    const series = state.activeSeries
    if (state.segments) {
        document.title = $pageHeader.innerText = state.block.name + " " + series.label;
        const opts = {
            width: $chart.clientWidth,
            height: $chart.clientHeight,
            stroke: 3,
            dates: state.dates,
        };
        const cold = [];
        const hot = [];
        for (const s of state.segments) {
            const r = {
                values: s[series.key],
                title: s.name,
                onclick: `toggleSegment(${s.id})`
            };
            if (state.hotSegments.indexOf(s.id) >= 0) {
                r.color = formatHsl(s.hue, 50, 50)
                hot.push(r);
            } else {
                r.color = formatHsl(s.hue, ...(s.is_total ? [20, 70] : [10, 80]))
                cold.push(r);
            }
        }
        $chart.innerHTML = drawLineChart(cold.concat(hot), opts);
    } else {
        $chart.innerHTML = el('div', { className: "loading" }, "Loading...");
    }
    if (state.sidebar) {
        const radio = (label, checked, attrs, desc) => {
            if (checked) {
                attrs.checked = "checked";
            }
            return el('label', [
                el('input', {
                    ...attrs,
                    type: "radio",
                }),
                label,
                desc && el('div', {className: "desc"}, desc),
            ]);
        };
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
        sections.push(el('section', [
            el('h3', 'Series'),
            el('div', pointSeries.map(s =>
                radio(s.label, series === s, {
                    name: 'series',
                    value: s.key,
                    onclick: `selectSeries('${s.key}')`
                }, s.desc))),
        ]));
        $sidebar.innerHTML = el('form', sections);
        document.body.classList.add("sidebar");
    } else {
        $sidebar.innerHTML = "";
        document.body.classList.remove("sidebar");
    }
}

// impure!
function roll(state, v, len=7) {
    if (!state.hasOwnProperty("array")) {
        state.array = [];
        state.sum = 0;
    }
    state.array.push(v);
    state.sum += v;
    while (state.array.length > len) {
        state.sum -= state.array.shift();
    }
    return Math.max(state.sum / len, 0);
}

function fetchTableData(id) {
    setState({
        activeBlock: id,
        block: null,
        dates: null,
        segments: null,
        loading: true,
    });
    pushQS({id});
    fetch("data/block_" + id + ".json")
        .then(resp => resp.json())
        .then(block => {
            const [rawSegments, total] = getSegmentsWithTotal(
                block,
                ["cases_by_day", "deaths_by_day"],
            );
            const rawDates = buildDates(total, "cases_by_day");
            const segments = rawSegments
                .filter(s => s.population > 0) // no people, bah!
                .map(s => {
                    const points = s.cases_by_day.reduce((agg, c, i) => {
                        const r = {
                            total_cases: c,
                            total_deaths: s.deaths_by_day[i],
                        };
                        if (i > 0) {
                            // don't even track raw daily cases
                            r.daily_cases = roll(agg.cr, r.total_cases - s.cases_by_day[i - 1]);
                            r.daily_deaths = roll(agg.dr, r.total_deaths - s.deaths_by_day[i - 1]);
                        }
                        agg.ps.push(r);
                        return agg;
                    }, {cr: {}, dr: {}, ps: []}).ps.slice(1);
                    const r = {
                        id: s.id,
                        name: s.name,
                        is_total: !!s.is_total,
                    };
                    pointSeries.forEach(spec => {
                        r[spec.key] = points.map(p => spec.calc(p, s));
                    });
                    return r;
                });
            // assign them hues here, so they're stable
            segments.forEach((s, i) => {
                // noinspection JSPrimitiveTypeWrapperUsage
                s.hue = (i / segments.length) * 360
            });
            setState({
                segments,
                dates: rawDates.slice(1),
                block: {
                    id: block.id,
                    name: block.name,
                },
                loading: false,
            });
        })
}

window.addEventListener("popstate", e => {
    if (e.state.id) fetchTableData(e.state.id);
});
window.addEventListener("resize", () => setState({})); // tee hee

selectSeries("case_rate")
