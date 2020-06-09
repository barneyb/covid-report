function init(data) {
    $("#updated").innerHTML = "Updated: " + formatDate(data.date);
    const breaks = [
        -1,    "delta-down-zero",
        -0.6,  "delta-down-lots",
        -0.25, "delta-down-some",
        -0.1,  "delta-down-bit",
         0,    "delta-down-smidge",
         0.1,  "delta-up-smidge",
         0.25, "delta-up-bit",
         0.6,  "delta-up-some",
         0.8,  "delta-up-lots",
    ]
    const classForDelta = val => {
        if (!val) return null;
        for (var b = 0; b < breaks.length; b += 2) {
            if (val < breaks[b]) return breaks[b + 1];
        }
        return "delta-up-wow";
    };
    const drawBar = (element, deltas) => {
        const ds = deltas
            .filter(it => isActualNumber(it.delta))
            .sort((a, b) => b.delta - a.delta);
        const tp = ds.reduce((s, d) => s + d.pop, 0);
        element.innerHTML = ds
            .map(it =>
                tag('span', '', {
                    title: it.name + " (" + formatPercent(it.delta) + ")",
                    className: classForDelta(it.delta),
                    style: `width:${Math.round(it.pop / tp * 10000) / 100}%`
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
fetch("dashboard.json")
    .then(r => r.json())
    .then(init)
