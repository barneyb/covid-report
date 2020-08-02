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
const parseQS = () => {
    const qs = location.search;
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
const el = (name, attrs, children) => {
    if (children == null && (attrs instanceof Array || typeof attrs === "string")) {
        children = attrs;
        attrs = undefined;
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
    return `<${name}${Object.keys(attrs)
        .map(k => ` ${k === "className" ? "class" : k}="${attrs[k]}"`)
        .join('')}>${children && children.join ? children.filter(IDENTITY)
        .join("") : children || ''}</${name}>`;
};
const numComp = (a, b) => {
    if (isActualNumber(a)) return isActualNumber(b) ? a - b : 1;
    if (isActualNumber(b)) return -1;
    return 0;
};
const strComp = (a, b) => a < b ? -1 : a > b ? 1 : 0;
const revComp = comp => (a, b) => comp(b, a);
const sidebar = $("#sidebar .content");
if (sidebar) {
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