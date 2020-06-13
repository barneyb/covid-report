function init(data) {
    const colorForDelta = val => {
        const h = val < 0 ? 92 : 15; // green/red
        const s = 50 + Math.min(1, Math.abs(val)) * 40;
        const l = 95 - Math.min(1, Math.abs(val)) * 40;
        return "hsl(" + h + "," + s + "%," + l + "%)";
    }
    const drawBar = (element, deltas) => {
        const ds = deltas
            .filter(it => isActualNumber(it.delta))
            .sort((a, b) => b.delta - a.delta);
        const tp = ds.reduce((s, d) => s + d.pop, 0);
        element.innerHTML = ds
            .map(it =>
                tag('span', '', {
                    title: it.name + " (" + formatPercent(it.delta) + ")",
                    style: `width:${Math.round(it.pop / tp * 10000) / 100}%;background-color:${colorForDelta(it.delta)}`
                }))
            .join("\n");
    };
    const drawSpark = (element, spark) => {
        const width = 200;
        const height = 50;
        const min = spark.deltas.reduce((a, b) => Math.min(a, b), 999999999)
        const max = spark.deltas.reduce((a, b) => Math.max(a, b), 0)
        const range = max - min;
        const dx = (width - 2) / (spark.deltas.length - 1)
        const points = spark.deltas
            .map((d, i) => [
                1 + i * dx,
                height - 1 - (d - min) / range * (height - 2)
            ])
            .map(p => p.join(","))
            .join(" ");
        element.innerHTML = tag('svg', tag('polyline', '', {points,fill:"none",stroke:"red",'stroke-width':"2px"}), {width: width + "px", height: height + "px"}) +
            tag('div', "Total Cases:", {style: "font-size:90%;text-align:right"}) +
            tag('div', formatNumber(spark.total), {style: "font-weight:bold;font-size:200%;text-align:right"});
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
