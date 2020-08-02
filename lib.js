const $ = document.querySelector.bind(document);
const HunThou = 100000;
const Week = 7;
const ID_BEB = 252000000;
const ID_US = 840
const fTrue = () => true;
const IDENTITY = v => v;
const isNum = v =>
    typeof v === "number" || v instanceof Number;
const isActualNumber = v =>
    v != null && !isNaN(v) && isFinite(v)
const parseDate = ld => {
    const ps = ld.trim()
        .split("-")
        .map(p => parseInt(p, 10));
    return new Date(ps[0], ps[1] - 1, ps[2]);
};
const formatDate = ld =>
    (typeof ld === "string" ? parseDate(ld) : ld)
        .toLocaleDateString("en-US", {
            month: "short",
            day: "numeric",
        });
const nfpMap = new Map();
const formatNumber = (v, places = 0) => {
    if (!isActualNumber(v)) return '';
    if (!nfpMap.has(places)) {
        nfpMap.set(places, new Intl.NumberFormat("en-US", {
            minimumFractionDigits: places,
            maximumFractionDigits: places,
        }));
    }
    return nfpMap.get(places).format(v);
};
const formatPercent = (v, places = 1, plus=false) => {
    if (!isActualNumber(v)) return '';
    return (plus && v > 0 ? "+" : "") + formatNumber(v * 100, places) + "%"
};
const formatDeathRate = v => formatNumber(v, 1)
const formatDeathRateSegment = v => formatNumber(v, 2)
const Delta = "&#x1D6AB;"
const parseQS = (qs = location.search) => {
    if (!qs) return {};
    return qs.substr(1)
        .split("&")
        .map(p => p.split("="))
        .reduce((r, p) => {
            const n = p[0];
            if (r.hasOwnProperty(n)) {
                if (!(r[n] instanceof Array)) {
                    r[n] = [r[n]];
                }
                r[n].push(p[1]);
            } else {
                r[n] = p[1];
            }
            return r;
        }, {});
};
const formatQS = data => {
    if (!data) return "";
    const qs = "?" + Object.keys(data)
        .flatMap(k => {
            const prefix = encodeURIComponent(k) + "="
            const v = data[k];
            if (v instanceof Array) {
                return v.map(e => prefix + encodeURIComponent(e));
            } else {
                return prefix + encodeURIComponent(v);
            }
        })
        .join("&");
    return qs === "?" ? "" : qs;
};
const pushQS = dataOrQS => {
    let qs, data;
    if (typeof dataOrQS === "string") {
        qs = dataOrQS;
        data = parseQS(qs);
    } else {
        data = dataOrQS;
        qs = formatQS(data);
    }
    if (location.search !== qs) {
        history.pushState(data, document.title, qs);
    }
}
const camel2kebab = p => {
    for (let i = p.length - 1; i > 0; i--) {
        const c = p.charAt(i)
        if (c >= "A" && c <= "Z") {
            p = p.substr(0, i) + "-" + c.toLowerCase() + p.substr(i + 1);
        }
    }
    return p;
}
const el = (name, attrs, children, ...moreKids) => {
    if (children == null && (attrs instanceof Array || typeof attrs === "string")) {
        children = attrs;
        attrs = null;
    }
    if (attrs == null) attrs = {};
    if (attrs.hasOwnProperty("style")) {
        const st = attrs.style;
        if (st instanceof Array) {
            attrs.style = st.join(";");
        } else if (typeof st !== "string") {
            attrs.style = Object.keys(st)
                .map(k => camel2kebab(k) + ":" + st[k])
                .join(";");
        }
    }
    if (attrs.hasOwnProperty("className")) {
        const cns = attrs.className;
        if (cns instanceof Array) {
            attrs.className = cns
                .filter(IDENTITY)
                .join(" ");
        } else if (typeof cns !== "string") {
            // an object w/ flag values
            attrs.className = Object.keys(cns)
                .filter(k => cns[k])
                .map(k => camel2kebab(k))
                .join(" ");
        }
    }
    for (const k in attrs) {
        if (attrs[k] == null) delete attrs[k];
    }
    if (moreKids.length) {
        if (children == null) {
            children = moreKids;
        } else {
            if (!(children instanceof Array)) {
                children = [children];
            }
            children = children.concat(moreKids)
        }
    }
    const attrString = Object.keys(attrs)
        .map(k => ` ${k === "className" ? "class" : k}="${attrs[k]}"`)
        .join('');
    const kidString = children && children.join
        ? children.filter(IDENTITY) .join("")
        : children || '';
    return `<${name}${attrString}>${kidString}</${name}>`;
};
const numComp = (a, b) => {
    if (isActualNumber(a)) return isActualNumber(b) ? a - b : 1;
    if (isActualNumber(b)) return -1;
    return 0;
};
const strComp = (a, b) => a < b ? -1 : a > b ? 1 : 0;
const revComp = comp => (a, b) => comp(b, a);
const formatHsl = (h, s, l) =>
    `hsl(${formatNumber(h, 2)},${formatNumber(s, 2)}%,${formatNumber(l, 2)}%)`;
