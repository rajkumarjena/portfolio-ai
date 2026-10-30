(function () {
    'use strict';
    // MFR_V9 1/2/6 / MFR_V10 4 - every record of mf_folio_scheme_transactions for
    // the signed-in client, filtered by date range / AMC / scheme / text. One
    // page of 50 rows is drawn inside a vertically scrolling table with numbered
    // page controls underneath, so a ledger of thousands of rows stays navigable.
    const PAGE_SIZE = 50;
    const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
    const number = value => Number(value || 0);
    const money = value => number(value).toLocaleString('en-IN', {minimumFractionDigits:2, maximumFractionDigits:2});
    const units = value => number(value).toLocaleString('en-IN', {minimumFractionDigits:0, maximumFractionDigits:6});
    const isBlank = value => value === null || value === undefined || value === '';
    const amountText = value => isBlank(value) ? '—' : money(value);
    const unitsText = value => isBlank(value) ? '—' : units(value);

    const txnBody = document.querySelector('#txnTable tbody');
    const scrollBox = document.getElementById('txnScroll');
    const more = document.getElementById('txnMore');
    const pager = document.getElementById('txnPager');
    const emptyNote = document.getElementById('emptyNote');
    const rangeNote = document.getElementById('rangeNote');
    const amcSelect = document.getElementById('amcFilter');
    const schemeSelect = document.getElementById('schemeFilter');
    const state = {from:'', to:'', amcId:'', scheme:'', search:'',
        page:1, pages:1, total:0, busy:false};

    function requestUrl(page) {
        const params = new URLSearchParams({page: String(page), size: String(PAGE_SIZE)});
        if (state.from) params.set('from', state.from);
        if (state.to) params.set('to', state.to);
        if (state.amcId) params.set('amcId', state.amcId);
        if (state.scheme) params.set('scheme', state.scheme);
        if (state.search) params.set('q', state.search);
        return '/api/mf/report/portfolio/transactions?' + params.toString();
    }

    function remember() {
        const params = new URLSearchParams(location.search);
        [['from','from'], ['to','to'], ['amcId','amcId'], ['scheme','scheme'], ['q','search']]
            .forEach(([key, field]) => {
                if (state[field]) params.set(key, state[field]); else params.delete(key);
            });
        const text = params.toString();
        history.replaceState(null, '', location.pathname + (text ? '?' + text : ''));
    }

    async function load(page) {
        if (state.busy) return;
        if (state.from && state.to && state.from > state.to) {
            more.textContent = 'The From date is after the To date - pick a valid range.';
            return;
        }
        state.busy = true;
        try {
            const response = await fetch(requestUrl(page), {cache:'no-store'});
            if (!response.ok) throw new Error('Unable to load transactions (' + response.status + ').');
            const data = await response.json();
            fillOptions(data.amcs || [], data.schemeNames || []);
            renderSummary(data.totals || {});
            renderRange(data);
            renderRows(data.rows || []);
            const paging = data.pagination || {};
            state.page = page;
            state.total = paging.total || 0;
            state.pages = Math.max(1, Math.ceil(state.total / PAGE_SIZE));
            updateMore(paging);
            renderPager();
            emptyNote.style.display = state.total ? 'none' : 'block';
            window.App && App.renderIcons();
        } catch (error) {
            more.textContent = error.message;
        } finally {
            state.busy = false;
        }
    }

    function renderRows(rows) {
        txnBody.innerHTML = rows.map(row => {
            const debit = number(row.amount) < 0;
            return `<tr>
                <td>${esc(row.date)}</td><td class="left">${esc(row.amc)}</td><td>${esc(row.folioNo)}</td>
                <td class="left">${esc(row.scheme)}</td><td>${esc(row.type)}</td>
                <td class="left">${esc(row.description)}</td>
                <td class="amt">${unitsText(row.units)}</td><td class="amt">${amountText(row.nav)}</td>
                <td class="amt ${debit ? 'neg' : ''}">${amountText(row.amount)}</td>
                <td class="amt">${unitsText(row.balance)}</td></tr>`;
        }).join('');
        scrollBox.scrollTop = 0;
    }

    function renderSummary(totals) {
        document.getElementById('summaryCards').innerHTML = `
            <div class="card"><div class="label">Records</div>
                <div class="value">${number(totals.count).toLocaleString('en-IN')}</div></div>
            <div class="card"><div class="label">Net Investment (₹)</div>
                <div class="value">${money(totals.amount)}</div></div>
            <div class="card blue"><div class="label">Market Value of the range (₹)</div>
                <div class="value">${money(totals.marketValue)}</div></div>
            <div class="card"><div class="label">Units of the range</div>
                <div class="value">${units(totals.units)}</div></div>`;
    }

    function renderRange(data) {
        rangeNote.textContent = data.derived
            ? 'Showing ' + (data.from || 'the first transaction') + ' to ' + (data.to || 'today')
                + '. Net investment is the sum of the amounts; market value is the units of the range '
                + 'times the last NAV of each scheme in the range.'
            : '';
    }

    function fillOptions(amcs, schemes) {
        const currentAmc = amcSelect.value || state.amcId;
        const currentScheme = schemeSelect.value || state.scheme;
        amcSelect.innerHTML = '<option value="">All</option>'
            + amcs.map(amc => `<option value="${esc(amc.id)}">${esc(amc.name)}</option>`).join('');
        schemeSelect.innerHTML = '<option value="">All</option>'
            + schemes.map(name => `<option value="${esc(name)}">${esc(name)}</option>`).join('');
        amcSelect.value = currentAmc;
        schemeSelect.value = currentScheme;
    }

    function updateMore(paging) {
        if (!state.total) {
            more.textContent = '';
            return;
        }
        const from = (paging.from || 0) + 1;
        const to = paging.to || state.total;
        more.textContent = `Showing ${from.toLocaleString('en-IN')}–${to.toLocaleString('en-IN')} of `
            + `${state.total.toLocaleString('en-IN')} transactions`
            + (state.pages > 1 ? ' — scroll inside the table for the rest of this page.' : '.');
    }

    function renderPager() {
        window.App && App.pager(pager, {
            page: state.page, pages: state.pages,
            onGo: page => load(page)
        });
    }

    function readFilters() {
        state.from = document.getElementById('fromDate').value;
        state.to = document.getElementById('toDate').value;
        state.amcId = amcSelect.value;
        state.scheme = schemeSelect.value;
        state.search = document.getElementById('searchBox').value.trim();
        remember();
    }

    function reset() {
        ['fromDate', 'toDate', 'searchBox'].forEach(id => { document.getElementById(id).value = ''; });
        amcSelect.value = '';
        schemeSelect.value = '';
        readFilters();
        load(1);
    }

    document.getElementById('applyFilters').addEventListener('click', () => { readFilters(); load(1); });
    document.getElementById('resetFilters').addEventListener('click', reset);
    [amcSelect, schemeSelect].forEach(select =>
        select.addEventListener('change', () => { readFilters(); load(1); }));
    document.getElementById('searchBox').addEventListener('keydown', event => {
        if (event.key === 'Enter') { readFilters(); load(1); }
    });

    // deep links: portfolio row / detail page carry amcId, scheme and the range
    const params = new URLSearchParams(location.search);
    state.from = params.get('from') || '';
    state.to = params.get('to') || '';
    state.amcId = params.get('amcId') || '';
    state.scheme = params.get('scheme') || '';
    state.search = params.get('q') || '';
    document.getElementById('fromDate').value = state.from;
    document.getElementById('toDate').value = state.to;
    document.getElementById('searchBox').value = state.search;

    load(1);
})();
