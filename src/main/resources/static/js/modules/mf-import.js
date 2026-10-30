// mf-import.js - upload a protected KFINTECH pdf, list previous imports
// (MF.md 1/2/3/16/17)
function esc(s) {
    var A = String.fromCharCode(38);
    return String(s == null ? '' : s)
        .replace(/&/g, A + 'amp;')
        .replace(/</g, A + 'lt;')
        .replace(/>/g, A + 'gt;')
        .replace(/"/g, A + 'quot;')
        .replace(/'/g, A + '#39;');
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

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

// MF_V2 1 - the period is printed by the statement as "25-Jan-2007 to
// 25-Oct-2026"; the history shows it the same way.
function printedDate(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso || ''));
    return m ? m[3] + '-' + MONTHS[Number(m[2]) - 1] + '-' + m[1] : (iso ? String(iso) : '?');
}

function chip(v) {
    const cls = ['GREEN', 'AMBER', 'RED', 'PENDING'].includes(v) ? v : 'PENDING';
    return `<span class="badge badge-${cls}">${esc(v || 'PENDING')}</span>`;
}

function statusBadge(v) {
    const color = {UPLOADED: 'badge-sip', ACCEPTED: 'badge-active', DISCARDED: 'badge-cancelled'}[v] || '';
    return `<span class="badge ${color}">${esc(v)}</span>`;
}

// ------------------------------------------------------------------ upload
document.getElementById('uploadForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    const input = document.getElementById('fileInput');
    const error = document.getElementById('uploadError');
    const status = document.getElementById('uploadStatus');
    if (!input.files.length) { showError(error, 'Choose a KFINTECH report file first'); return; }

    const fd = new FormData();
    fd.append('file', input.files[0]);
    const password = document.getElementById('reportPassword').value;
    if (password) fd.append('password', password);

    const btn = document.getElementById('uploadBtn');
    btn.disabled = true;
    error.style.display = 'none';
    status.textContent = 'Reading the statement...';

    try {
        const data = await getJson('/api/mf/import', {method: 'POST', body: fd});
        status.textContent = data.firstImport
            ? 'First import - opening the reconciliation report...'
            : 'Uploaded - opening the reconciliation report...';
        window.location.href = '/mutualfunds/mf-import-view.html?id=' + data.batch.id;
    } catch (err) {
        // MF.md 3 - the reason (missing password / wrong password / unreadable
        // file) is printed under the form instead of a popup.
        status.textContent = '';
        showError(error, err.message);
        btn.disabled = false;
    }
});

function showError(node, message) {
    node.textContent = message || 'Unable to read the Mutual Fund report';
    node.style.display = 'block';
}

// ------------------------------------------------------------- batch list
function loadBatches() {
    return getJson('/api/mf/import/batches').then(batches => {
        const tbody = document.querySelector('#batchTable tbody');
        tbody.innerHTML = (batches || []).map(b => {
            const period = printedDate(b.reportFrom) + ' to ' + printedDate(b.reportTo);
            const actions = [];
            actions.push(`<button class="small primary" onclick="review(${b.id})">Reconciliation</button>`);
            actions.push(`<button class="small" onclick="downloadJson(${b.id})"
                title="Download the parsed statement as JSON (MF.md 16)">JSON</button>`);
            if (b.status === 'UPLOADED') {
                actions.push(`<button class="small danger" onclick="discard(${b.id})">Discard</button>`);
            }
            return `<tr>
                <td>${b.id}</td>
                <td class="left">${esc(b.fileName)}</td>
                <td>${esc(b.fileType)}</td>
                <td>${esc(period)}</td>
                <td>${esc((b.uploadedAt || '').replace('T', ' ').slice(0, 16))}</td>
                <td>${b.folioCount || 0}</td>
                <td>${b.schemeCount || 0}</td>
                <td>${b.txnCount || 0}</td>
                <td>${statusBadge(b.status)}</td>
                <td>${chip(b.validStatus)}</td>
                <td class="left">${actions.join(' ')}</td>
            </tr>`;
        }).join('');
        document.getElementById('emptyNote').style.display = (batches || []).length ? 'none' : 'block';
    }).catch(err => showError(document.getElementById('uploadError'), err.message));
}

function review(id) {
    window.location.href = '/mutualfunds/mf-import-view.html?id=' + id;
}

// MF.md 16 - the parsed report as JSON, for a high level check before the
// reconciliation report is read.
function downloadJson(id) {
    window.location.href = '/api/mf/import/batches/' + id + '/json';
}

function discard(id) {
    if (!confirm('Discard this upload? The temporary tables are emptied and nothing was written to your portfolio.')) return;
    getJson('/api/mf/import/batches/' + id, {method: 'DELETE'})
        .then(loadBatches)
        .catch(err => showError(document.getElementById('uploadError'), err.message));
}

loadBatches();
