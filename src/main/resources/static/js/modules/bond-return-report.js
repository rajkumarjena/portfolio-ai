// bond-return-report.js - BONDBP B: Bond Returns report.
//  * B.1: Issuer column, resolved from the cached application param list
//    (bond.issuer stores the app-param row id - see bond-form loadIssuers()).
//  * B.2: Bond ref and issuer filter criteria, combined AND with the existing
//    date criteria. The dataset is fetched once and filtered in memory, so
//    every filter change re-renders instantly with no extra API round trip.
//  * B.3: the /api/cashflows response is already scoped to the signed-in
//    user's bonds server-side - nothing here can see another client's rows.
(function () {
  'use strict';

  let allRows = [];              // cash flows, scoped server-side
  let bonds = [];                // for the Bond Ref dropdown
  const issuerNames = new Map(); // app-param id -> issuer name

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const money = n => (Number(n) || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  function issuerOf(row) {
    if (row.issuerId == null || row.issuerId === '') return '-';
    return issuerNames.get(String(row.issuerId)) || String(row.issuerId);
  }

  function renderTable(rows) {
    const tbody = document.querySelector('#reportTable tbody');
    let totalInterest = 0, totalRedemption = 0, totalCredit = 0;

    tbody.innerHTML = rows.map(row => {
      totalInterest += Number(row.payout) || 0;
      totalRedemption += Number(row.amount) || 0;
      totalCredit += (Number(row.payout) || 0) + (Number(row.amount) || 0);
      return `<tr>
        <td>${row.id}</td>
        <td>${esc(row.date)}</td>
        <td class="amt">${money(row.payout)}</td>
        <td>${esc(row.schedule)}</td>
        <td class="amt">${money(row.amount)}</td>
        <td>${row.year}</td>
        <td class="left">${esc(row.bondName)}</td>
        <td class="left">${esc(issuerOf(row))}</td>
        <td class="left">${esc(row.clientName || '-')}</td>
      </tr>`;
    }).join('');

    document.getElementById('summary').innerText =
      `Total Interest: ${money(totalInterest)} | Total Redemption: ${money(totalRedemption)} | Total Credit: ${money(totalCredit)}`;
  }

  function applyFilter() {
    const type = document.getElementById('filterType').value;
    const bondId = document.getElementById('filterBond').value;
    const issuerId = document.getElementById('filterIssuer').value;
    const clientSelect = document.getElementById('filterClients');

    let rows = allRows;

    if (type === 'date') {
      const d = document.getElementById('filterDate').value;
      if (d) rows = rows.filter(r => r.date === d);
    } else if (type === 'month') {
      const m = document.getElementById('filterMonth').value; // YYYY-MM
      if (m) rows = rows.filter(r => r.date && r.date.slice(0, 7) === m);
    } else if (type === 'year') {
      const y = document.getElementById('filterYear').value;
      if (y) rows = rows.filter(r => String(r.year) === y);
    } else if (type === 'range') {
      const start = document.getElementById('filterStart').value;
      const end = document.getElementById('filterEnd').value;
      if (start) rows = rows.filter(r => r.date && r.date >= start);
      if (end) rows = rows.filter(r => r.date && r.date <= end);
    }

    // BONDBP B.2 - bond ref and issuer criteria combine AND with the date one
    if (bondId) rows = rows.filter(r => String(r.bondId) === bondId);
    if (issuerId) rows = rows.filter(r => String(r.issuerId) === issuerId);

    // BONDBP_V1: filter by selected clients (ADMIN/SUPERVISOR)
    if (clientSelect && clientSelect.options.length > 0) {
      const selected = Array.from(clientSelect.selectedOptions).map(o => o.value);
      if (selected.length > 0 && !selected.includes('__ALL__')) {
        rows = rows.filter(r => selected.includes(r.clientName || '-'));
      }
    }

    renderTable(rows);
  }

  function populateBondFilter() {
    const select = document.getElementById('filterBond');
    bonds.forEach(b => {
      const option = document.createElement('option');
      option.value = String(b.id);
      option.textContent = b.bondName || ('Bond #' + b.id);
      select.appendChild(option);
    });
  }

  function populateIssuerFilter() {
    // unique issuers actually held by this user's bonds, names from app param
    const seen = new Set();
    const select = document.getElementById('filterIssuer');
    bonds.forEach(b => {
      const key = b.issuer == null ? '' : String(b.issuer);
      if (!key || seen.has(key)) return;
      seen.add(key);
      const option = document.createElement('option');
      option.value = key;
      option.textContent = issuerNames.get(key) || key;
      select.appendChild(option);
    });
  }

  // Three parallel requests, fetched once; every later filter runs in memory.
  function populateClientFilter() {
    const role = window.APP_USER_ROLE || 'USER';
    if (role !== 'ADMIN' && role !== 'SUPERVISOR') return;
    const wrapper = document.getElementById('clientFilterWrapper');
    const select = document.getElementById('filterClients');
    if (!wrapper || !select) return;
    const seen = new Set();
    (allRows || []).forEach(r => {
      const name = r.clientName || '-';
      if (!seen.has(name)) {
        seen.add(name);
        const opt = document.createElement('option');
        opt.value = name;
        opt.textContent = name;
        select.appendChild(opt);
      }
    });
    wrapper.style.display = 'inline-block';
  }

  Promise.all([
    Api.guard(Api.get('/api/cashflows'), 'Could not load cash flows'),
    Api.guard(Api.get('/api/bonds'), 'Could not load bonds'),
    Api.guard(Api.get('/api/bonds/issuer'), 'Could not load issuers')
  ]).then(([rows, bondRows, issuerRows]) => {
    if (rows) allRows = rows;
    if (bondRows) bonds = bondRows;
    (issuerRows || []).forEach(r => {
      if (r && r.id != null && r.paramValue) issuerNames.set(String(r.id), r.paramValue);
    });

    populateBondFilter();
    populateIssuerFilter();
    populateClientFilter();
    renderTable(allRows);
  });

  window.applyFilter = applyFilter;

  // Show/hide date filter inputs based on selection
  document.getElementById('filterType').addEventListener('change', function () {
    document.getElementById('filterDate').style.display = this.value === 'date' ? 'inline' : 'none';
    document.getElementById('filterMonth').style.display = this.value === 'month' ? 'inline' : 'none';
    document.getElementById('filterYear').style.display = this.value === 'year' ? 'inline' : 'none';
    document.getElementById('filterStart').style.display = this.value === 'range' ? 'inline' : 'none';
    document.getElementById('filterEnd').style.display = this.value === 'range' ? 'inline' : 'none';
  });
})();