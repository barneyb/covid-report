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
    const drawBar = deltas => {
        const ds = deltas
            .filter(it => isActualNumber(it.delta))
            .sort((a, b) => a.delta - b.delta);
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
                tag('polyline', '', {points,fill:"none",stroke:`hsl(${h},${s+10}%,${l-10}%)`,'stroke-width':"2px"}),
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
                            tag('strong', 'Population Breakdown'),
                            tag('span', drawBar(s.breakdown), {className: 'bar'}),
                        ],
                        {className: "bar-layout", title: "Week-over-week change in new case rate, by population segment"}));
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
