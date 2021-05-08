LS_KEY = "covid-query";
const nextId = (() => {
    let id_seq = 0;
    return prefix => prefix + (++id_seq);
})();
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
const unparseDate = d =>
    `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
const formatDate = ld => {
    if (ld == null) return "";
    return (typeof ld === "string" ? parseDate(ld) : ld)
        .toLocaleDateString("en-US", {
            month: "short",
            day: "numeric",
        })
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
const parseQS = qs => {
    if (!qs || qs === "?") return {};
    return qs.substr(1)
        .split("&")
        .map(p => p.split("="))
        .map(p => [decodeURIComponent(p.shift()), decodeURIComponent(p.join("="))])
        .reduce((r, [k, v]) => {
            if (r.hasOwnProperty(k)) {
                if (!(r[k] instanceof Array)) {
                    r[k] = [r[k]];
                }
                r[k].push(v);
            } else {
                r[k] = v;
            }
            return r;
        }, {});
};
const formatQS = data => {
    if (!data) return "";
    const qs = "?" + Object.keys(data)
        .sort()
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
const afterQueryStringSet = qs => {
    const bals = document.querySelectorAll("#navbar > a[href]");
    for (const l of bals) {
        l.setAttribute(
            "href",
            l.getAttribute("href").split("?")[0] + qs,
        );
    }
    window.localStorage.setItem(LS_KEY, qs);
}
const pushQS = (dataOrQS, replace) => {
    let qs, data;
    if (typeof dataOrQS === "string") {
        qs = dataOrQS;
        data = parseQS(qs);
    } else {
        data = dataOrQS;
        qs = formatQS(data);
    }
    if (location.search !== qs) {
        if (replace) history.replaceState(data, '', qs);
        else history.pushState(data, '', qs);
        afterQueryStringSet(qs)
    }
}
const pullQS = () => {
    let qs = location.search;
    if (!qs || qs === "?") {
        // check local storage too
        qs = window.localStorage.getItem(LS_KEY);
    }
    return parseQS(qs);
};
const INITIAL_QS = pullQS();
afterQueryStringSet(formatQS(INITIAL_QS));
const useState = (init, updated) => {
    let state = {...init};
    let __setting = false;
    return function setState(s, updateComplete) {
        if (__setting) throw new Error("Reentrant setState!");
        __setting = true;
        const prev = state;
        try {
            if (typeof s === "function") {
                s = s(prev);
            }
            if (s == null) return;
            state = {
                ...prev,
                ...s,
            };
            updated && updated(state, prev);
        } finally {
            __setting = false;
        }
        updateComplete && updateComplete(state, prev);
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
    for (const k in attrs) {
        if (attrs[k] == null) delete attrs[k];
    }
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
const arrayMax = arr => {
    // arr.reduce(Math.max, 0) doesn't work for some reason?
    return arr.reduce((a, b) => Math.max(a, b), 0);
};
/**
 * Formats an HSL color (including optional alpha) to a CSS literal.
 *
 * @param h hue angle `[0, 360)`
 * @param s saturation percentage `[0, 100)` (not `[0, 1)`!)
 * @param l lightness percentage `[0, 100)` (not `[0, 1)`!)
 * @param a alpha value `[0, 1)`, default `1`
 * @returns {string} the CSS literal for the color components
 */
const formatHsl = (h, s, l, a=1) =>
    `hsl(${formatNumber(h, 2)},${formatNumber(s, 2)}%,${formatNumber(l, 2)}%,${formatNumber(a, 2)})`;
const rangeToIndices = (coll, min, max) => {
    let start = coll.findIndex(d => d >= min);
    if (start < 0) start = 0;
    let end = coll.findIndex(d => d > max);
    if (end < 0) end = coll.length;
    return [start, end];
}
// The drawn slider MUST be synchronously placed into the DOM!
const drawDateRangeSlider = (dates, startDate, endDate, options) => {
    const opts = {
        width: 200,
        height: 75,
        series: null,
        onMotion: null,
        onCommit: (s, e) =>
            console.log(formatDate(s), formatDate(e)),
        ...options,
    };
    const pad = 8;
    const width = opts.width - 2 * pad - 1;
    const dx = (width - 1) / (dates.length - 1);
    let [startIdx, endIdx] = rangeToIndices(dates, startDate, endDate);
    endIdx -= 1; // the math is easier w/ a closed range
    const buildDoer = key =>(si, ei) =>
        opts[key] && opts[key](dates[si], dates[ei]);
    const doMotion = buildDoer("onMotion");
    const doCommit = buildDoer("onCommit");
    const idxToPos = i => i * dx + pad;
    const posToIdx = p => Math.round((p + 1 - pad) / dx);
    const maskWidth = opts.width - 2;
    const idxToPos_start = i => idxToPos(i) - maskWidth;
    const posToIdx_start = p => posToIdx(p + maskWidth);
    const doThumb = ($el, idx, buildIdxRange, idxToPos, posToIdx, doMotion, doCommit) => {
        if (!$el) return; // render race
        let pos = idxToPos(idx)
        $el.querySelector(".thumb").addEventListener("pointerdown", e => {
            e.preventDefault();
            const motionOrigin = e.clientX;
            const maskOrigin = idxToPos(idx);
            const [minPos, maxPos] = buildIdxRange().map(idxToPos);
            const onPointerMove = e => {
                e.preventDefault();
                pos = maskOrigin + (e.clientX - motionOrigin);
                if (pos < minPos) pos = minPos;
                if (pos > maxPos) pos = maxPos;
                doMotion(posToIdx(pos));
                $el.style.left = pos + "px";
            }
            const onPointerUp = e => {
                e.preventDefault();
                idx = posToIdx(pos);
                pos = idxToPos(idx);
                $el.style.left = pos + "px";
                doCommit(idx);
                document.removeEventListener("pointerup", onPointerUp);
                document.removeEventListener("pointermove", onPointerMove);
            }
            document.addEventListener("pointerup", onPointerUp);
            document.addEventListener("pointermove", onPointerMove);
        });
    };
    const startId = nextId("range-mask-start");
    const endId = nextId("range-mask-start");
    setTimeout(() => { // can't set 'em up until they're in the DOM
        doThumb($("#" + startId),
            startIdx,
            () => [0, endIdx - 1],
            idxToPos_start,
            posToIdx_start,
            i => doMotion(i, endIdx),
            i => doCommit(startIdx = i, endIdx),
        );
        doThumb($("#" + endId),
            endIdx,
            () => [startIdx + 1, dates.length - 1],
            idxToPos,
            posToIdx,
            i => doMotion(startIdx, i),
            i => doCommit(startIdx, endIdx = i),
        );
    });
    return el("div", {
        className: "range-track",
        style: {
            padding: `0 ${pad}px`,
        }
    }, [
        opts.series && drawLineChart([opts.series], {
            width,
            height: opts.height,
            stroke: 1,
            gridlines: false,
            dates,
            dateOverlay: true,
        }),
        el('div', {
            id: startId,
            className: "range-mask start",
                style: `left:${idxToPos_start(startIdx)}px`,
            },
            el("div", {className: "thumb"}, "||"),
        ),
        el('div', {
            id: endId,
            className: "range-mask end",
                style: `left:${idxToPos(endIdx)}px`,
            },
            el("div", {className: "thumb"}, "||"),
        ),
    ]);
};
const line = (x, y, dx=0, dy=0, colorOrStyle="#666", width="0.5px") => {
    let attrs = {
        x1: x,
        y1: y,
        x2: x + dx,
        y2: y + dy,
        'vector-effect': "non-scaling-stroke",
    };
    if (typeof colorOrStyle === "string" && colorOrStyle.indexOf(":") < 0) {
        attrs.stroke = colorOrStyle;
        attrs['stroke-width'] = width;
    } else {
        attrs.style = colorOrStyle;
    }
    return el('line', attrs);
}
const drawLineChart = (series, options) => {
    const opts = {
        width: 200,
        height: 75,
        range: null,
        stroke: 3,
        title: null,
        dates: null,
        dateOverlay: false,
        gridlines: true,
        detailOnHover: false,
        ...options,
    };
    series = series.map(s => ({
        // 'values' is required
        color: formatHsl(Math.random() * 360, 50, 50),
        onclick: null,
        stroke: opts.stroke,
        title: null,
        detailOnHover: opts.detailOnHover && !!s.title,
        ...s,
    }));
    opts.detailOnHover = series.some(s => s.detailOnHover);
    const margins = {top: opts.stroke / 2, left: opts.stroke / 2, right: opts.stroke / 2, bottom: opts.stroke / 2};
    let [ymin, ymax] = series.reduce(([min, max], s) => [
        s.values.reduce((a, b) => Math.min(a, b), min),
        s.values.reduce((a, b) => Math.max(a, b), max),
    ], [999999999, -999999999])
    if (ymin === ymax) ymax += 1; // protect against flat series
    if (options.range) {
        ymax = Math.max(ymax, ymin + options.range);
    }
    let gridpoints, gridLabelPlaces;
    if (opts.gridlines) {
        margins.top += 10;
        margins.left += 10;
        margins.bottom += 10;
        let d = parseFloat(new Intl.NumberFormat('en-US', {
            maximumSignificantDigits: 1
        })
            .format((ymax - ymin) / (opts.height / 50))
            .replace(/,/g, ''));
        if (d <= 0) throw new Error("what?!");
        gridLabelPlaces = Math.max(0, -Math.floor(Math.log10(d)));
        gridpoints = [];
        let v = Math.floor(ymin / d) * d;
        ymin = v;
        for (; v < ymax; v += d) {
            gridpoints.push(v);
        }
        margins.right += 5 + 7 * formatNumber(v, gridLabelPlaces).length;
        gridpoints.push(v);
        ymax = v;
    }
    let today;
    if (opts.dates) {
        if (!opts.dateOverlay) margins.bottom += opts.gridlines ? 15 : 20;
        today = opts.dates[opts.dates.length - 1];
    }
    const chartHeight = opts.height - margins.top - margins.bottom;
    const dy = chartHeight / (ymax - ymin);
    const v2y = v => margins.top + chartHeight - (v - ymin) * dy;
    const y2v = y => (y - margins.top - chartHeight) / -dy + ymin;
    const chartWidth = opts.width - margins.left - margins.right;
    const len = series[0].values.length
    const dx = chartWidth / (len - 1)
    const i2x = i => margins.left + i * dx;
    const x2i = x => Math.round((x - margins.left) / dx);
    series.forEach(s =>
        s.values = s.values.map((v, i) => [v, i2x(i), v2y(v)]));
    const isRoomBeforeEOM = d => {
        const eom = new Date(d.getFullYear(), d.getMonth() + 1, 0);
        const cutoff = Math.min(today, eom);
        return (cutoff - d) / 86400 / 1000 * dx > 6 * 7;
    };
    const doMonthLabel = (d, ignored) => {
        if (d.getDate() !== 1) return false;
        // is there room before the right edge?
        return isRoomBeforeEOM(d);
    };
    const doDateLabel = (d, ignored) => {
        if (d.getDate() === 1) return false;
        if ((d.getDate() - 1) * dx < 2 + 5 * 7 + 2) return false;
        return isRoomBeforeEOM(d);
    };
    const datesToDraw = opts.dates && opts.dates
        .map((d, i) => [d, i])
        .filter(([d]) => d.getDate() === 1 || d.getDay() === 0 || dx > 6 * 7)
        .map(([d, i]) => {
            const x = i2x(i)
            return [d, x, d.getDate() === 1, doDateLabel(d, x)];
        });
    const domId = nextId("chart")
    if (opts.detailOnHover) {
        let $detail
        const drawDetail = (di, dv) => {
            if ($detail == null)
                $detail = document.getElementById(domId + "-detail");
            if ($detail == null) return;
            const lines = [];
            if (opts.dates) {
                lines.push({
                    text: formatDate(opts.dates[di]),
                    bold: true,
                })
            }
            series.filter(s => s.detailOnHover)
                .map(s => {
                    const v = s.values[di][0]
                    return {
                        v,
                        text: `${s.title} (${formatNumber(v, 1)})`,
                        color: s.color,
                    }
                })
                .sort((a, b) => numComp(b.v, a.v))
                .forEach(s => lines.push(s));
            const width = lines.reduce((m, l) => Math.max(m, l.text.length), 0) * 7 + 10
            const x = i2x(di)
            const y = v2y(dv)
            $detail.innerHTML = line(x, margins.top, 0, chartHeight, "#666", "1px") +
                line(margins.left, y, chartWidth, 0, {
                    stroke: "#999",
                    strokeWidth: "1px",
                    strokeDasharray: "6 4",
                }) +
                el('g', {
                    transform: `translate(${x + width > chartWidth ? x - width : x}, ${margins.top})`,
                },
                el('rect', {
                    x: 0,
                    y: 0,
                    width,
                    height: lines.length * 14 + 4,
                    fill: "white",
                    stroke: "#ccc",
                }),
                lines.map((l, i) => {
                    if (!l.color) return;
                    return el('circle', {
                        cx: 6,
                        cy: i * 14 + 9,
                        r: 4,
                        fill: l.color,
                    })
                }),
                lines.map((l, i) => el('text', {
                    x: l.color ? 12 : 2,
                    y: (i + 1) * 14,
                    'font-weight': l.bold ? "bold" : "normal",
                }, l.text)));
        }
        const onMouseMove = e => {
            const root = document.getElementById(domId);
            if (!root) {
                document.removeEventListener("mousemove", onMouseMove);
                return;
            }
            if (!root.contains(e.target)) return;
            const i = x2i(e.offsetX);
            if (i < 0 || i >= len) return;
            const v = y2v(e.offsetY);
            if (v < ymin || v > ymax) return;
            drawDetail(i, v)
        };
        document.addEventListener("mousemove", onMouseMove);
        setTimeout(() => drawDetail(len - 1, ymax));
    }
    return el(
        'svg',
        {
            id: domId,
            viewBox: `0 0 ${opts.width} ${opts.height}`,
            'font-size': "12px", // this is a 12x7 character. By fiat.
        },
        opts.gridlines ? el('g', {},
            // right border
            line(margins.left + chartWidth, margins.top, 0, chartHeight),
            // horizontal lines
            gridpoints.map((v, i) =>
                line(margins.left, v2y(v), chartWidth, 0, i % 2 === 0 ? "#bbb" : "#ddd")),
            // y-axis labels
            gridpoints
                .filter((v, i) => i % 2 === 0)
                .map(v => el('text', {
                    fill: "#333",
                    x: margins.left + chartWidth + 2,
                    y: v2y(v) + 3.5,
                }, formatNumber(v, gridLabelPlaces))),
        ) : el('g', line(margins.left, v2y(ymin), chartWidth, 0, "#ccc")),
        opts.dates && el('g', {},
            // vertical lines
            datesToDraw.map(([ignored, x, mo, dt]) =>
                line(x, margins.top, 0, chartHeight + (mo || dt ? 15 : 0), mo ? "#666" : "#ddd")
            ),
            // date/month labels
            datesToDraw.flatMap(([d, x, mo, dt], i) => [
                (i === 0 || doMonthLabel(d, x)) && el('text', {
                    fill: "#333",
                    x: x + 2,
                    y: margins.top + chartHeight + (opts.dateOverlay ? -3 : 13),
                }, formatDate(d)),
                i !== 0 && dt && el('text', {
                    fill: "#888",
                    x: x + 2,
                    y: margins.top + chartHeight + (opts.dateOverlay ? -3 : 13),
                }, d.getDate()),
            ])
        ),
        opts.detailOnHover && el('g', {
            id: domId + "-detail",
        }),
        series.map(s => el('polyline', {
                points: s.values
                    .map(([ignored, x, y]) => x + "," + y)
                    .join(" "),
                fill: "none",
                stroke: s.color,
                onclick: s.onclick,
                cursor: s.onclick ? "pointer" : null,
                'stroke-width': s.stroke + "px",
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
    $("#reset-to-defaults")
        .addEventListener("click", () => location = "?sidebar")
    if (location.search === "?sidebar") document.body.classList.add("sidebar");
}
fetch("data/last-update.txt")
    .then(r => r.text())
    .then(ld => {
        const d = parseDate(ld);
        window.lastUpdate = d;
        const $navbar = $("#navbar")
        $navbar.innerHTML += el(
            "span",
            {className: "updated-at"},
            "Updated: " + formatDate(d),
        );
        if (d < Date.now() - 3 * 86400 * 1000) {
            document.body.classList.add("stale-data");
            document.body.style.setProperty("--body-warning-hue", Date.now() / 1000 % 86400 / 86400 * 360);
        }
        if (location.href.indexOf("/target/") > 0) {
            $navbar.style.setProperty("background-color", "hsl(var(--body-warning-hue), 100%, 40%)");
        }
    });
