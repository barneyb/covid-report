function _togglerBuilder(key) {
    return idx => {
        setState(s => {
            const next = (s[key] || []).slice();
            const i = next.indexOf(idx);
            if (i < 0) {
                next.push(idx);
            } else {
                next.splice(i, 1);
            }
            return {
                [key]: next,
            };
        });
    };
}

function _pickCtrlBuilder(type) {
    return (label, checked, attrs, desc) => {
        if (checked) {
            attrs.checked = "checked";
        }
        return el('label', [
            el('input', {
                ...attrs,
                type,
            }),
            label,
            desc && el('div', {className: "desc"}, desc),
        ]);
    };
}

function getDataSegments(block, keys, segmentTransform = IDENTITY) {
    const segments = block.segments.map(segmentTransform);
    addFlags(segments)
    segments.sort(blockComp);
    const spc = 30;
    segments.forEach((s, i) =>
        s.hue = s.is_total ? 0 : spc + (i / segments.length) * (360 - 2*spc));
    const total = {
        ...block,
        is_total: true,
        hue: 0,
    }
    delete total.segments;
    for (const k of keys) {
        total[k] = aggArrayKey(segments, k);
    }
    segments.push(total);
    removeLeadingZeros(segments, total, keys);
    return segments;
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

function removeLeadingZeros(segments, total, keys) {
    const lastZero = keys.reduce((lz, k) =>
        Math.min(lz, total[k].lastIndexOf(0)), 99999);
    if (lastZero > 0) {
        for (const s of segments) {
            for (const k of keys) {
                s[k] = s[k].slice(lastZero);
            }
        }
    }
}

function buildDates(total, key, step=1) {
    return total[key].reduce(ds => {
        if (ds == null) {
            const d = window.lastUpdate
            d.setHours(12); // avoid having to deal with DST :)
            return [new Date(d.valueOf() - 86400 * 1000)];
        } else {
            ds.unshift(new Date(ds[0].valueOf() - step * 86400 * 1000));
            return ds;
        }
    }, null);
}

function addFlags(blocks) {
    blocks.forEach(b => {
        b.is_beb = b.id >= ID_BEB;
        b.is_us = Math.floor(b.id / 100000) === ID_US;
    });
}

function blockComp(a, b) {
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
}
