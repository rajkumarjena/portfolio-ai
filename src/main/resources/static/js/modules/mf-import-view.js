// mf-import-view.js - the five reconciliation reports of MF.md 12 and the
// accept / discard actions (MF.md 13).
//
// MF_V2 2/3/4: the long reports carry a filter bar (date / folio # / scheme /
// AMC) and are drawn in windows of 100 rows - the next window is appended when
// the bottom of the table scrolls into view - so a report with thousands of
// rows never puts thousands of <tr> on the page.
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

// Columns of each report of MF.md 12, so the screen stays generic when a row
// carries more detail than the previous report did.
const REPORT_COLUMNS = {
    1: [['scheme', 'Scheme', 'left'], ['date', 'Date'], ['description', 'Description', 'left'],
        ['amount', 'Amount'], ['units', 'Units'], ['type', 'Type']],
    2: [['amc', 'AMC', 'left'], ['summaryCost', 'Summary cost'], ['transactionCost', 'Transactions cost'],
        ['costDifference', 'Cost difference'], ['summaryMarket', 'Summary market'],
        ['valuationMarket', 'Valuation market'], ['marketDifference', 'Market difference'],
        ['costOk', 'Cost'], ['marketOk', 'Market']],
    3: [['scheme', 'Scheme', 'left'], ['previous', 'Previous adjusted'], ['current', 'New adjusted'],
        ['difference', 'Difference'], ['reason', 'Reason', 'left']],
    4: [['where', 'Where', 'left'], ['date', 'Date'], ['scheme', 'Scheme', 'left'],
        ['description', 'Description', 'left'], ['amount', 'Amount'], ['units', 'Units'],
        ['type', 'Type'], ['reason', 'Reason', 'left']],
    5: [['check', 'Check', 'left'], ['against', 'Compared with', 'left'], ['expected', 'Expected'],
        ['actual', 'Actual'], ['difference', 'Difference'], ['tolerancePercent', 'Tolerance %'],
        ['ok', 'Within tolerance']]
};

// MF_V2 2/4 - which reports carry a filter bar, and which row field each
// control narrows. "folio" and "schemeName" are cut out of the report's own
// scheme key ("910160157382 | Axis Small Cap Fund ..."); "amc" is delivered by
// the report itself; "date" is the transaction date of the report.
const REPORT_FILTERS = {
    1: [['dateFrom', 'Date from', 'date'], ['dateTo', 'Date to', 'date'],
        ['folio', 'Folio #', 'select'], ['schemeName', 'Scheme', 'select'],
        ['amc', 'AMC', 'select']],
    3: [['folio', 'Folio #', 'select'], ['schemeName', 'Scheme', 'select'],
        ['amc', 'AMC', 'select']],
    // MF_V3 7 - the missing / out of sequence rows get the same filter bar
    4: [['dateFrom', 'Date from', 'date'], ['dateTo', 'Date to', 'date'],
        ['folio', 'Folio #', 'select'], ['schemeName', 'Scheme', 'select'],
        ['amc', 'AMC', 'select']]
};

// MF_V2 3 - rows drawn per window; scrolling past the last drawn row appends
// the next window.
const PAGE_SIZE = 100;

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const STATUS_TEXT = {
    GREEN: 'All five reports are green - the upload agrees with the accepted data.',
    AMBER: 'Highlighted differences need your review before accepting.',
    RED: 'Differences were found - review the red rows before accepting.'
};

let batches = [];
let currentId = null;
/** report id -> the windowed table of that report (rows, filters, window). */
const views = {};

// ------------------------------------------------------------------- load
function loadBatches() {
    return getJson('/api/mf/import/batches').then(list => {
        batches = list || [];
        const select = document.getElementById('batchSelect');
        select.innerHTML = batches.map(b =>
            `<option value="${b.id}">#${b.id} ${esc(b.fileName)}</option>`).join('')
            || '<option value="">No imports yet</option>';
        const wanted = new URLSearchParams(location.search).get('id');
        const pick = wanted && batches.some(b => String(b.id) === wanted)
            ? wanted
            : (batches[0] ? String(batches[0].id) : '');
        if (pick) {
            select.value = pick;
            currentId = pick;
            return reconcile();
        }
        document.getElementById('statusBanner').className = 'status-banner banner-PENDING';
        document.getElementById('statusBanner').textContent =
            'No import selected - upload a KFINTECH report first.';
        document.getElementById('reports').innerHTML = '';
        document.getElementById('batchCards').innerHTML = '';
        document.getElementById('emptyNote').style.display = 'block';
    }).catch(err => banner('RED', err.message));
}

