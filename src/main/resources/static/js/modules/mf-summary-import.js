function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}
async function request(url, options) {
    const response = await fetch(url, options);
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = {message: text}; }
    if (!response.ok) throw new Error(data && data.message ? data.message : `HTTP ${response.status}`);
    return data;
}
let batchId = null;
let report = null;
document.getElementById('summaryUploadForm').addEventListener('submit', async event => {
    event.preventDefault();
    const file = document.getElementById('summaryFile').files[0];
    const message = document.getElementById('summaryUploadMessage');
    if (!file) { message.textContent = 'Choose the summary PDF first.'; return; }
    const form = new FormData(); form.append('file', file);
    const password = document.getElementById('summaryPassword').value;
    if (password) form.append('password', password);
    const button = document.getElementById('summaryUploadButton'); button.disabled = true;
    message.textContent = 'Reading and validating the summary...';
    try {
        const result = await request('/api/mf/summary-import', {method:'POST', body:form});
        batchId = result.batch.id;
        message.textContent = `Uploaded statement as on ${result.asOn}; opening reconciliation.`;
        await loadReport();
    } catch (error) { message.textContent = error.message; }
    finally { button.disabled = false; }
});
async function loadReport() {
    report = await request(`/api/mf/summary-import/batches/${batchId}/report`);
    const view = document.getElementById('summaryReview'); view.style.display = 'block';
    const banner = document.getElementById('summaryBanner');
    banner.className = `status-banner banner-${report.status}`;
    banner.textContent = `Summary reconciliation ${report.status}: ${report.note} As on ${report.asOn}. Email: ${report.email}.`;
    const rows = report.rows || [];
    const scrollHost = document.getElementById('summaryTableScroll');
    scrollHost.style.maxHeight = rows.length > 50 ? '65vh' : 'none';
    scrollHost.style.overflowY = rows.length > 50 ? 'auto' : 'visible';
    document.getElementById('summaryTotals').innerHTML = [
        ['PDF cost', report.expectedCost], ['Calculated cost', report.actualCost], ['Cost difference', report.costDifference],
        ['PDF market value', report.expectedMarket], ['Calculated market value', report.actualMarket], ['Market difference', report.marketDifference]
    ].map(([label, value]) => `<div class="card"><b>${esc(label)}</b><span>${esc(value)}</span></div>`).join('');
    document.getElementById('summaryRows').innerHTML = rows.map(row => `<tr data-row="${row.id}" class="${row.probableFolioMatch ? 'tone-amber' : ''}">
      <td>${esc(row.folioNo)}${row.probableFolioMatch ? '<span class="badge badge-AMBER">Verify probable match</span>' : ''}</td><td><input class="summary-amc" value="${esc(row.amc || '')}" aria-label="AMC for ${esc(row.folioNo)}"></td>
      <td><input class="summary-scheme" value="${esc(row.scheme || '')}" aria-label="Scheme name for ${esc(row.folioNo)}"></td>
      <td>${esc(row.isin)}</td><td>${esc(row.costValue)}</td><td>${esc(row.units)}</td><td>${esc(row.navDate)}</td><td>${esc(row.price)}</td><td>${esc(row.marketValue)}</td><td>${esc(row.matchStatus)}${row.message ? ` — ${esc(row.message)}` : ''}</td></tr>`).join('');
    const accept = document.getElementById('summaryAccept');
    accept.disabled = report.batch.status === 'ACCEPTED';
    accept.textContent = accept.disabled ? 'Already accepted' : 'Accept summary';
}
document.getElementById('summaryAccept').addEventListener('click', async () => {
    if (!batchId || !confirm('Accept these summary values into your mutual fund portfolio?')) return;
    const corrections = {};
    document.querySelectorAll('#summaryRows tr[data-row]').forEach(row => {
        corrections[row.dataset.row] = {
            amc: row.querySelector('.summary-amc').value,
            scheme: row.querySelector('.summary-scheme').value
        };
    });
    const button = document.getElementById('summaryAccept'); button.disabled = true;
    try { await request(`/api/mf/summary-import/batches/${batchId}/accept`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(corrections)}); await loadReport(); }
    catch (error) { document.getElementById('summaryBanner').textContent = error.message; button.disabled = false; }
});
document.getElementById('summaryDiscard').addEventListener('click', async () => {
    if (!batchId || !confirm('Discard this summary review? Your accepted portfolio is unchanged.')) return;
    try { await request(`/api/mf/summary-import/batches/${batchId}`, {method:'DELETE'}); document.getElementById('summaryReview').style.display='none'; document.getElementById('summaryUploadMessage').textContent='Summary import discarded.'; }
    catch (error) { document.getElementById('summaryBanner').textContent = error.message; }
});
