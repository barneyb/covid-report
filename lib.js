const $ = document.querySelector.bind(document);
const HunThou = 100000;
const fTrue = () => true;
const IDENTITY = v => v;
const isNum = v =>
    typeof v === "number" || v instanceof Number;
const isActualNumber = v =>
    !isNaN(v) && isFinite(v)
const formatDate = ld => {
    const ps = ld.trim()
        .split("-")
        .map(p => parseInt(p, 10));
    return new Date(ps[0], ps[1] - 1, ps[2])
        .toLocaleDateString("en-US", {
            month: "short",
            day: "numeric",
        });
};
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
const tag = (el, c, attrs) =>
    `<${el}${Object.keys(attrs || {})
        .map(k => ` ${k === "className" ? "class" : k}="${attrs[k]}"`)
        .join('')}>${c && c.join ? c.filter(IDENTITY)
        .join("") : c || ''}</${el}>`;
const el = function(name, attrs, children) {
    if (children == null && (attrs instanceof Array || typeof attrs === "string")) {
        children = attrs;
        attrs = undefined;
    }
    return tag(name, children, attrs)
};
const numComp = (a, b) => {
    if (isActualNumber(a)) return isActualNumber(b) ? a - b : 1;
    if (isActualNumber(b)) return -1;
    return 0;
};
const strComp = (a, b) => a < b ? -1 : a > b ? 1 : 0;
const revComp = sort => (a, b) => sort(b, a);
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
    .then(d => $("#navbar").innerHTML += tag("span", "Updated: " + formatDate(d), {className: "updated-at"}))