(function () {
    'use strict';
    // MFR_V9 4/5/6/7 / MFR_V10 1/4 - one AMC's report with a date range and a
    // scheme filter. The schemes table shows the cost / market value of the
    // range; the ledger shows one page of 50 rows inside a table that scrolls
    // vertically, with numbered page controls underneath, so a statement with
    // thousands of rows no longer makes the page itself thousands of rows long.
    const PAGE_SIZE = 50;
    const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
    const number = value => Number(value || 0);
    const money = value => number(value).toLocaleString('en-IN', {minimumFractionDigits:2, maximumFractionDigits:2});
    const units = value => number(value).toLocaleString('en-IN', {minimumFractionDigits:0, maximumFractionDigits:6});
    const isBlank = value => value === null || value === undefined || value === '';
    const amountText = value => isBlank(value) ? '—' : money(value);
    const unitsText = value => isBlank(value) ? '—' : units(value);

    const message = document.getElementById('detailMessage');
    const rangeNote = document.getElementById('rangeNote');
    const more = document.getElementById('transactionMore');
    const pager = document.getElementById('transactionPager');
    const scrollBox = document.getElementById('transactionScroll');
    const schemeSelect = document.getElementById('schemeFilter');
    const txnBody = document.querySelector('#transactionTable tbody');
    const state = {
        amcId: new URLSearchParams(location.search).get('amcId') || '',
        from: '', to: '', scheme: '',
        page: 1, pages: 1, total: 0, busy: false
    };

    function requestUrl(page) {
        const params = new URLSearchParams({page: String(page), size: String(PAGE_SIZE)});
        if (state.from) params.set('from', state.from);
        if (state.to) params.set('to', state.to);
        if (state.scheme) params.set('scheme', state.scheme);
        return '/api/mf/report/portfolio/' + encodeURIComponent(state.amcId) + '?' + params.toString();
    }

    function syncLinks() {
        const back = new URLSearchParams();
        if (state.from) back.set('from', state.from);
        if (state.to) back.set('to', state.to);
        if (state.amcId) back.set('amcId', state.amcId);
        document.getElementById('backLink').href =
            '/mutualfunds/mf-portfolio.html' + (back.toString() ? '?' + back.toString() : '');

        const all = new URLSearchParams();
        if (state.amcId) all.set('amcId', state.amcId);
        if (state.from) all.set('from', state.from);
        if (state.to) all.set('to', state.to);
        if (state.scheme) all.set('scheme', state.scheme);
        document.getElementById('allTransactions').href =
            '/mutualfunds/mf-portfolio-transactions.html' + (all.toString() ? '?' + all.toString() : '');
    }

    function remember() {
        const params = new URLSearchParams(location.search);
        ['from', 'to', 'scheme'].forEach(key => {
            if (state[key]) params.set(key, state[key]); else params.delete(key);
        });
        params.set('amcId', state.amcId);
        history.replaceState(null, '', location.pathname + '?' + params.toString());
    }

    async function load(page) {
        if (state.busy) return;
        if (!state.amcId || !/^\d+$/.test(state.amcId)) {
            message.textContent = 'A valid mutual fund selection is required.';
            return;
        }
        if (state.from && state.to && state.from > state.to) {
            message.style.display = 'block';
            message.textContent = 'The From date is after the To date - pick a valid range.';
            return;
        }
        state.busy = true;
        try {
            const response = await fetch(requestUrl(page), {cache:'no-store'});
            if (!response.ok) {
                throw new Error(response.status === 404 ? 'This mutual fund portfolio was not found.'
                    : 'Unable to load portfolio details (' + response.status + ').');
            }
            const data = await response.json();
            message.style.display = 'none';
            renderReport(data);
            renderTransactions(data.transactions || []);
            const paging = data.pagination || {};
            state.page = page;
            state.total = paging.total || 0;
            state.pages = Math.max(1, Math.ceil(state.total / PAGE_SIZE));
            updateMore(paging);
            renderPager();
            window.App && App.renderIcons();
        } catch (error) {
            message.style.display = 'block';
            message.textContent = error.message;
            message.classList.add('error-message');
        } finally {
            state.busy = false;
        }
    }

    function renderReport(data) {
        document.getElementById('detailHeading').textContent = (data.amc || 'Mutual Fund') + ' — Portfolio Details';
        const gain = number(data.marketValue) - number(data.costValue);
        document.getElementById('portfolioCards').innerHTML = `
            <div class="card"><div class="label">Schemes</div><div class="value">${(data.schemes || []).length}</div></div>
            <div class="card"><div class="label">Records</div><div class="value">${number(data.txnCount).toLocaleString('en-IN')}</div></div>
            <div class="card"><div class="label">Cost Value (₹)</div><div class="value">${money(data.costValue)}</div></div>
            <div class="card blue"><div class="label">Market Value (₹)</div><div class="value">${money(data.marketValue)}</div></div>
            <div class="card ${gain >= 0 ? 'green' : 'red'}"><div class="label">Gain / Loss (₹)</div>
                <div class="value ${gain >= 0 ? 'pos' : 'neg'}">${money(gain)}</div></div>`;
        rangeNote.textContent = data.derived
            ? 'Cost and market value cover ' + (data.from || 'the first transaction') + ' to '
                + (data.to || 'today') + ': cost is the sum of the amounts, market value is the units of '
                + 'that range times the last NAV of the range for each scheme.'
            : '';
        fillSchemes(data.schemeNames || []);
        document.querySelector('#schemeTable tbody').innerHTML = (data.schemes || []).map(row => `<tr>
            <td>${esc(row.folioNo)}</td><td class="left">${esc(row.scheme)}</td><td>${esc(row.isin)}</td>
            <td class="amt">${unitsText(row.closeUnits)}</td><td class="amt">${unitsText(row.units)}</td>
            <td class="amt">${number(row.txnCount).toLocaleString('en-IN')}</td>
            <td>${esc(row.navDate)}</td><td class="amt">${amountText(row.nav)}</td>
            <td class="amt">${amountText(row.costValue)}</td><td class="amt">${amountText(row.marketValue)}</td>
        </tr>`).join('') || '<tr><td colspan="10" class="muted">No scheme holdings match this selection.</td></tr>';
        syncLinks();
    }

    function fillSchemes(names) {
        schemeSelect.innerHTML = '<option value="">All</option>'
            + names.map(name => `<option value="${esc(name)}">${esc(name)}</option>`).join('');
        schemeSelect.value = state.scheme;
    }

    function renderTransactions(rows) {
        txnBody.innerHTML = rows.map(row => `<tr>
            <td>${esc(row.date)}</td><td class="left">${esc(row.scheme)}</td><td>${esc(row.type)}</td>
            <td class="left">${esc(row.description)}</td><td class="amt">${unitsText(row.units)}</td>
            <td class="amt">${amountText(row.nav)}</td><td class="amt">${amountText(row.amount)}</td>
            <td class="amt">${unitsText(row.balance)}</td></tr>`).join('')
            || '<tr><td colspan="8" class="muted">No transactions match this selection.</td></tr>';
        scrollBox.scrollTop = 0;
    }

    function updateMore(paging) {
        if (!state.total) {
            more.textContent = '';
            return;
        }
        const from = (paging.from || 0) + 1;
        const to = paging.to || state.total;
        more.textContent = state.pages > 1
            ? `Showing ${from.toLocaleString('en-IN')}–${to.toLocaleString('en-IN')} of `
                + `${state.total.toLocaleString('en-IN')} transactions — scroll inside the table for the rest of this page.`
            : `${state.total.toLocaleString('en-IN')} transaction(s).`;
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
        state.scheme = schemeSelect.value;
        remember();
    }

    function reset() {
        document.getElementById('fromDate').value = '';
        document.getElementById('toDate').value = '';
        schemeSelect.value = '';
        state.scheme = '';
        readFilters();
        load(1);
    }

    document.getElementById('applyFilters').addEventListener('click', () => { readFilters(); load(1); });
    document.getElementById('resetFilters').addEventListener('click', reset);
    schemeSelect.addEventListener('change', () => { readFilters(); load(1); });

    const params = new URLSearchParams(location.search);
    state.from = params.get('from') || '';
    state.to = params.get('to') || '';
    state.scheme = params.get('scheme') || '';
    document.getElementById('fromDate').value = state.from;
    document.getElementById('toDate').value = state.to;
    syncLinks();
    load(1);
})();
