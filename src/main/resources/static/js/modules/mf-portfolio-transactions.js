// mf-portfolio-transactions-script.js - review all investments (MF.md 8 / 19)
let txnRows = [];
let headerMap = {};

function esc(s) {
    var A = String.fromCharCode(38);
    return String(s == null ? '' : s)
        .replace(/&/g, A + 'amp;')
        .replace(/</g, A + 'lt;')
        .replace(/>/g, A + 'gt;')
        .replace(/"/g, A + 'quot;')
        .replace(/'/g, A + '#39;');
}

function money(v) {
    return (Number(v) || 0).toLocaleString('en-IN',
        {minimumFractionDigits: 2, maximumFractionDigits: 2});
}

async function getJson(url, init) {
    const res = await fetch(url, init);
    const text = await res.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch (e) { body = text; }
    if (!res.ok) {
        throw new Error((body && body.message) ? body.message : (text || ('HTTP ' + res.status)));
    }
    return body;
}

function qs() {
    const p = new URLSearchParams();
    const map = {
        from: document.getElementById('fromDate').value,
        to: document.getElementById('toDate').value,
        amc: document.getElementById('amcFilter').value,
        category: document.getElementById('categoryFilter').value,
        q: document.getElementById('searchBox').value.trim(),
        headerId: window.headerIdFilter || ''
    };
    Object.entries(map).forEach(([k, v]) => { if (v) p.set(k, v); });
    const s = p.toString();
    return s ? '?' + s : '';
}

function resetFilters() {
    ['fromDate', 'toDate'].forEach(id => document.getElementById(id).value = '');
    ['amcFilter', 'categoryFilter', 'searchBox'].forEach(id => document.getElementById(id).value = '');
    window.headerIdFilter = '';
    document.getElementById('holdingNote').style.display = 'none';
    load();
}

function renderSummary(summary) {
    const s = summary || {};
    document.getElementById('summaryCards').innerHTML = `
        <div class="card"><div class="label">Transactions</div>
            <div class="value">${s.txnCount || 0}</div></div>
        <div class="card"><div class="label">Net Invested in Txns (₹)</div>
            <div class="value">${money(s.txnInvested)}</div></div>
        <div class="card blue"><div class="label">Holding Value (₹)</div>
            <div class="value">${money(s.holdingValue)}</div></div>
        <div class="card ${((s.holdingReturns || 0) >= 0 ? 'green' : 'red')}">
            <div class="label">Holding Returns (₹)</div>
            <div class="value ${((s.holdingReturns || 0) >= 0) ? 'pos' : 'neg'}">
                ${money(s.holdingReturns)}</div></div>
        <div class="card"><div class="label">Holding Units</div>
            <div class="value">${(Number(s.holdingUnits) || 0).toFixed(4)}</div></div>`;
}

function renderTable() {
    const tbody = document.querySelector('#txnTable tbody');
    tbody.innerHTML = txnRows.map(t => {
        const h = headerMap[String(t.headerId)] || {};
        const s = h.scheme || {};
        const sign = (Number(t.amount) || 0) < 0 ? 'color:#b02a37;' : '';
        return `<tr>
            <td>${esc(t.txnDate)}</td>
            <td class="left">${esc(s.schemeName)}</td>
            <td class="left">${esc(s.amcName)}</td>
            <td>${esc(s.category)}</td>
            <td>${esc(h.folioNo)}</td>
            <td>${esc(t.txnType)}</td>
            <td class="left">${esc(t.description)}</td>
            <td class="amt">${(Number(t.units) || 0).toFixed(4)}</td>
            <td class="amt">${t.nav ? money(t.nav) : '-'}</td>
            <td class="amt" style="${sign}">${money(t.amount)}</td>
            <td>${esc(t.source)}</td>
        </tr>`;
    }).join('');
    document.getElementById('emptyNote').style.display = txnRows.length ? 'none' : 'block';
}

function load() {
    return getJson('/api/mf/transactions' + qs()).then(data => {
        txnRows = data.rows || [];
        headerMap = data.headers || {};
        renderSummary(data.summary);
        renderTable();
    }).catch(err => alert('Error loading transactions: ' + err.message));
}

function loadFilters() {
    return getJson('/api/mf/filters').then(f => {
        (f.amcs || []).forEach(v => {
            const o = document.createElement('option');
            o.value = v; o.textContent = v;
            document.getElementById('amcFilter').appendChild(o);
        });
        (f.categories || []).forEach(v => {
            const o = document.createElement('option');
            o.value = v; o.textContent = v;
            document.getElementById('categoryFilter').appendChild(o);
        });
    }).catch(() => {});
}

// deep link ?headerId=... - show which holding is filtered
const params = new URLSearchParams(window.location.search);
window.headerIdFilter = params.get('headerId') || '';
if (window.headerIdFilter) {
    fetch('/api/mf/headers/' + window.headerIdFilter).then(r => r.ok ? r.json() : null)
        .then(d => {
            if (!d) return;
            const s = d.header.scheme || {};
            const note = document.getElementById('holdingNote');
            note.style.display = 'flex';
            note.innerHTML = 'Filtered to: <b>' + esc(s.schemeName) + '</b> (folio ' +
                esc(d.header.folioNo) + ') ' +
                '<button class="small" style="margin-left:10px;" onclick="clearHold()">Clear</button>';
        }).catch(() => {});
}

function clearHold() {
    window.headerIdFilter = '';
    document.getElementById('holdingNote').style.display = 'none';
    load();
}

document.getElementById('searchBox').addEventListener('keydown', e => {
    if (e.key === 'Enter') load();
});

Promise.all([loadFilters()]).then(load);
