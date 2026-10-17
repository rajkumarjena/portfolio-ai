// mf-import-script.js - upload CAS statements, list batches (MF.md 14)
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

function chip(v) {
    const cls = ['GREEN', 'AMBER', 'RED', 'PENDING'].includes(v) ? v : 'PENDING';
    return `<span class="badge badge-${cls}">${esc(v || 'PENDING')}</span>`;
}

function statusBadge(v) {
    const color = {UPLOADED: 'badge-sip', ACCEPTED: 'badge-active', REJECTED: 'badge-cancelled'}[v] || '';
    return `<span class="badge ${color}">${esc(v)}</span>`;
}

// ------------------------------------------------------------------ upload
document.getElementById('uploadForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    const input = document.getElementById('fileInput');
    if (!input.files.length) { alert('Choose a report file first'); return; }

    const fd = new FormData();
    fd.append('file', input.files[0]);
    const btn = document.getElementById('uploadBtn');
    const status = document.getElementById('uploadStatus');
    btn.disabled = true;
    status.textContent = 'Parsing statement...';

    try {
        const data = await getJson('/api/mf/import', {method: 'POST', body: fd});
        status.textContent = 'Uploaded - opening reconciliation report...';
        window.location.href = '/mutualfunds/mf-import-view.html?id=' + data.batch.id;
    } catch (err) {
        status.textContent = '';
        alert('Upload failed: ' + err.message);
        btn.disabled = false;
    }
});

// ------------------------------------------------------------- batch list
function loadBatches() {
    return getJson('/api/mf/import/batches').then(batches => {
        const tbody = document.querySelector('#batchTable tbody');
        tbody.innerHTML = (batches || []).map(b => {
            const period = (b.reportFrom || '?') + ' to ' + (b.reportTo || '?');
            const actions = [];
            if (b.status === 'UPLOADED') {
                actions.push(`<button class="small primary" onclick="review(${b.id})">Review & Accept</button>`);
                actions.push(`<button class="small danger" onclick="discard(${b.id})">Discard</button>`);
            } else {
                actions.push(`<button class="small" onclick="review(${b.id})">View</button>`);
            }
            return `<tr>
                <td>${b.id}</td>
                <td class="left">${esc(b.fileName)}</td>
                <td>${esc(b.fileType)}</td>
                <td>${esc(period)}</td>
                <td>${esc((b.uploadedAt || '').replace('T', ' ').slice(0, 16))}</td>
                <td>${statusBadge(b.status)}</td>
                <td>${chip(b.validStatus)}</td>
                <td class="left">${actions.join(' ')}</td>
            </tr>`;
        }).join('');
        document.getElementById('emptyNote').style.display = (batches || []).length ? 'none' : 'block';
    }).catch(err => alert('Error loading batches: ' + err.message));
}

function review(id) {
    window.location.href = '/mutualfunds/mf-import-view.html?id=' + id;
}

function discard(id) {
    if (!confirm('Discard this imported batch? No portfolio rows were written.')) return;
    getJson('/api/mf/import/batches/' + id, {method: 'DELETE'})
        .then(loadBatches)
        .catch(err => alert('Error: ' + err.message));
}

loadBatches();
