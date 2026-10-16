// mf-portfolio-script.js - portfolio view with search + summary + detail (MF.md 17-19)
let rows = [];

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
        status: document.getElementById('statusFilter').value,
        q: document.getElementById('searchBox').value.trim()
    };
    Object.entries(map).forEach(([k, v]) => { if (v) p.set(k, v); });
    const s = p.toString();
    return s ? '?' + s : '';
}

function resetFilters() {
    ['fromDate', 'toDate'].forEach(id => document.getElementById(id).value = '');
    ['amcFilter', 'categoryFilter', 'statusFilter', 'searchBox']
        .forEach(id => document.getElementById(id).value = '');
    load();
}

// ----------------------------------------------------------------- summary
function renderSummary(summary) {
    const s = summary || {};
    document.getElementById('summaryCards').innerHTML = `
        <div class="card"><div class="label">Holdings</div>
            <div class="value">${s.count || 0}</div></div>
        <div class="card"><div class="label">Total Invested (₹)</div>
            <div class="value">${money(s.invested)}</div></div>
        <div class="card blue"><div class="label">Total MF Value (₹)</div>
            <div class="value">${money(s.current)}</div></div>
        <div class="card ${(s.returns || 0) >= 0 ? 'green' : 'red'}">
            <div class="label">Total Profit (₹)</div>
            <div class="value ${((s.returns || 0) >= 0) ? 'pos' : 'neg'}">${money(s.returns)}</div></div>
        <div class="card"><div class="label">Total Units</div>
            <div class="value">${(Number(s.units) || 0).toFixed(4)}</div></div>`;
}

// ------------------------------------------------------------------- table
function renderTable() {
    const tbody = document.querySelector('#headerTable tbody');
    tbody.innerHTML = rows.map(h => {
        const s = h.scheme || {};
        return `<tr>
            <td class="left">${esc(s.schemeName)}</td>
            <td class="left">${esc(s.amcName)}</td>
            <td>${esc(s.category)}</td>
            <td>${esc(h.folioNo)}</td>
            <td><span class="badge ${h.registrationType === 'SIP' ? 'badge-sip' : 'badge-onetime'}">
                ${esc(h.registrationType === 'SIP' ? 'SIP' : 'One-time')}</span></td>
            <td class="amt">${(Number(h.units) || 0).toFixed(4)}</td>
            <td class="amt">${money(h.investedValue)}</td>
            <td class="amt">${money(h.currentValue)}</td>
            <td class="amt" style="color:${(h.returns || 0) >= 0 ? '#0f5132' : '#b02a37'}">
                ${money(h.returns)}</td>
            <td><span class="badge badge-${(h.status || '').toLowerCase()}">${esc(h.status)}</span></td>
            <td class="left">
                <button class="small" onclick="openDetail(${h.id})">📄 Detail</button>
                <button class="small" onclick="txnsFor(${h.id})">📋 Txns</button>
            </td>
        </tr>`;
    }).join('');
    document.getElementById('emptyNote').style.display = rows.length ? 'none' : 'block';
}

// --------------------------------------------------------- detail drill-down
async function openDetail(id) {
    try {
        const d = await getJson('/api/mf/headers/' + id);
        const h = d.header;
        const s = h.scheme || {};
        const st = d.stats || {};
        document.getElementById('detailTitle').textContent =
            `Detail Report - ${s.schemeName} (folio ${h.folioNo || '-'})`;
        document.getElementById('detailCards').innerHTML = `
            <div class="card"><div class="label">AMC</div>
                <div class="value" style="font-size:14px;">${esc(s.amcName)}</div></div>
            <div class="card"><div class="label">Category</div>
                <div class="value" style="font-size:14px;">${esc(s.category)}</div></div>
            <div class="card"><div class="label">Units</div>
                <div class="value">${(Number(h.units) || 0).toFixed(4)}</div></div>
            <div class="card"><div class="label">Invested (₹)</div>
                <div class="value">${money(st.invested)}</div></div>
            <div class="card blue"><div class="label">Current (₹)</div>
                <div class="value">${money(st.current)}</div></div>
            <div class="card ${((st.returns || 0) >= 0 ? 'green' : 'red')}">
                <div class="label">Returns (₹)</div>
                <div class="value ${((st.returns || 0) >= 0) ? 'pos' : 'neg'}">${money(st.returns)}</div></div>
            <div class="card"><div class="label">Transactions</div>
                <div class="value">${st.txnCount || 0}</div></div>
            <div class="card"><div class="label">Redeemed (₹)</div>
                <div class="value">${money(st.redeemed)}</div></div>`;

        document.querySelector('#detailTable tbody').innerHTML =
            (d.transactions || []).map(t => `<tr>
                <td>${esc(t.txnDate)}</td>
                <td>${esc(t.txnType)}</td>
                <td class="left">${esc(t.description)}</td>
                <td class="amt">${(Number(t.units) || 0).toFixed(4)}</td>
                <td class="amt">${t.nav ? money(t.nav) : '-'}</td>
                <td class="amt">${money(t.amount)}</td>
                <td>${esc(t.source)}</td>
            </tr>`).join('') ||
            '<tr><td colspan="7" class="muted">No transactions yet</td></tr>';

        document.getElementById('detailModal').classList.add('open');
    } catch (err) {
        alert('Error loading detail: ' + err.message);
    }
}

function closeDetail() {
    document.getElementById('detailModal').classList.remove('open');
}

function txnsFor(id) {
    window.location.href = '/mutualfunds/mf-portfolio-transactions.html?headerId=' + id;
}

// ------------------------------------------------------------------- load
function load() {
    return getJson('/api/mf/headers' + qs()).then(data => {
        rows = data.rows || [];
        renderSummary(data.summary);
        renderTable();
    }).catch(err => alert('Error loading portfolio: ' + err.message));
}

function loadFilters() {
    return getJson('/api/mf/filters').then(f => {
        const amc = document.getElementById('amcFilter');
        (f.amcs || []).forEach(v => {
            const o = document.createElement('option');
            o.value = v; o.textContent = v; amc.appendChild(o);
        });
        const cat = document.getElementById('categoryFilter');
        (f.categories || []).forEach(v => {
            const o = document.createElement('option');
            o.value = v; o.textContent = v; cat.appendChild(o);
        });
    }).catch(() => { /* filters are optional */ });
}

document.getElementById('searchBox').addEventListener('keydown', e => {
    if (e.key === 'Enter') load();
});

// deep-link from other screens: ?headerId / pre-filled search
const params = new URLSearchParams(window.location.search);
if (params.get('q')) document.getElementById('searchBox').value = params.get('q');

Promise.all([loadFilters()]).then(load);
