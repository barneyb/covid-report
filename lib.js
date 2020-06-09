const $ = document.querySelector.bind(document);
const HunThou = 100000;
const fTrue = () => true;
const IDENTITY = v => v;
const isNum = v =>
    typeof v === "number" || v instanceof Number;
const isActualNumber = v =>
    !isNaN(v) && isFinite(v)
const formatDate = ld => {
    const ps = ld.split("-")
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
const formatPercent = (v, places = 1) => {
    if (!isActualNumber(v)) return '';
    return formatNumber(v * 100, places) + "%"
};
const formatDeathRate = v => formatNumber(v, 1)
const formatDeathRateSegment = v => formatNumber(v, 2)
const Delta = "&#x1D6AB;"
const tag = (el, c, attrs) =>
    `<${el}${Object.keys(attrs || {})
        .map(k => ` ${k === "className" ? "class" : k}="${attrs[k]}"`)
        .join('')}>${c && c.join ? c.filter(IDENTITY)
        .join("") : c || ''}</${el}>`;
const numComp = (a, b) => {
    if (isActualNumber(a)) return isActualNumber(b) ? a - b : 1;
    if (isActualNumber(b)) return -1;
    return 0;
};
const strComp = (a, b) => a < b ? -1 : a > b ? 1 : 0;
const revComp = sort => (a, b) => sort(b, a);
