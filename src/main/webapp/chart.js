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
seriesLookup = {};
pointSeries.forEach(s => {
    if (!s.hasOwnProperty("format")) s.format = formatNumber;
    seriesLookup[s.key] = s;
});
defaultSeries = seriesLookup["case_rate"]

const setState = useState({
    activeSeries: defaultSeries,
    start: new Date(2020, 3 - 1, 15),
    end: new Date(),
    sidebar: location.search === "?sidebar",
}, render);

selectBlock = sel =>
    fetchTableData(parseInt(sel.value));

selectSeries = key =>
    setState({
        activeSeries: seriesLookup[key] || defaultSeries,
    });

toggleSegment = _togglerBuilder("hotSegments");

$pageHeader = $("#page-header")
$chart = $("#chart")
$legend = $("#legend")
$dateTrack = $("#date-track")
$thumbLeft = $("#date-track .range-mask.left")
$thumbRight = $("#date-track .range-mask.right")
function swatch(s) {
    return el(
        'span',
        {
            className: "swatch",
            style: {backgroundColor: formatHsl(s.hue, 60, 50)},
        },
    )
}

function render(state) {
    toQS(state);
    const series = state.activeSeries
    if (state.block) {
        document.title = $pageHeader.innerText = state.block.name + " " + series.label;
    }
    if (state.segments) {
        const [hot, cold] = state.segments.reduce(([h, c], s) => {
            if (state.hotSegments.indexOf(s.id) >= 0) {
                h.push(s);
            } else if (s.is_relevant) {
                c.push(s);
            }
            return [h, c];
        }, [[], []]);
        const tb = hot => s => ({
            values: s[series.key],
            title: s.name,
            onclick: `toggleSegment(${s.id})`,
            color: hot
                ? formatHsl(s.hue, 50, 50)
                : formatHsl(s.hue, ...(s.is_total ? [20, 70] : [10, 80])),
        });
        const paintChart = (sd, ed) => {
            const [start, end] = rangeToIndices(state.dates, sd, ed);
            const datesToDisplay = state.dates.slice(start, end);
            const seriesToDisplay = cold.map(tb(false))
                .concat(hot.map(tb(true)))
                .map(s => {
                    s.values = s.values.slice(start, end);
                    return s;
                });
            $chart.innerHTML = drawLineChart(seriesToDisplay, {
                width: $chart.clientWidth,
                height: $chart.clientHeight,
                stroke: 3,
                dates: datesToDisplay,
            });
        }
        setTimeout(() => { // sidebar show has to draw DOM so we can measure
            paintChart(state.start, state.end);
            const s = hot.length === 0
                ? cold[cold.length - 1]
                : hot[hot.length - 1];
            $dateTrack.innerHTML = drawDateRangeSlider(
                state.dates,
                state.start,
                state.end,
                {
                    width: $dateTrack.clientWidth,
                    height: $dateTrack.clientHeight,
                    series: s && {
                        values: s[series.key],
                        color: formatHsl(s.hue, 60, 50),
                    },
                    onMotion: paintChart,
                    onCommit: (start, end) => setState({
                        start,
                        end,
                    }),
                });
        });
        // legend
        if (hot.length > 0) {
            $legend.innerHTML = hot.map(s =>
                el('div', [
                    swatch(s),
                    s.name,
                    el('a', {
                        className: "remove",
                        onclick: `toggleSegment(${s.id})`,
                    }, el('svg', {
                        viewbox: "0 0 10 10"
                    }, [
                        el('line', {x1: 2, y1: 2, x2: 8, y2: 8, stroke: "#999"}),
                        el('line', {x1: 8, y1: 2, x2: 2, y2: 8, stroke: "#999"}),
                    ]))
                ])).join("\n");
            $legend.classList.remove("empty");
        } else {
            $legend.innerText = "";
            $legend.classList.add("empty");
        }
    } else {
        $chart.innerHTML = el('div', { className: "loading" }, "Loading...");
    }
    if (state.sidebar) {
        const radio = _pickCtrlBuilder("radio");
        const chkbx = _pickCtrlBuilder("checkbox");
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
        state.segments && sections.push(el('section', {className: "segments"}, [
            el('h3', 'Segments'),
            el('div', state.segments.map(s =>
                chkbx(el('span', {
                    className: {
                        irrelevant: !s.is_relevant,
                    }
                }, [s.name, swatch(s)]),
                    state.hotSegments.indexOf(s.id) >= 0, {
                        name: 'segment',
                        value: s.id,
                        onclick: `toggleSegment(${s.id})`
                    })))
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
    fetch("data/block_" + id + ".json")
        .then(resp => resp.json())
        .then(block => {
            const rawSegments = getDataSegments(
                block,
                ["cases_by_day", "deaths_by_day"],
            );
            const relevanceThreshold = arrayMax(rawSegments.find(s => s.is_total).cases_by_day)
                / rawSegments.reduce((c, s) => s.is_total ? c : (c + 1), 0)
                / 5;
            const rawDates = buildDates(rawSegments[0], "cases_by_day");
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
                        is_relevant: s.is_total || arrayMax(s.cases_by_day) >= relevanceThreshold,
                    };
                    pointSeries.forEach(spec => {
                        r[spec.key] = points.map(p => spec.calc(p, s));
                    });
                    return r;
                });
            // assign them hues here, so they're stable
            segments.forEach((s, i) => {
                // noinspection JSPrimitiveTypeWrapperUsage
                s.hue = s.is_total ? 0 : 40 + (i / rawSegments.length) * 280;
            });
            setState(s => {
                let hotSegments = null;
                if (s.hotSegments) {
                    const ids = new Set(segments.map(s => s.id));
                    hotSegments = s.hotSegments
                        .filter(id => ids.has(id));
                }
                if (!hotSegments || hotSegments.length === 0) {
                    hotSegments = [id];
                }
                return {
                    segments,
                    hotSegments,
                    dates: rawDates.slice(1),
                    block: {
                        id: block.id,
                        name: block.name,
                    },
                    loading: false,
                };
            });
        })
}

const toQS = state => {
    const qs = {};
    if (state.activeBlock) qs.id = "" + state.activeBlock;
    if (state.activeSeries) qs.s = state.activeSeries.key;
    if (state.hotSegments) qs.h = state.hotSegments.join(",");
    qs.d = [state.start, state.end].map(unparseDate).join(":");
    const curr = history.state;
    return pushQS(qs, curr && curr.id === qs.id && curr.s === qs.s);
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
        if (seriesLookup.hasOwnProperty(qs.s)) {
            next.activeSeries = seriesLookup[qs.s];
        }
        if (qs.h) {
            next.hotSegments = qs.h.split(",")
                .map(s => parseInt(s))
                .filter(isActualNumber);
        }
        if (qs.d) {
            try {
                const [sd, ed] = qs.d.split(":").map(parseDate);
                next.start = sd;
                next.end = ed;
            } catch (e) {
                console.warn("error parsing dates - ignore", e);
            }
        }
        return next;
    }, (s, p) => {
        if (s.activeBlock !== p.activeBlock) fetchTableData(s.activeBlock);
    });

window.addEventListener("popstate", e => fromQS(e.state));
window.addEventListener("resize", () => setState({})); // tee hee

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