const drawLineChart = (series, options) => {
    const opts = {
        width: 200,
        height: 75,
        stroke: 3,
        title: null,
        dates: null,
        gridlines: true,
        ...options,
    };
    const margins = {top: opts.stroke / 2, left: opts.stroke / 2, right: opts.stroke / 2, bottom: opts.stroke / 2};
    let [ymin, ymax] = series.reduce(([min, max], s) => [
        s.values.reduce((a, b) => Math.min(a, b), min),
        s.values.reduce((a, b) => Math.max(a, b), max),
    ], [999999999, -999999999])
    let gridpoints;
    if (opts.gridlines) {
        margins.top += 10;
        margins.left += 10;
        margins.bottom += 10;
        // char width...
        margins.right += 10 * Math.ceil(Math.log10(ymax));
        gridpoints = [];
        let v, d;
        for (v = ymin, d = Math.max(1, Math.round((ymax - ymin) / (opts.height / 50))); v < ymax; v += d) {
            gridpoints.push(v);
        }
        gridpoints.push(v);
        ymax = v;
    }
    if (opts.dates) {
        margins.bottom += 20;
    }
    const chartHeight = opts.height - margins.top - margins.bottom;
    const dy = chartHeight / (ymax - ymin);
    const v2y = v => margins.top + chartHeight - (v - ymin) * dy;
    const chartWidth = opts.width - margins.left - margins.right;
    const len = series[0].values.length
    const dx = chartWidth / (len - 1)
    const i2x = i => margins.left + i * dx;
    return el(
        'svg',
        {
            viewBox: `0 0 ${opts.width} ${opts.height}`,
        },
        opts.gridlines && el('g', {},
            gridpoints.map((v, i) => el('line', {
                x1: margins.left,
                y1: v2y(v),
                x2: margins.left + chartWidth,
                y2: v2y(v),
                stroke: i % 2 === 0 ? "#ccc" : "#ddd",
                'stroke-width': "0.5px",
                'vector-effect': "non-scaling-stroke",
            })),
            gridpoints
                .filter((v, i) => i % 2 === 0)
                .map(v => el('text', {
                    fill: "#666",
                    'font-size': "14px",
                    x: margins.left + chartWidth + 2,
                    y: v2y(v) + 5,
                }, v)),
        ),
        opts.dates && el('g', {},
            opts.dates
                .map((d, i) => [d, i])
                .filter(([d]) => d.getDate() === 1 || d.getDay() === 0)
                .flatMap(([d, i]) => [
                    el('line', {
                        x1: i2x(i),
                        y1: margins.top,
                        x2: i2x(i),
                        y2: margins.top + chartHeight + 15,
                        stroke: d.getDate() === 1 ? "#ccc" : "#ddd",
                        'stroke-width': d.getDate() === 1 ? "1px" : "0.5px",
                        'vector-effect': "non-scaling-stroke",
                    }),
                    d.getDate() === 1 && el('text', {
                        fill: "#666",
                        'font-size': "12px",
                        x: i2x(i) + 2,
                        y: margins.top + chartHeight + 13,
                    }, formatDate(d)),
                    d.getDay() === 0 && d.getDate() > 5 && d.getDate() < 27 && el('text', {
                        fill: "#888",
                        'font-size': "12px",
                        x: i2x(i) + 2,
                        y: margins.top + chartHeight + 13,
                    }, d.getDate()),
                ])
        ),
        series.map(s => el('polyline', {
                points: s.values
                    .map((v, i) => i2x(i) + "," + v2y(v))
                    .join(" "),
                fill: "none",
                stroke: s.color || formatHsl(Math.random() * 360, 50, 50),
                onclick: s.onclick,
                cursor: s.onclick ? "pointer" : null,
                'stroke-width': (s.stroke || opts.stroke) + "px",
                'stroke-linejoin': "round",
                'stroke-linecap': "round",
            }, s.title && el('title', s.title)),
        ),
        opts.title && el('title', opts.title));
};
const $sidebar = $("#sidebar .content");
if ($sidebar) {
    $("#show-sidebar")
        .addEventListener("click", () => setState({sidebar: true}))
    $("#hide-sidebar")
        .addEventListener("click", () => setState({sidebar: false}))
    $("#reset-to-defaults").addEventListener("click", () => {
        window.localStorage.setItem(LS_KEY, JSON.stringify({
            sidebar: true,
        }));
        window.location.reload();
    })
}
fetch("data/last-update.txt")
    .then(r => r.text())
    .then(ld => {
        const d = parseDate(ld);
        window.lastUpdate = d;
        $("#navbar").innerHTML += el(
            "span",
            {className: "updated-at"},
            "Updated: " + formatDate(d),
        );
        if (d < Date.now() - 3 * 86400 * 1000) {
            document.body.classList.add("stale-data");
            document.body.style.setProperty("--body-warning-hue", Date.now() / 1000 % 86400 / 86400 * 360);
        }
    });