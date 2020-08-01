function init(data) {
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
    const formatHsl = (h, s, l) =>
        `hsl(${formatNumber(h, 2)},${formatNumber(s, 2)}%,${formatNumber(l, 2)}%)`;
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
    const drawSpark = values => {
        const width = 200;
        const height = 50;
        const stroke = 3;
        const min = values.reduce((a, b) => Math.min(a, b), 999999999)
        const max = values.reduce((a, b) => Math.max(a, b), 0)
        const range = max - min;
        const len = values.length
        const dx = width / (len - 1)
        const points = values
            .map((d, i) => [
                i * dx,
                formatNumber(height - (d - min) / range * height, 2)
            ])
            .map(p => p.join(","))
            .join(" ");
        const first = values[0]
        const last = values[len - 1]
        const [h,s,l] = colorForDelta((last - first) / first);
        return el(
            'svg',
            {
                viewBox: `${-stroke} ${-stroke} ${width + 2 * stroke} ${height + 2 * stroke}`,
            },
            [
                el('title', `Average new cases per day (past ${len} days)`),
                el('polyline', {
                    points,
                    fill: "none",
                    stroke: formatHsl(h, s + 10, l - 10),
                    'stroke-width': stroke + "px",
                    'stroke-linejoin': "round",
                    'stroke-linecap': "round",
                }),
            ]);
    };
    const _statHelper = (label, val, title) =>
        el('div', {className: "stat", title}, [
            el('div', {className: "stat-label"}, label),
            el('div', {className: "stat-value"}, val),
        ])
    const drawCountStat = (label, count, title) =>
        _statHelper(label, formatNumber(count), title);
    const drawRateStat = (count, pop, title) =>
        _statHelper("per 100k", formatNumber(count / pop * HunThou, 1), title);
    const oneInFormat = new Intl.NumberFormat("en-US", {
        maximumSignificantDigits: 2,
    });
    const drawOneInStat = (count, pop, title) =>
        _statHelper(
            "1 per",
            count === 0 ? "-" : oneInFormat.format(pop / count),
            title);
    const drawSection = section =>
        el("section", [
            el('h1', section.label),
            el('div', {className: "cards"}, section.areas
                .map(n => data.lookup[n])
                .map(a => {
                    const kids = [
                        el('header',[
                            el('h3', {title:a.name}, a.name),
                        ]),
                        el('div', {className: "spark-container"}, drawSpark(a.values)),
                        drawCountStat("Total Cases", a.total, "Total cases"),
                        a.pop && drawRateStat(a.total, a.pop, "Total cases, per 100,000 population"),
                        a.pop && drawOneInStat(a.total, a.pop, "One case, on average, per this many people"),
                        drawCountStat("Daily Cases", a.daily, "New cases per day"),
                        a.pop && drawRateStat(a.daily, a.pop, "New cases per day, per 100,000 population"),
                        a.pop && drawOneInStat(a.daily, a.pop, "One new case each day, on average, per this many people"),
                        a.pop && drawCountStat("Pop", a.pop, "Population"),
                    ];
                    if (a.segments && a.segments.length > 1) {
                        kids.push(el('div', {className: "segment-container"}, drawColumn(a.segments)));
                    }
                    return el('article', {className: "card"}, kids)
                })
                .join("\n")),
        ]);
    $("#areas").innerHTML = data.sections
        .map(drawSection)
        .join("\n");
}
fetch("data/dashboard.json")
    .then(r => r.json())
    .then(init)
