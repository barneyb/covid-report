google.charts.load('current', {'packages': ['annotationchart']});
function init(dataUrl) {
    let annChart;
    const LS_KEY = "covid-rates-state";
    let state = {
        hotSeries: new Set([
            "Worldwide",
            "US",
            "Oregon, US",
            "Washington, Oregon, US",
            "California, US",
        ]),
        expanded: new Set([
            "US",
            "Oregon, US",
        ]),
    };
    try {
        cache = window.localStorage.getItem(LS_KEY);
        if (cache) {
            state = JSON.parse(cache, (k, v) => {
                if (v instanceof Array && (k === "hotSeries" || k === "expanded")) {
                    return new Set(v);
                }
                return v;
            });
        }
    } catch (e) {}
    const setState = s => {
        if (typeof s === "function") {
            s = s(state);
        }
        state = {
            ...state,
            ...s,
        };
        window.localStorage.setItem(LS_KEY, JSON.stringify(state, (k, v) =>
            v instanceof Set ? Array.from(v) : v));
        render(state);
    }
    const buildToggler = sn => key =>
        setState(s => {
            const set = new Set(s[sn]);
            if (set.has(key)) {
                set.delete(key);
            } else {
                set.add(key);
            }
            return {
                [sn]: set,
            };
        });
    window.toggleSeries = buildToggler("hotSeries");
    window.toggleExpanded = buildToggler("expanded");
    const render = state => {
        google.charts.setOnLoadCallback(() => {
            setTimeout(() => {
                annChart.showDataColumns(state.headers.map((_, i) => i));
                annChart.hideDataColumns(state.headers
                    .map((h, i) => state.hotSeries.has(h) ? null : i)
                    .filter(it => it != null));
            });
        });
        drawPicker(state);
    }
    Promise.all([
        fetch(dataUrl)
            .then(r => r.text())
            .then(r => r.trim()
                .split("\n")
                .map(r => r.split("|"))),
        fetch("./events.txt")
            .then(r => r.text())
            .then(r => r.trim()
                .split("\n")
                .map(r => r.split("|"))),
    ])
        .then(([lines, events]) => {
            const headers = lines.shift()
            headers.shift(); // cull the date column
            setState({
                headers,
            });
            google.charts.setOnLoadCallback(() =>
                drawChart(headers, lines, events))
        });

    function drawChart(headers, lines, events) {
        const table = new google.visualization.DataTable();
        // a single two-level index
        const byJnD = events.slice(1)
            .reduce((agg, [dt, jur, evt, desc]) => {
                if (!agg.has(jur)) {
                    agg.set(jur, new Map());
                }
                const di = agg.get(jur);
                if (!di.get(dt)) {
                    di.set(dt, []);
                }
                di.get(dt).push(evt, desc);
                return agg;
            }, new Map());

        table.addColumn('date', "Date");

        headers
            .forEach(h => {
                table.addColumn('number', h);
                if (byJnD.has(h)) {
                    table.addColumn('string', 'Event');
                    table.addColumn('string', 'Description');
                }
            });

        table.addRows(lines
            .map(r => {
                const dt = r[0];
                const [m, d] = dt
                    .split("/")
                    .map(p => parseInt(p, 10));
                r = r.slice(1)
                    .flatMap((n, i) => {
                        const p = [parseFloat(n)]
                        const di = byJnD.get(headers[i]);
                        if (di != null) {
                            if (di.has(dt)) {
                                const [evt, desc] = di.get(dt);
                                p.push(evt);
                                p.push(desc);
                            } else {
                                p.push(null);
                                p.push(null);
                            }
                        }
                        return p;
                    });
                r.unshift(new Date(2020, m - 1, d));
                return r;
            }));

        var options = {
            dateFormat: "MMM d",
            displayZoomButtons: false,
        };

        annChart = new google.visualization.AnnotationChart(
            document.getElementById('ann_chart'));
        annChart.draw(table, options);
        annChart.hideDataColumns(headers.slice(1).map((_, i) => i));
        window.addEventListener(
            "resize",
            () => annChart.draw(table, options),
        );
        annChart.setVisibleChartRange(new Date(2020, 3 - 1, 15));
    }

    function drawPicker({headers, hotSeries, expanded}) {
        const add = (tree, key, parts) => {
            const name = parts[0];
            if (parts.length === 1) {
                tree[parts[0]] = {
                    name,
                    key,
                };
                return tree;
            }
            if (!tree.hasOwnProperty(name)) {
                console.warn("Missing '" + name + "' level for " + key + "?!");
                return tree;
            }
            const it = tree[name];
            if (it.children == null) {
                it.children = {};
            }
            add(it.children, key, parts.slice(1))
            it.childCount = Object.keys(it.children).length;
            return tree;
        };
        const tree = headers
            .reduce((items, key) =>
                add(items, key, key.split(",")
                    .map(s => s.trim())
                    .reverse()), {});
        const drawTree = (tree, depth = 0) =>
            Object.keys(tree)
                .map(k => tree[k])
                .map(it => drawItem(it, depth))
                .join("");
        const drawItem = ({name, key, children, childCount}, depth) => {
            const exp = expanded.has(key);
            const content = '<div style="display:flex;align-content:start;justify-content:start;user-select:none;margin-left:' + (1.3 * depth) + 'em">' +
                '<div style="font-family:monospace;cursor:pointer;margin-right:0.5em" onclick="toggleExpanded(\'' + key + '\')">' + (childCount ? exp ? '&#9660;' : '&#9658;' : '&nbsp;') + '</div>' +
                '<label style="display:block">' +
                '<input type="checkbox"' + (hotSeries.has(key) ? ' checked' : '') + ' value="' + key + '" onclick="toggleSeries(\'' + key + '\')" /> ' + name +
                '</label>' +
                '</div>';
            if (childCount && exp) {
                return content + drawTree(children, depth + 1);
            } else {
                return content;
            }
        }
        document.getElementById('picker').innerHTML = '<div>' +
            '</div>' +
            drawTree(tree);
    }
}