// ------------------------------------------------------------ the reports
function reconcile() {
    const select = document.getElementById('batchSelect');
    if (!select.value) { banner('PENDING', 'No import selected.'); return; }
    currentId = select.value;
    document.getElementById('statusBanner').textContent = 'Reading the reconciliation report...';
    return getJson('/api/mf/import/batches/' + currentId + '/report')
        .then(render)
        .catch(err => banner('RED', err.message));
}

function render(data) {
    const batch = data.batch || {};
    const counts = data.counts || {};
    // MF_V3 2 - does the accepted data already span this upload?
    const coverage = data.coverage || {};
    document.getElementById('emptyNote').style.display = 'none';

    banner(data.status,
        'Reconciliation ' + esc(data.status) + ' - ' + (STATUS_TEXT[data.status] || ''),
        (coverage.covered && coverage.note ? esc(coverage.note) + '<br>' : '')
            + ['newTransactions: ' + counts.newTransactions,
            'adjusted changes: ' + counts.adjustedChanges,
            'missing: ' + counts.missingTransactions,
            'AMC mismatches: ' + counts.amcMismatches,
            'failed checks: ' + counts.failedChecks].join(' &nbsp;|&nbsp; '));

    const tol = data.tolerance || {};
    document.getElementById('batchCards').innerHTML = [
        card('Import', '#' + batch.id + ' ' + esc(batch.fileName || '')),
        card('Status', esc(batch.status)),
        // MF_V2 1 - the period exactly the way the statement prints it
        card('Period', esc(periodOf(batch))),
        // MF_V3 2 - whether the accepted data already spans this upload
        card('Coverage', coverage.covered ? 'covered by the accepted data'
            : (data.hasAcceptedData ? 'extends the accepted data' : 'nothing accepted yet')),
        card('Client', esc(batch.clientName || '')),
        card('Folios', batch.folioCount),
        card('Schemes', batch.schemeCount),
        card('Transactions', batch.txnCount),
        card('ADJUSTED rows', batch.adjustedCount),
        card('Tolerance (units / cost / market %)',
            esc([tol.unitsPercent, tol.costPercent, tol.marketPercent].join(' / '))),
        card('Accepted data', data.hasAcceptedData ? 'present' : 'none - first import')
    ].join('');

    const host = document.getElementById('reports');
    closeViews();
    host.innerHTML = '';
    (data.reports || []).forEach(report => host.appendChild(sectionFor(report)));
    if (window.lucide && typeof window.lucide.createIcons === 'function') {
        window.lucide.createIcons();
    }

    const accept = document.getElementById('acceptBtn');
    accept.disabled = batch.status === 'ACCEPTED';
    accept.textContent = batch.status === 'ACCEPTED' ? 'Already accepted' : 'Accept into Portfolio';
}

/** Builds one report section: header, filter bar, table and scroll sentinel. */
function sectionFor(report) {
    const columns = REPORT_COLUMNS[report.id] || [];
    const rows = report.rows || [];
    const head = columns.map(c => `<th class="${c[2] || ''}">${esc(c[1])}</th>`).join('');
    const holder = document.createElement('div');
    holder.innerHTML = `<section class="filter-section" style="display:block;">
            <h3 style="margin-bottom:6px;">
                <span class="badge badge-${esc(report.status)}">${esc(String(report.id))}</span>
                ${esc(report.name)}
                <span class="badge badge-${esc(report.status)}" style="margin-left:8px;">${esc(report.status)}</span>
            </h3>
            <div class="muted" style="margin-bottom:8px;">${esc(report.note || '')}</div>
            ${filterBar(report, rows)}
            <div class="muted" id="range-${report.id}" style="margin-bottom:6px;"></div>
            <table class="data-table">
                <thead><tr>${head}</tr></thead>
                <tbody id="rows-${report.id}"></tbody>
            </table>
            <div class="muted" id="more-${report.id}" style="display:none; text-align:center; padding:6px;"></div>
        </section>`;
    const section = holder.firstElementChild;
    mount(section, report, columns, rows);
    return section;
}

/** The filter controls of reports 1 and 3 (MF_V2 2/4). */
function filterBar(report, rows) {
    const defs = REPORT_FILTERS[report.id];
    if (!defs || !rows.length) {
        return '';
    }
    const controls = defs.map(([field, label, kind]) => {
        const id = 'f' + report.id + '-' + field;
        return kind === 'date'
            ? `<label for="${id}">${esc(label)}</label>` +
              `<input type="date" id="${id}" data-filter="${field}">`
            : `<label for="${id}">${esc(label)}</label>` +
              `<select id="${id}" data-filter="${field}"><option value="">All</option></select>`;
    }).join('');
    return `<div class="filter-section" style="margin:0 0 8px;">
            ${controls}
            <button class="secondary" onclick="resetFilters(${report.id})">
                <i data-lucide="filter-x"></i>Reset</button>
        </div>`;
}

