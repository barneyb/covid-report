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
        ];
    }
    const drawColumn = segments => {
        const width = 30;
        const height = 132;
        const pad = 1;
        const ds = segments
            .map(it => ({name: it[0], pop: it[1], delta: it[2]}))
            .filter(it => isActualNumber(it.delta))
            .sort((a, b) => b.delta - a.delta);
        const tp = ds.reduce((s, d) => s + d.pop, 0);
        const factor = (height - pad - pad) / tp;
        return el('svg', {width:width + "px",height:height + "px"}, ds.reduce((agg, it) => {
            const [h,s,l] = colorForDelta(it.delta);
            return {
                pop: agg.pop + it.pop,
                rects: agg.rects.concat(el('rect', {
                    x: pad,
                    y: pad + Math.round(agg.pop * factor * 100) / 100,
                    width: width - pad - pad,
                    height: Math.round(it.pop * factor * 100) / 100,
                    fill: `hsl(${h},${s}%,${l}%)`,
                    onmouseover: `this.style.fill='hsl(270,${s}%,${l}%)'`,
                    onmouseout: `this.style.fill='hsl(${h},${s}%,${l}%)'`,
                }, [
                    el('title', it.name + " (" + formatPercent(it.delta) + ")"),
                ])),
            };
        }, {pop: 0, rects: []}).rects)
    }
    const drawSpark = values => {
        const width = 200;
        const height = 50;
        const pad = 1;
        const min = values.reduce((a, b) => Math.min(a, b), 999999999)
        const max = values.reduce((a, b) => Math.max(a, b), 0)
        const range = max - min;
        const len = values.length
        const dx = (width - pad - pad) / (len - 1)
        const points = values
            .map((d, i) => [
                pad + i * dx,
                height - pad - (d - min) / range * (height - pad - pad)
            ])
            .map(p => p.join(","))
            .join(" ");
        const first = values[0]
        const last = values[len - 1]
        const [h,s,l] = colorForDelta((last - first) / first);
        return tag('svg', [
                tag('title', `Average new cases per day (past ${len} days)`),
                tag('polyline', '', {points,fill:"none",stroke:`hsl(${h},${s+10}%,${l-10}%)`,'stroke-width':"2px"}),
            ], {width:width + "px",height:height + "px"});
    };
    const drawNum = (label, n) => {
        return tag('div', [
            tag('span', label + ":", {style: "font-size:90%;float:left;margin-top:0.5em"}),
            tag('span', formatNumber(n), {style: "font-weight:bold;font-size:200%"}),
        ], {style: "text-align:right;margin-top:0.25em"});
    }
    const renderAreas = (element, areas) => {
        element.innerHTML = areas
            .map(a => {
                const cols = [];
                cols.push(tag('div', [
                    drawSpark(a.values),
                    drawNum("Daily", a.daily),
                    drawNum("Total", a.total),
                    ], {className: "spark"}));
                if (a.segments) {
                    cols.push(drawColumn(a.segments));
                }
                return tag('h3', a.name) +
                    tag('div', cols
                        .map(c => tag('div', c, {className: "col"})), {className: "colset"})
            })
            .join("\n")
    };
    renderAreas($("#areas"), data.areas)
}
fetch("data/dashboard.json")
    .then(r => r.json())
    .then(init)
