// mf-import-view-script.js - reconciliation report: color codes, remap, accept
// (MF.md 15, 16, 20, 22, 23, 25)
let current = null;          // {batch, lines, checks, detail}
let registeredSchemes = []; // for the remap dialog
let remapLineId = null;

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

function num(v) {
    return (Number(v) || 0).toLocaleString('en-IN', {maximumFractionDigits: 4});
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

const STATUS_LABEL = {
    NEW: 'NEW', EXISTS: 'DUPLICATE', NEW_SCHEME: 'NEW SCHEME',
    MAPPED: 'REMAPPED', ERROR: 'ERROR', SKIPPED: 'EMPTY ROW'
};

// ---------------------------------------------------------------- banner
function renderBanner() {
    const b = current.batch;
    const d = current.detail;
    const counts = d.counts || {};
    const cls = ['GREEN', 'AMBER', 'RED'].includes(b.validStatus) ? b.validStatus : 'PENDING';
    const titles = {
        GREEN: 'GREEN - good match, ready to accept',
        AMBER: 'AMBER - needs your attention before accepting',
        RED: 'RED - totals or rows do not match, review required',
        PENDING: 'PENDING - not validated yet'
    };
    document.getElementById('statusBanner').className = 'status-banner banner-' + cls;
    const stmt = b.clientName && b.reportPan
        ? `${esc(b.clientName)} (${esc(b.reportPan)})`
        : b.reportPan ? `statement PAN ${esc(b.reportPan)}`
        : b.clientName ? esc(b.clientName) : '';
    document.getElementById('statusBanner').innerHTML =
        `<span>${titles[cls]}</span>` +
        `<span class="sub">` +
        `new ${counts.NEW || 0} · duplicate ${counts.EXISTS || 0} · ` +
        `unregistered scheme ${counts.NEW_SCHEME || 0} · remapped ${counts.MAPPED || 0} · ` +
        `empty ${counts.SKIPPED || 0} · errors ${counts.ERROR || 0}` +
        (stmt ? ` · statement: ${stmt}` : '') +
        `</span>`;

    const uploaded = b.status === 'UPLOADED';
    document.getElementById('acceptBtn').style.display = uploaded ? '' : 'none';
    document.getElementById('discardBtn').style.display = uploaded ? '' : 'none';
    if (!uploaded) {
        document.getElementById('statusBanner').innerHTML +=
            `<span class="sub">Batch ${esc(b.status)}` +
            `${b.acceptedAt ? ' on ' + esc(String(b.acceptedAt).replace('T', ' ').slice(0, 16)) : ''}</span>`;
    }
}

// --------------------------------------------------------- detail report
function renderDetailCards() {
    const d = current.detail;
    const sys = d.system, rep = d.report, pro = d.projected;
    const delta = (pro.current - sys.current);
    document.getElementById('detailCards').innerHTML = `
        <div class="card">
            <div class="label">System now - value (₹)</div>
            <div class="value">${money(sys.current)}</div>
            <div class="muted">${sys.headers} holdings · ${sys.transactions} txns<br>
                invested ${money(sys.invested)} · profit ${money(sys.profit)}<br>
                units ${num(sys.units)}</div>
        </div>
        <div class="card blue">
            <div class="label">Imported report</div>
            <div class="value">${money(rep.parsedCurrent)}</div>
            <div class="muted">${esc(rep.fileName)} (${esc(rep.fileType)})<br>
                period ${esc(rep.from || '?')} → ${esc(rep.to || '?')}<br>
                report header: inv ${money(rep.invested)} · cur ${money(rep.current)}<br>
                rows: ${rep.holdings} holdings, ${rep.transactions} txns<br>
                units ${num(rep.parsedUnits)}</div>
        </div>
        <div class="card ${delta >= 0 ? 'green' : 'red'}">
            <div class="label">After accept - total value (₹)</div>
            <div class="value">${money(pro.current)}</div>
            <div class="muted">${pro.headers} holdings · ${pro.transactions} txns<br>
                invested ${money(pro.invested)} · profit ${money(pro.profit)}<br>
                units ${num(pro.units)}<br>
                change vs now: ${delta >= 0 ? '+' : ''}${money(delta)}</div>
        </div>
        <div class="card amber">
            <div class="label">Reconciliation impact</div>
            <div class="value">${pro.duplicateRowsSkipped || 0}</div>
            <div class="muted">duplicate rows skipped (no double entry)<br>
                +${pro.refreshedHeaders || 0} holdings refreshed<br>
                +${((current.detail.counts.NEW || 0) > 0 ? pro.headers - sys.headers : 0)} new holdings<br>
                ${pro.schemesToRegister || 0} schemes would be registered</div>
        </div>`;
}

// ---------------------------------------------------------------- checks
function renderChecks() {
    const rows = (current.checks || []).map(c => `
        <tr>
            <td>${esc(c.name)}</td>
            <td><span class="badge badge-${esc(c.severity)}">${esc(c.severity)}</span></td>
            <td>${esc(c.expected)}</td>
            <td>${esc(c.actual)}</td>
            <td class="left">${esc(c.message)}</td>
        </tr>`).join('');
    document.querySelector('#checkTable tbody').innerHTML = rows ||
        '<tr><td colspan="5" class="muted">No checks yet - press Re-validate</td></tr>';
}

// ------------------------------------------------------------------ rows
function renderLines() {
    const status = document.getElementById('statusFilter').value;
    const type = document.getElementById('typeFilter').value;
    const q = document.getElementById('lineSearch').value.trim().toLowerCase();

    const all = current.lines || [];
    let rows = all;
    if (status) rows = rows.filter(l => l.status === status);
    if (type) rows = rows.filter(l => l.lineType === type);
    if (q) {
        rows = rows.filter(l => [l.schemeName, l.amcName, l.folioNo, l.description, l.category]
            .some(v => v && String(v).toLowerCase().includes(q)));
    }

    const counts = current.detail.counts || {};
    document.getElementById('rowCounts').textContent =
        `showing ${rows.length} of ${all.length} rows`;

    document.querySelector('#lineTable tbody').innerHTML = rows.map(l => {
        const isHold = l.lineType === 'HOLDING';
        const st = l.status || '?';
        const action = (st === 'NEW_SCHEME' && current.batch.status === 'UPLOADED')
            ? `<button class="small" onclick="openRemap(${l.id})">🔀 Remap</button>`
            : '';
        return `<tr class="row-${esc(st)}">
            <td>${l.lineNo}</td>
            <td>${isHold ? 'Holding' : 'Transaction'}</td>
            <td class="left">${esc(l.schemeName)}</td>
            <td>${esc(l.amcName)}</td>
            <td>${esc(l.folioNo)}</td>
            <td>${esc(l.txnDate || '')}</td>
            <td class="left">${esc(l.description || '')}</td>
            <td class="amt">${l.units ? num(l.units) : ''}</td>
            <td class="amt">${l.nav ? money(l.nav) : ''}</td>
            <td class="amt">${isHold ? '' : money(l.amount)}</td>
            <td class="amt">${isHold ? money(l.investedValue) : ''}</td>
            <td class="amt">${isHold ? money(l.currentValue) : ''}</td>
            <td><span class="badge badge-${st === 'NEW_SCHEME' ? 'AMBER' :
                    st === 'ERROR' ? 'FAIL' : st === 'NEW' ? 'PASS' : st === 'MAPPED' ? 'INFO' : 'PENDING'}">
                    ${esc(STATUS_LABEL[st] || st)}</span></td>
            <td class="left">${esc(l.message || '')} ${action}</td>
        </tr>`;
    }).join('');
    document.getElementById('emptyNote').style.display = rows.length ? 'none' : 'block';
}

function renderAll() {
    renderBanner();
    renderDetailCards();
    renderChecks();
    renderLines();
    document.getElementById('batchSelect').value = String(current.batch.id);
}

// ----------------------------------------------------------------- load
function loadBatch(id) {
    return getJson('/api/mf/import/batches/' + id).then(data => {
        current = data;
        renderAll();
        if (history.replaceState) {
            history.replaceState(null, '', '/mutualfunds/mf-import-view.html?id=' + id);
        }
    }).catch(err => alert('Error loading batch: ' + err.message));
}

function loadBatchList(selectedId) {
    return getJson('/api/mf/import/batches').then(batches => {
        const sel = document.getElementById('batchSelect');
        sel.innerHTML = '';
        if (!batches.length) {
            sel.innerHTML = '<option value="">No imports yet</option>';
            return null;
        }
        batches.forEach(b => {
            const o = document.createElement('option');
            o.value = b.id;
            o.textContent = `#${b.id} ${b.fileName} (${b.status}/${b.validStatus})`;
            sel.appendChild(o);
        });
        let pick = selectedId && batches.find(b => String(b.id) === String(selectedId));
        if (!pick) pick = batches.find(b => b.status === 'UPLOADED') || batches[0];
        return pick.id;
    });
}

function loadSchemes() {
    return getJson('/api/mf/schemes').then(list => {
        registeredSchemes = list || [];
        const sel = document.getElementById('remapScheme');
        sel.innerHTML = registeredSchemes.map(s =>
            `<option value="${s.id}">${esc(s.schemeName)} - ${esc(s.amcName)}</option>`).join('');
    });
}

// ---------------------------------------------------------------- actions
function revalidate() {
    if (!current) return;
    if (current.batch.status !== 'UPLOADED') {
        alert('An accepted batch cannot be re-validated');
        return;
    }
    getJson('/api/mf/import/batches/' + current.batch.id + '/validate', {method: 'POST'})
        .then(data => { current = data; renderAll(); })
        .catch(err => alert('Validation failed: ' + err.message));
}

function acceptBatch() {
    if (!current) return;
    const d = current.detail;
    const counts = d.counts || {};
    const msg = 'Accept this reconciliation into your portfolio?\n\n' +
        `new rows: ${counts.NEW || 0}\n` +
        `duplicates skipped: ${counts.EXISTS || 0}\n` +
        `unregistered schemes to register: ${counts.NEW_SCHEME || 0}\n` +
        `empty rows ignored: ${counts.SKIPPED || 0}\n` +
        `errors skipped: ${counts.ERROR || 0}\n\n` +
        `After accept: total value ₹${money(d.projected.current)}, ` +
        `units ${num(d.projected.units)}, profit ₹${money(d.projected.profit)}`;
    if (!confirm(msg)) return;

    getJson('/api/mf/import/batches/' + current.batch.id + '/accept', {method: 'POST'})
        .then(r => {
            alert('Accepted!\n' +
                `holdings written: ${r.createdHeaders + r.updatedHeaders} ` +
                `(${r.createdHeaders} new, ${r.updatedHeaders} refreshed)\n` +
                `transactions imported: ${r.createdTransactions}\n` +
                `duplicates skipped: ${r.skippedDuplicates}\n` +
                `schemes registered: ${r.registeredSchemes}`);
            window.location.href = '/mutualfunds/mf-portfolio.html';
        })
        .catch(err => alert('Accept failed: ' + err.message));
}

function discardBatch() {
    if (!current) return;
    if (!confirm('Discard this batch? Nothing was written to the portfolio.')) return;
    getJson('/api/mf/import/batches/' + current.batch.id, {method: 'DELETE'})
        .then(() => window.location.href = '/mutualfunds/mf-import.html')
        .catch(err => alert('Error: ' + err.message));
}

// ----------------------------------------------------------------- remap
function openRemap(lineId) {
    const line = (current.lines || []).find(l => l.id === lineId);
    if (!line) return;
    remapLineId = lineId;
    document.getElementById('remapInfo').innerHTML =
        `Statement row: <b>${esc(line.schemeName)}</b>` +
        (line.amcName ? ` (${esc(line.amcName)})` : '') +
        (line.folioNo ? ` - folio ${esc(line.folioNo)}` : '') +
        (line.txnDate ? ` - ${esc(line.txnDate)}` : '');
    document.getElementById('remapModal').classList.add('open');
}

function closeRemap() {
    document.getElementById('remapModal').classList.remove('open');
    remapLineId = null;
}

function confirmRemap() {
    if (!remapLineId || !current) return;
    const schemeId = document.getElementById('remapScheme').value;
    if (!schemeId) { alert('Choose a registered scheme'); return; }
    getJson('/api/mf/import/batches/' + current.batch.id + '/remap', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({lineId: remapLineId, schemeId: parseInt(schemeId, 10)})
    }).then(data => {
        current = data;
        closeRemap();
        renderAll();
    }).catch(err => alert('Remap failed: ' + err.message));
}

// --------------------------------------------------------------- filters
['statusFilter', 'typeFilter'].forEach(id =>
    document.getElementById(id).addEventListener('change', renderLines));
document.getElementById('lineSearch').addEventListener('input', renderLines);
document.getElementById('batchSelect').addEventListener('change', function () {
    loadBatch(this.value);
});

// -------------------------------------------------------------- bootstrap
const params = new URLSearchParams(window.location.search);
const wanted = params.get('id');
Promise.all([loadSchemes()])
    .then(() => loadBatchList(wanted))
    .then(id => (id ? loadBatch(id) : null))
    .catch(err => alert('Error: ' + err.message));