function mount(section, report, columns, rows) {
    const view = {
        id: report.id,
        section: section,
        columns: columns,
        note: report.note || '',
        all: rows,
        filtered: rows,
        drawn: 0,
        body: section.querySelector('#rows-' + report.id),
        more: section.querySelector('#more-' + report.id),
        range: section.querySelector('#range-' + report.id),
        observer: null
    };
    views[report.id] = view;

    const defs = REPORT_FILTERS[report.id];
    if (defs) {
        defs.forEach(([field, , kind]) => {
            if (kind !== 'select') return;
            const select = section.querySelector('#f' + report.id + '-' + field);
            fillSelect(select, distinct(rows, field));
        });
        section.querySelectorAll('[data-filter]').forEach(el =>
            el.addEventListener('change', () => refilter(view)));
    }
    redraw(view);
    observe(view);
    watchScroll(view);
}

function fillSelect(select, values) {
    if (!select) return;
    select.innerHTML = '<option value="">All</option>'
        + values.map(v => `<option value="${esc(v)}">${esc(v)}</option>`).join('');
}

/** Distinct, naturally sorted values of a filter field over the report rows. */
function distinct(rows, field) {
    const values = new Set();
    for (const row of rows) {
        const value = filterValue(row, field);
        if (value) values.add(value);
    }
    return [...values].sort((a, b) => String(a).localeCompare(String(b), undefined, {numeric: true}));
}

function filterValue(row, field) {
    if (field === 'amc') return String(row.amc || '');
    const parts = splitScheme(row.scheme);
    return field === 'folio' ? parts[0] : parts[1];
}

// The statement prints the folio number and the scheme name in one column
// ("910160157382 | Axis Small Cap Fund Direct Growth (Non Demat)") - the
// filters treat them as two fields (MF_V2 2).
function splitScheme(key) {
    const text = String(key == null ? '' : key);
    const cut = text.indexOf(' | ');
    return cut < 0 ? ['', text] : [text.slice(0, cut), text.slice(cut + 3)];
}

function refilter(view) {
    const chosen = {};
    view.section.querySelectorAll('[data-filter]').forEach(el => {
        if (el.value) chosen[el.dataset.filter] = el.value;
    });
    view.filtered = view.all.filter(row => matches(row, chosen));
    redraw(view);
}

function matches(row, chosen) {
    if (chosen.amc && filterValue(row, 'amc') !== chosen.amc) return false;
    if (chosen.folio && filterValue(row, 'folio') !== chosen.folio) return false;
    if (chosen.schemeName && filterValue(row, 'schemeName') !== chosen.schemeName) return false;
    if (chosen.dateFrom && (!row.date || row.date < chosen.dateFrom)) return false;
    if (chosen.dateTo && (!row.date || row.date > chosen.dateTo)) return false;
    return true;
}

function resetFilters(id) {
    const view = views[id];
    if (!view) return;
    view.section.querySelectorAll('[data-filter]').forEach(el => { el.value = ''; });
    refilter(view);
}

/** Back to the first window of the (re)filtered rows. */
function redraw(view) {
    view.drawn = 0;
    view.body.innerHTML = '';
    appendWindow(view);
}

/** Draws the next window of rows and refreshes the counter / sentinel. */
function appendWindow(view) {
    const total = view.filtered.length;
    if (!total) {
        view.body.innerHTML = `<tr><td colspan="${view.columns.length || 1}" class="muted">`
            + esc(view.all.length ? 'No row matches the selected filters.' : view.note || 'Nothing to show')
            + '</td></tr>';
    } else {
        const end = Math.min(view.drawn + PAGE_SIZE, total);
        let html = '';
        for (let i = view.drawn; i < end; i++) {
            html += rowHtml(view.filtered[i], view.columns);
        }
        view.body.insertAdjacentHTML('beforeend', html);
        view.drawn = end;
    }
    updateRange(view);
    updateSentinel(view);
}

function rowHtml(row, columns) {
    return `<tr class="tone-${esc(row.tone || 'grey')}">`
        + columns.map(c => `<td class="${c[2] || ''}">${fmt(row[c[0]])}</td>`).join('')
        + '</tr>';
}

function updateRange(view) {
    const all = view.all.length;
    const total = view.filtered.length;
    if (!all) {
        view.range.textContent = '';
    } else if (total === all) {
        view.range.textContent = total > PAGE_SIZE
            ? `Showing ${view.drawn} of ${total} rows` : '';
    } else {
        view.range.textContent =
            `${total} of ${all} rows match the filters - showing ${view.drawn}`;
    }
}

