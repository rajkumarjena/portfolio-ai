(function () {
    'use strict';
    // MFR_V9 3 - AMC summary with a date range + AMC filter. Without dates the
    // accepted statement snapshot is shown; with dates the numbers are derived
    // from the ledger of that range (cost = sum of amounts, market value =
    // units of the range x last NAV of the range).
    const rowsBody = document.querySelector('#portfolioTable tbody');
    const message = document.getElementById('portfolioMessage');
    const rangeNote = document.getElementById('rangeNote');
    const amcSelect = document.getElementById('amcFilter');
    const allTransactions = document.getElementById('allTransactions');
    const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
    const number = value => Number(value || 0);
    const money = value => number(value).toLocaleString('en-IN', {minimumFractionDigits:2, maximumFractionDigits:2});
    const isBlank = value => value === null || value === undefined || value === '';
    /** AMC of the deep link, applied once the option list has been filled. */
    let pendingAmcId = '';

    function selectedFilters() {
        return {
            from: document.getElementById('fromDate').value,
            to: document.getElementById('toDate').value,
            // the option list arrives with the first response, so a deep-linked
            // AMC is asked for before it can be selected
            amcId: amcSelect.value || pendingAmcId
        };
    }

    function query(filters) {
        const params = new URLSearchParams();
        if (filters.from) params.set('from', filters.from);
        if (filters.to) params.set('to', filters.to);
        if (filters.amcId) params.set('amcId', filters.amcId);
        const text = params.toString();
        return text ? '?' + text : '';
    }

    function syncLinks(filters) {
        const params = new URLSearchParams();
        if (filters.from) params.set('from', filters.from);
        if (filters.to) params.set('to', filters.to);
        if (filters.amcId) params.set('amcId', filters.amcId);
        const text = params.toString();
        allTransactions.href = '/mutualfunds/mf-portfolio-transactions.html' + (text ? '?' + text : '');
    }

    function detailLink(row, filters) {
        const params = new URLSearchParams({amcId: row.amcId});
        if (filters.from) params.set('from', filters.from);
        if (filters.to) params.set('to', filters.to);
        return '/mutualfunds/mf-portfolio-detail.html?' + params.toString();
    }

    async function load() {
        const filters = selectedFilters();
        if (filters.from && filters.to && filters.from > filters.to) {
            message.style.display = 'block';
            message.textContent = 'The From date is after the To date - pick a valid range.';
            return;
        }
        message.style.display = 'block';
        message.classList.remove('error-message');
        message.textContent = 'Loading portfolio summary…';
        syncLinks(filters);
        try {
            const response = await fetch('/api/mf/report/portfolio' + query(filters), {cache:'no-store'});
            if (!response.ok) throw new Error('Unable to load mutual fund portfolio (' + response.status + ').');
            const data = await response.json();
            fillAmcOptions(data.amcs || []);
            const rows = data.rows || [];
            rowsBody.innerHTML = rows.map(row => {
                const name = row.amc || 'AMC not identified';
                const gain = number(row.marketValue) - number(row.costValue);
                return `<tr class="portfolio-row" tabindex="0" role="link" data-href="${esc(detailLink(row, filters))}" aria-label="View ${esc(name)} details">
                    <td class="left">${esc(name)}</td><td class="amt">${money(row.costValue)}</td>
                    <td class="amt">${money(row.marketValue)}</td><td class="amt ${gain >= 0 ? 'pos' : 'neg'}">${money(gain)}</td>
                    <td class="amt">${isBlank(row.txnCount) ? '—' : number(row.txnCount).toLocaleString('en-IN')}</td>
                    <td><a class="small" href="${esc(detailLink(row, filters))}">Details</a></td>
                </tr>`;
            }).join('');
            const total = data.total || {};
            const cost = number(total.costValue);
            const market = number(total.marketValue);
            document.getElementById('totalCost').textContent = money(cost);
            document.getElementById('totalMarket').textContent = money(market);
            document.getElementById('totalGain').textContent = money(market - cost);
            document.getElementById('totalRecords').textContent = rows.reduce(
                (sum, row) => sum + (isBlank(row.txnCount) ? 0 : number(row.txnCount)), 0) || '';
            rangeNote.textContent = data.derived
                ? 'Investments and market value are derived from the transactions between '
                    + (data.from || 'the first transaction') + ' and ' + (data.to || 'today')
                    + ': cost is the sum of the amounts and market value is the units of that range times the last NAV of the range.'
                : '';
            message.style.display = rows.length ? 'none' : 'block';
            message.textContent = rows.length ? '' : 'No accepted mutual fund portfolio is available for this selection. Import and accept a statement first.';
            rowsBody.querySelectorAll('tr[data-href]').forEach(row => {
                const navigate = () => { window.location.href = row.dataset.href; };
                row.addEventListener('click', event => { if (!event.target.closest('a,button')) navigate(); });
                row.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); navigate(); } });
            });
            window.App && App.renderIcons();
        } catch (error) {
            message.style.display = 'block';
            message.textContent = error.message;
            message.classList.add('error-message');
        }
    }

    function fillAmcOptions(amcs) {
        const current = amcSelect.value || pendingAmcId;
        amcSelect.innerHTML = '<option value="">All</option>'
            + amcs.map(amc => `<option value="${esc(amc.id)}">${esc(amc.name)}</option>`).join('');
        amcSelect.value = current;
        pendingAmcId = '';
    }

    function reset() {
        document.getElementById('fromDate').value = '';
        document.getElementById('toDate').value = '';
        amcSelect.value = '';
        load();
    }

    // deep link: the detail page sends the range back so "Back" keeps the view
    const params = new URLSearchParams(location.search);
    if (params.get('from')) document.getElementById('fromDate').value = params.get('from');
    if (params.get('to')) document.getElementById('toDate').value = params.get('to');
    if (params.get('amcId')) pendingAmcId = params.get('amcId');

    document.getElementById('applyFilters').addEventListener('click', load);
    document.getElementById('resetFilters').addEventListener('click', reset);
    load();
})();
