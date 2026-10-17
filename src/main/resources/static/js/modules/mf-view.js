// mf-view-script.js - view active SIPs, pause/cancel/redeem (MF.md 1, 7, 10)
let allSips = [];

function esc(s) {
    var A = String.fromCharCode(38); // '&' without writing entities literally
    return String(s == null ? '' : s)
        .replace(/&/g, A + 'amp;')
        .replace(/</g, A + 'lt;')
        .replace(/>/g, A + 'gt;')
        .replace(/"/g, A + 'quot;')
        .replace(/'/g, A + '#39;');
}

function money(v) {
    return (Number(v) || 0).toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2});
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

function badge(status) {
    const cls = {ACTIVE: 'badge-active', PAUSED: 'badge-paused',
        CANCELLED: 'badge-cancelled', CLOSED: 'badge-closed'}[status] || '';
    return `<span class="badge ${cls}">${esc(status || '')}</span>`;
}

// --------------------------------------------------------------- rendering
function render() {
    const status = document.getElementById('statusFilter').value;
    const q = document.getElementById('searchBox').value.trim().toLowerCase();

    let rows = allSips;
    if (status && status !== '__ALL__') rows = rows.filter(h => h.status === status);
    if (q) {
        rows = rows.filter(h => {
            const s = h.scheme || {};
            return [s.schemeName, s.amcName, s.category, h.folioNo, h.notes]
                .some(v => v && String(v).toLowerCase().includes(q));
        });
    }

    const tbody = document.querySelector('#sipTable tbody');
    tbody.innerHTML = rows.map(h => {
        const s = h.scheme || {};
        const actions = [];
        if (h.status === 'ACTIVE') {
            actions.push(`<button class="small" onclick="setStatus(${h.id},'PAUSED')"><i data-lucide="pause"></i>Pause</button>`);
            actions.push(`<button class="small danger" onclick="setStatus(${h.id},'CANCELLED')"><i data-lucide="x"></i>Cancel</button>`);
        }
        if (h.status === 'PAUSED') {
            actions.push(`<button class="small good" onclick="setStatus(${h.id},'ACTIVE')"><i data-lucide="play"></i>Resume</button>`);
            actions.push(`<button class="small danger" onclick="setStatus(${h.id},'CANCELLED')"><i data-lucide="x"></i>Cancel</button>`);
        }
        if (h.status !== 'CLOSED' && (h.units || 0) > 0) {
            actions.push(`<button class="small" onclick="openRedeem(${h.id})"><i data-lucide="hand-coins"></i>Redeem</button>`);
        }
        actions.push(`<button class="small" onclick="editSip(${h.id})"><i data-lucide="pencil"></i>Edit</button>`);

        return `<tr>
            <td class="left">${esc(s.schemeName)}</td>
            <td class="left">${esc(s.amcName)}</td>
            <td>${esc(s.category)}</td>
            <td>${esc(h.folioNo)}</td>
            <td class="amt">${money(h.sipAmount)}</td>
            <td>${esc(h.sipType || '-')}</td>
            <td>${h.sipDate || '-'}</td>
            <td>${esc(h.sipStartDate || '-')}</td>
            <td>${esc(h.sipEndDate || '-')}</td>
            <td>${esc(h.bankName || '')}<div class="muted">${esc(h.bankAccountNumber || '')}</div></td>
            <td class="amt">${(Number(h.units) || 0).toFixed(4)}</td>
            <td class="amt">${money(h.currentValue)}</td>
            <td>${badge(h.status)}</td>
            <td class="left">${actions.join(' ')}</td>
        </tr>`;
    }).join('');
    window.App && App.renderIcons();

    document.getElementById('emptyNote').style.display = rows.length ? 'none' : 'block';

    // summary over the FULL list (not the filtered one) - MF.md 18
    const sum = window.lastSummary || {};
    const invested = allSips.reduce((a, h) => a + (h.investedValue || 0), 0);
    const current = allSips.reduce((a, h) => a + (h.currentValue || 0), 0);
    const units = allSips.reduce((a, h) => a + (h.units || 0), 0);
    const ret = current - invested;
    document.getElementById('summaryCards').innerHTML = `
        <div class="card"><div class="label">Total SIPs</div>
            <div class="value">${allSips.length}</div></div>
        <div class="card"><div class="label">Invested (₹)</div>
            <div class="value">${money(invested)}</div></div>
        <div class="card"><div class="label">Current Value (₹)</div>
            <div class="value">${money(current)}</div></div>
        <div class="card ${ret >= 0 ? 'green' : 'red'}"><div class="label">Returns (₹)</div>
            <div class="value ${ret >= 0 ? 'pos' : 'neg'}">${money(ret)}</div></div>
        <div class="card"><div class="label">Total Units</div>
            <div class="value">${units.toFixed(4)}</div></div>`;
}

// ---------------------------------------------------------------- actions
function editSip(id) {
    window.location.href = '/mutualfunds/mf-sip-form.html?id=' + id;
}

function setStatus(id, status) {
    const verb = status === 'CANCELLED' ? 'cancel' : (status === 'PAUSED' ? 'pause' : 'resume');
    if (!confirm(`Are you sure you want to ${verb} this SIP?`)) return;
    getJson('/api/mf/sips/' + id + '/status', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({status: status})
    }).then(load).catch(err => alert('Error: ' + err.message));
}