function updateSentinel(view) {
    const total = view.filtered.length;
    const windowed = view.all.length > PAGE_SIZE;
    if (view.drawn < total) {
        view.more.style.display = 'block';
        view.more.textContent =
            `Showing ${view.drawn} of ${total} - scroll down to load the next ${PAGE_SIZE}`;
    } else {
        view.more.style.display = windowed ? 'block' : 'none';
        view.more.textContent = windowed ? `End of the report - ${total} row(s) shown` : '';
    }
}

// A jump that flies past the sentinel (End key, scroll-bar drag) raises no
// intersection event, so the scroll position is checked as well - cheap, and
// it can only append while rows are left to draw.
function watchScroll(view) {
    if (!('IntersectionObserver' in window)) return; // the click fallback below covers it
    let ticking = false;
    view.onScroll = () => {
        if (ticking || view.drawn >= view.filtered.length) return;
        ticking = true;
        requestAnimationFrame(() => {
            ticking = false;
            const rect = view.more.getBoundingClientRect();
            if (rect.top < window.innerHeight + 300) appendWindow(view);
        });
    };
    window.addEventListener('scroll', view.onScroll, {passive: true});
}

// MF_V2 3 - the next 100 rows are fetched from memory only when the sentinel
// under the table comes into view, so the page never holds the whole report.
function observe(view) {
    if (!('IntersectionObserver' in window)) {
        view.more.style.cursor = 'pointer';
        view.more.onclick = () => appendWindow(view);
        return;
    }
    view.observer = new IntersectionObserver(entries => {
        for (const entry of entries) {
            if (entry.isIntersecting && view.drawn < view.filtered.length) {
                appendWindow(view);
            }
        }
    }, {rootMargin: '300px'});
    view.observer.observe(view.more);
}

function closeViews() {
    Object.values(views).forEach(view => {
        if (view.observer) view.observer.disconnect();
        if (view.onScroll) window.removeEventListener('scroll', view.onScroll);
    });
    Object.keys(views).forEach(key => delete views[key]);
}

function card(label, value) {
    return `<div class="card"><div class="label">${esc(label)}</div><div class="value">${value}</div></div>`;
}

// MF_V2 1 - the period is stored as a date, but the statement prints
// "25-Jan-2007 to 25-Oct-2026"; show it exactly the way the pdf does.
function printedDate(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso || ''));
    return m ? m[3] + '-' + MONTHS[Number(m[2]) - 1] + '-' + m[1] : (iso ? String(iso) : '?');
}

function periodOf(batch) {
    return printedDate(batch.reportFrom) + ' to ' + printedDate(batch.reportTo);
}

function fmt(value) {
    if (value === null || value === undefined) return '';
    if (typeof value === 'boolean') {
        return value ? '<span class="badge badge-GREEN">yes</span>'
            : '<span class="badge badge-RED">no</span>';
    }
    if (typeof value === 'number') {
        return Number.isInteger(value) ? value.toLocaleString('en-IN')
            : value.toLocaleString('en-IN', {maximumFractionDigits: 4});
    }
    if (typeof value === 'object') return esc(JSON.stringify(value));
    return esc(value);
}

function banner(status, text, sub) {
    const node = document.getElementById('statusBanner');
    const cls = ['GREEN', 'AMBER', 'RED', 'PENDING'].includes(status) ? status : 'PENDING';
    node.className = 'status-banner banner-' + cls;
    node.innerHTML = esc(text) + (sub ? `<span class="sub">${sub}</span>` : '');
}

// --------------------------------------------------------------- actions
function acceptBatch() {
    if (!currentId) return;
    const confirmed = confirm('Accept this reconciliation? The temporary tables are copied '
        + 'into your portfolio and become the data the next upload is compared with (MF.md 13).');
    if (!confirmed) return;
    getJson('/api/mf/import/batches/' + currentId + '/accept', {method: 'POST'})
        .then(() => {
            banner('GREEN', 'Import accepted into your portfolio.');
            return loadBatches();
        })
        .catch(err => banner('RED', err.message));
}

function discardBatch() {
    if (!currentId) return;
    if (!confirm('Discard this upload? The temporary tables are emptied; accepted data is untouched.')) return;
    getJson('/api/mf/import/batches/' + currentId, {method: 'DELETE'})
        .then(() => loadBatches())
        .catch(err => banner('RED', err.message));
}

function downloadJson() {
    if (currentId) window.location.href = '/api/mf/import/batches/' + currentId + '/json';
}

document.getElementById('batchSelect').addEventListener('change', reconcile);

loadBatches();