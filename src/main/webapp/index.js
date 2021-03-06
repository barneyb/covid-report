function init(data) {
    let _state = {};
    const setState = s => {
        if (typeof s === "function") s = s(_state);
        _state = {
            ..._state,
            ...s,
        };
        render(_state);
    };
    // noinspection JSMismatchedCollectionQueryUpdate
    const HSL_ZERO = [0, 0, 40];
    // noinspection JSMismatchedCollectionQueryUpdate
    const HSL_INFINITY = [0, 0, 80];
    const colorForDelta = val => {
        const zero = [50, 40, 90]
        if (val > -0.001 && val < 0.001) {
            return zero;
        }
        const dist = Math.min(1, Math.abs(val))
        let end
        if (val < 0) {
            end = [178, 63, 45];
        } else {
            end = [13, 76, 46];
        }
        return [
            zero[0] - (zero[0] - end[0]) * (val > 0 ? Math.sqrt(dist) : dist),
            zero[1] - (zero[1] - end[1]) * (val > 0 ? Math.sqrt(dist) : dist),
            zero[2] - (zero[2] - end[2]) * dist,
        ].map(d => Math.round(d * 100) / 100);
    }
    const drawColumn = segments => {
        const width = 30;
        const height = 150;
        const ds = segments
            .map(it => ({name: it[0], pop: it[1], delta: it[2]}))
            .filter(it => isActualNumber(it.delta))
            .sort((a, b) => b.delta - a.delta);
        const tp = ds.reduce((s, d) => s + d.pop, 0);
        const factor = height / tp;
        return el('svg',
            {
                className: "segments",
                viewBox: `0 0 ${width} ${height}`,
                preserveAspectRatio: "none",
            },
            ds.reduce((agg, it) => {
                const [h,s,l] = colorForDelta(it.delta);
                return {
                    pop: agg.pop + it.pop,
                    rects: agg.rects.concat(el('rect', {
                        x: 0,
                        y: agg.pop * factor,
                        width: width,
                        height: it.pop * factor,
                        fill: formatHsl(h, s, l),
                        onmouseover: `this.style.fill='${formatHsl(270, s, l)}'`,
                        onmouseout: `this.style.fill='${formatHsl(h, s, l)}'`,
                    }, [
                        el('title', it.name + " (" + formatPercent(it.delta, 1, true) + ", week-over-week)"),
                    ])),
                };
            }, {pop: 0, rects: []}).rects)
    }
    const drawSpark = (values, options) => {
        const len = values.length
        const first = values[0]
        const last = values[len - 1]
        const [h,s,l] = first === 0
            ? last === 0 ? HSL_ZERO : HSL_INFINITY
            : colorForDelta((last - first) / first);
        return drawLineChart([{
            values,
            color: formatHsl(h, s + 10, l - 10),
        }], {
            width: 200,
            height: 75,
            ...options,
            gridlines: false,
        })
    };
    const _statHelper = (label, val, title) =>
        el('div', {className: "stat", title}, [
            el('div', {className: "stat-label"}, label),
            el('div', {className: "stat-value"}, val),
        ])
    const drawCountStat = (label, count, title=label) =>
        _statHelper(label, formatNumber(count), title);
    const oneInFormat = new Intl.NumberFormat("en-US", {
        maximumSignificantDigits: 2,
    });
    const addOneIn = (label, count, pop) => {
        if (count > 0) {
            return `${label} (about 1 per ${oneInFormat.format(pop / count)})`;
        }
        return label;
    }
    const drawRateStat = (label, count, pop, title) => {
        const rate = count / pop * HunThou;
        return _statHelper(label, formatNumber(rate, rate < 0.1 ? 2 : 1), addOneIn(title, count, pop));
    };
    const statBlock = (label, count, pop) => [
        drawCountStat(label, count, addOneIn(label, count, pop)),
        pop && drawRateStat("per 100k", count, pop, label + ", per 100,000 population"),
    ];
    const tileRenderers = {
        stats(tile) {
            const spark = tile.cases.spark
            const kids = [
                el('header',
                    el('h2', {title: tile.title}, tile.title)),
                el('div', {
                    className: "spark-container",
                    title: `New cases per day, past ${spark.length <= 21 ? `${spark.length} days` : `${formatNumber(spark.length / 7)} weeks`}`,
                }, drawSpark(spark)),
                ...statBlock("Total Cases", tile.cases.total, tile.population),
                ...statBlock("Daily Cases", tile.cases.daily, tile.population),
                ...statBlock("Total Deaths", tile.deaths.total, tile.population),
                _statHelper("Case Mortality", formatPercent(tile.deaths.total / tile.cases.total), "Total COVID-19 deaths per total COVID-19 cases"),
                tile.population && drawCountStat("Pop", tile.population, "Population"),
            ];
            if (tile.segments && tile.segments.length > 1) {
                kids.push(el('div', {className: "segment-container"}, drawColumn(tile.segments)));
            }
            return kids;
        },
        list(tile) {
            const max = tile.items.reduce((m, it) => Math.max(m, it.value), 0);
            const min = tile.items.reduce((m, it) => Math.min(m, it.value), max);
            const places = min < 5 ? 2 : min < 10 || max < 100 ? 1 : 0;
            const maxRange = tile.items.reduce((m, it) => {
                const max = it.spark.reduce((m, v) => Math.max(m, v), 0);
                const min = it.spark.reduce((m, v) => Math.min(m, v), max);
                return Math.max(m, max - min);
            }, 0);
            return [
                el('header',
                    el('h2', {title: tile.title}, tile.title)),
                el('table', el('tbody',
                tile.items.map(it => el('tr', [
                    el('td', {
                        className: "spark-container",
                        title: `past ${it.spark.length} days`
                    }, drawSpark(it.spark, {
                        width: 75,
                        height: 25,
                        stroke: 2,
                        range: maxRange,
                    })),
                    el('td', it.name),
                    el('td', {className: "number"}, formatNumber(it.value, places)),
                    // el('div', {className: "spark-container"}, ),
                ]))))
            ];
        },
    };
    const drawSection = (state, section) =>
        el("section", {className: "cards"}, section.tiles
            .map(t => el('div',
                el('article', {className: "card " + t.type}, tileRenderers[t.type](t)))));
    const render = state =>
        $("#areas").innerHTML = state.sections
            .map(s => drawSection(state, s))
            .join(el('hr'));
    setState({
        sections: data,
    });
}
fetch("data/index.json")
    .then(r => r.json())
    .then(init)