function runDueSips() {
    getJson('/api/mf/sips/run-due', {method: 'POST'})
        .then(r => {
            alert(r.executed > 0
                ? `${r.executed} due SIP instalment(s) executed.`
                : 'No SIP instalments are due right now.');
            load();
        })
        .catch(err => alert('Error: ' + err.message));
}

// --------------------------------------------------------------- redeem
let redeemHeaderId = null;

function openRedeem(id) {
    const h = allSips.find(x => x.id === id);
    if (!h) return;
    redeemHeaderId = id;
    const nav = h.nav > 0 ? h.nav : 0;
    document.getElementById('redeemInfo').innerHTML =
        `${esc((h.scheme || {}).schemeName)} - folio ${esc(h.folioNo)}<br>` +
        `Held: <b>${(h.units || 0).toFixed(4)} units</b>, value ₹${money(h.currentValue)}` +
        (nav ? `, last NAV ${nav}` : '');
    document.getElementById('redeemValue').value = '';
    document.getElementById('redeemMode').value = 'UNITS';
    document.getElementById('redeemModal').classList.add('open');
}

function closeRedeem() {
    document.getElementById('redeemModal').classList.remove('open');
    redeemHeaderId = null;
}

function confirmRedeem() {
    if (!redeemHeaderId) return;
    const mode = document.getElementById('redeemMode').value;
    const value = parseFloat(document.getElementById('redeemValue').value);
    if (mode !== 'FULL' && (isNaN(value) || value <= 0)) {
        alert('Enter a value greater than zero');
        return;
    }
    const h = allSips.find(x => x.id === redeemHeaderId);
    const msg = mode === 'FULL'
        ? 'Sell ALL units of this mutual fund?'
        : (mode === 'UNITS'
            ? `Sell ${value} unit(s)?`
            : `Sell units worth ₹${value}?`);
    if (!confirm(msg + '\nOldest transactions are redeemed first (FIFO).')) return;

    getJson('/api/mf/sips/' + redeemHeaderId + '/redeem', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({mode: mode, value: mode === 'FULL' ? 0 : value})
    }).then(r => {
        alert(`Redeemed ${(r.unitsSold || 0).toFixed(4)} units for ₹${money(r.proceeds)}\n` +
            `FIFO cost basis removed: ₹${money(r.costBasis)}`);
        closeRedeem();
        load();
    }).catch(err => alert('Redemption failed: ' + err.message));
}

// ------------------------------------------------------------------ load
function load() {
    return getJson('/api/mf/sips').then(data => {
        allSips = data.rows || [];
        window.lastSummary = data.summary;
        render();
    }).catch(err => alert('Error loading SIPs: ' + err.message));
}

document.getElementById('statusFilter').addEventListener('change', render);
document.getElementById('searchBox').addEventListener('input', render);
load();
