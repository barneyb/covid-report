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
        ...options,
    };
    const [min, max] = series.reduce(([min, max], s) => [
        s.values.reduce((a, b) => Math.min(a, b), min),
        s.values.reduce((a, b) => Math.max(a, b), max),
    ], [999999999, -999999999])
    const range = max - min;
    const len = series[0].values.length
    const dx = opts.width / (len - 1)
    return el(
        'svg',
        {
            viewBox: `${-opts.stroke} ${-opts.stroke} ${opts.width + 2 * opts.stroke} ${opts.height + 2 * opts.stroke}`,
        },
        series.map(s => el('polyline', {
                points: s.values
                    .map((d, i) => [
                        i * dx,
                        formatNumber(opts.height - (d - min) / range * opts.height, 2)
                    ])
                    .map(p => p.join(","))
                    .join(" "),
                fill: "none",
                stroke: s.color || formatHsl(Math.random() * 360, 50, 50),
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