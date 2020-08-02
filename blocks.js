function getSeriesAndDates(block, keys, segmentTransform = IDENTITY) {
    const series = block.segments.map(segmentTransform);
    const total = {
        ...block,
        is_total: true,
    }
    delete total.segments;
    for (const k of keys) {
        total[k] = aggArrayKey(series, k);
    }
    series.push(total);
    removeLeadingZeros(series, total, keys);
    const dates = buildDates(total, keys[0]);
    return [series, dates];
}

function aggArrayKey(items, key) {
    const agg = items[0][key].map(() => 0)
    items.map(it => it[key]).forEach(v => {
        for (let i = agg.length - 1; i >= 0; i--) {
            agg[i] += v[i];
        }
    }, agg);
    return agg;
}

function removeLeadingZeros(series, total, keys) {
    const lastZero = keys.reduce((lz, k) =>
        Math.min(lz, total[k].lastIndexOf(0)), 99999);
    if (lastZero > 0) {
        for (const s of series) {
            for (const k of keys) {
                s[k] = s[k].slice(lastZero);
            }
        }
    }
}

function buildDates(total, key) {
    return total[key].reduce(ds => {
        if (ds == null) {
            const d = window.lastUpdate
            d.setHours(12); // avoid having to deal with DST :)
            return [new Date(d.valueOf() - 86400 * 1000)];
        } else {
            ds.unshift(new Date(ds[0].valueOf() - 7 * 86400 * 1000));
            return ds;
        }
    }, null);
}

fetch("data/blocks.json")
    .then(resp => resp.json())
    .then(blocks => {
        blocks.forEach(b => {
            b.is_beb = b.id >= ID_BEB;
            b.is_us = Math.floor(b.id / 100000) === ID_US;
        });
        blocks.sort((a, b) => {
            // beb's first
            if (a.is_beb !== b.is_beb) {
                return a.is_beb ? -1 : 1;
            }
            // non-US next
            if (a.is_us !== b.is_us) {
                return a.is_us ? 1 : -1;
            }
            // they're in the same bucket, so alphabetical
            return a.name.localeCompare(b.name);
        });
        setState({
            blocks,
        });
        const qs = parseQS();
        const idFromQS = parseInt(qs.id)
        if (!isNaN(idFromQS) && blocks.find(b => b.id === idFromQS)) {
            fetchTableData(idFromQS);
        } else {
            fetchTableData(ID_US);
        }
    });
