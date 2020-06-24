function init(data) {
    const colorForDelta = val => {
        const h = val < 0 ? 92 : 15; // green/red
        const s = 50 + Math.min(1, Math.abs(val)) * 40;
        const l = 95 - Math.min(1, Math.abs(val)) * 40;
        return [h,s,l];
    }
    const drawBar = deltas => {
        const ds = deltas
            .filter(it => isActualNumber(it.delta))
            .sort((a, b) => b.delta - a.delta);
        const tp = ds.reduce((s, d) => s + d.pop, 0);
        return ds
            .map(it => {
                const [h,s,l] = colorForDelta(it.delta)
                return tag('span', '', {
                    title: it.name + " (" + formatPercent(it.delta) + ")",
                    style: `width:${Math.round(it.pop / tp * 10000) / 100}%;background-color:hsl(${h},${s}%,${l}%)`,
                    onmouseover: `this.style.backgroundColor='hsl(270,${s}%,${l}%)'`,
                    onmouseout: `this.style.backgroundColor='hsl(${h},${s}%,${l}%)'`,
                })
            })
            .join("\n");
    };
    const drawSpark = spark => {
        const width = 200;
        const height = 50;
        const pad = 3;
        const min = spark.values.reduce((a, b) => Math.min(a, b), 999999999)
        const max = spark.values.reduce((a, b) => Math.max(a, b), 0)
        const range = max - min;
        const len = spark.values.length
        const dx = (width - pad - pad) / (len - 1)
        const points = spark.values
            .map((d, i) => [
                pad + i * dx,
                height - pad - (d - min) / range * (height - pad - pad)
            ])
            .map(p => p.join(","))
            .join(" ");
        const first = spark.values[0]
        const last = spark.values[len - 1]
        const [h,s,l] = colorForDelta((last - first) / first);
        return tag('svg', [
                tag('title', `Average new cases per day (past ${len} days)`),
                tag('polyline', '', {points,fill:"none",stroke:`hsl(${h},${s+10}%,${l-20}%)`,'stroke-width':"2px"}),
            ], {width:width + "px",height:height + "px"});
    };
    const drawNum = (label, n) => {
        return tag('div', [
            tag('span', label + ":", {style: "font-size:90%;float:left;margin-top:0.5em"}),
            tag('span', formatNumber(n), {style: "font-weight:bold;font-size:200%"}),
        ], {style: "text-align:right;margin-top:0.25em"});
    }
    const drawSparks = element => {
        element.innerHTML = data.sparks
            .map(s => {
                const cols = [];
                cols.push(tag('div', [
                    drawSpark(s),
                    drawNum("Daily", s.daily),
                    drawNum("Total", s.total),
                    ], {className: "spark"}));
                if (s.breakdown) {
                    cols.push(tag('div',
                        [
                            tag('strong', 'Case Rate'),
                            tag('span', 'Decreasing', {className: 'good-label'}),
                            tag('span', 'Increasing', {className: 'bad-label'}),
                            tag('span', drawBar(s.breakdown), {className: 'bar'}),
                        ],
                        {className: "bar-layout", title: "Population segments, ordered by change in new case rate between this week and last"}));
                }
                return tag('h3', s.name) +
                    tag('div', cols
                        .map(c => tag('div', c, {className: "col"})), {className: "colset"})
            })
            .join("\n")
    };
    drawSparks($("#sparks"))
}
fetch("data/dashboard.json")
    .then(r => r.json())
    .then(init)
