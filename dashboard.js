function init(data) {
    const colorForDelta = val => {
        const h = val < 0 ? 92 : 15; // green/red
        const s = 50 + Math.min(1, Math.abs(val)) * 40;
        const l = 95 - Math.min(1, Math.abs(val)) * 40;
        return [h,s,l];
    }
    const drawBar = (element, deltas) => {
        const ds = deltas
            .filter(it => isActualNumber(it.delta))
            .sort((a, b) => b.delta - a.delta);
        const tp = ds.reduce((s, d) => s + d.pop, 0);
        element.innerHTML = ds
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
    const drawSpark = (element, spark) => {
        const width = 200;
        const height = 50;
        const pad = 3;
        const min = spark.deltas.reduce((a, b) => Math.min(a, b), 999999999)
        const max = spark.deltas.reduce((a, b) => Math.max(a, b), 0)
        const range = max - min;
        const dx = (width - pad - pad) / (spark.deltas.length - 1)
        const points = spark.deltas
            .map((d, i) => [
                pad + i * dx,
                height - pad - (d - min) / range * (height - pad - pad)
            ])
            .map(p => p.join(","))
            .join(" ");
        element.innerHTML = tag('svg', [
                tag('title', 'New cases per day per 100,000 population'),
                tag('polyline', '', {points,fill:"none",stroke:"red",'stroke-width':"2px"}),
            ], {width:width + "px",height:height + "px"}) +
            tag('div', "Total Cases:", {style: "font-size:90%;text-align:right;margin-top:0.25em"}) +
            tag('div', formatNumber(spark.total), {style: "font-weight:bold;font-size:200%;text-align:right;margin-top:-0.15em"});
    };
    drawBar($("#world-bar .bar"), data.world_case_rate_deltas)
    drawBar($("#us-bar .bar"), data.us_case_rate_deltas)
    drawBar($("#or-bar .bar"), data.or_case_rate_deltas)
    drawSpark($("#world-spark"), data.world_spark)
    drawSpark($("#us-spark"), data.us_spark)
    drawSpark($("#or-spark"), data.or_spark)
    drawSpark($("#washco-spark"), data.wash_co_spark)
    drawSpark($("#multco-spark"), data.mult_co_spark)
}
fetch("data/dashboard.json")
    .then(r => r.json())
    .then(init)
