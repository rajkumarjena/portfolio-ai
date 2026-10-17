// bond-view.js - BONDBP A: Bond records list.
//  * A.1-A.3: Credit Rating, Units, Total Investment (= amt/unit x units) and
//    Maturity Amount (= sum of payout + amount from bond_cash_flow) columns on
//    both the Active and the Expired/Matured panels.
//  * A.4: issuer id resolved to the issuer NAME through the cached application
//    param list (/api/bonds/issuer) - no extra database query per row.
//  * A.5/A.6: role ladder from window.APP_USER_ROLE - View for everyone,
//    Edit for SUPERVISOR+, Delete for ADMIN only (mirrored server-side).
(function () {
  'use strict';

  const pageSize = 20;
  const role = window.APP_USER_ROLE || 'USER';
  const canEdit = role === 'SUPERVISOR' || role === 'ADMIN';
  const canDelete = role === 'ADMIN';

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const money = n => (Number(n) || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  // id -> display-name map: bond.issuer and bond.creditRating both store the
  // app-param row id (see bond-form loadIssuers / loadCreditRating).
  const nameMap = rows => {
    const map = new Map();
    (rows || []).forEach(r => {
      if (r && r.id != null && r.paramValue) map.set(String(r.id), r.paramValue);
    });
    return map;
  };
  const resolve = (map, raw) => (raw == null || raw === '' ? '-'
    : (map.get(String(raw)) || String(raw)));

  function actionButtons(bond) {
    return `
      <button class="btn btn-sm" onclick="viewBond(${bond.id})" title="Open bond in view-only mode">
        <i data-lucide="eye"></i>View</button>
      <button class="btn btn-sm" onclick="editBond(${bond.id})" ${canEdit ? '' : 'disabled'}
        title="${canEdit ? 'Edit bond' : 'Editing requires the SUPERVISOR role'}">
        <i data-lucide="pencil"></i>Edit</button>
      <button class="btn btn-sm danger" onclick="deleteBond(${bond.id})" ${canDelete ? '' : 'disabled'}
        title="${canDelete ? 'Delete bond' : 'Deleting requires the ADMIN role'}">
        <i data-lucide="trash-2"></i>Delete</button>`;
  }

  function rowHtml(bond, issuerNames, ratingNames) {
    const totalInvestment = (Number(bond.investmentAmountPerUnit) || 0) * (Number(bond.units) || 0);
    // Maturity Amount = sum of payout + amount across bond_cash_flow rows
    const maturityAmount = (bond.cashFlowData || []).reduce(
      (sum, cf) => sum + (Number(cf.payout) || 0) + (Number(cf.amount) || 0), 0);

    return `<tr>
      <td class="left">${esc(bond.bondName)}</td>
      <td class="left">${esc(resolve(issuerNames, bond.issuer))}</td>
      <td>${esc(resolve(ratingNames, bond.creditRating))}</td>
      <td>${esc(bond.investmentDate)}</td>
      <td>${esc(bond.maturityDate)}</td>
      <td class="amt">${Number(bond.units) || 0}</td>
      <td class="amt">${money(totalInvestment)}</td>
      <td class="amt">${money(maturityAmount)}</td>
      <td class="left">${esc(bond.clientName || bond.clientPan || '-')}</td>
      <td class="left">${actionButtons(bond)}</td>
    </tr>`;
  }

  function renderTable(bonds, tableId, paginationId, issuerNames, ratingNames) {
    const tableBody = document.querySelector(`#${tableId} tbody`);
    const pagination = document.getElementById(paginationId);

    function displayPage(page) {
      const start = (page - 1) * pageSize;
      const pageData = bonds.slice(start, start + pageSize);
      tableBody.innerHTML = pageData
        .map(b => rowHtml(b, issuerNames, ratingNames)).join('');

      pagination.innerHTML = '';
      for (let i = 1; i <= Math.ceil(bonds.length / pageSize); i++) {
        pagination.innerHTML += `<button ${i === page ? 'class="active"' : ''}
          onclick="window.__renderPage('${tableId}', ${i})">${i}</button>`;
      }
      window.App && App.renderIcons();
    }

    window['__renderPage_' + tableId] = displayPage;
    displayPage(1);
  }

  // Three cached lookups plus the bond list - served from the in-memory app
  // config cache, none of them hits the database again (BONDBP A.4).
  Promise.all([
    Api.guard(Api.get('/api/bonds'), 'Could not load bonds'),
    Api.guard(Api.get('/api/bonds/issuer'), 'Could not load issuers'),
    Api.guard(Api.get('/api/bonds/credit/rating'), 'Could not load credit ratings')
  ]).then(([bonds, issuers, ratings]) => {
    if (!bonds) return;
    const issuerNames = nameMap(issuers);
    const ratingNames = nameMap(ratings);

    const today = new Date();
    const active = bonds.filter(b => b.maturityDate && new Date(b.maturityDate) >= today);
    const expired = bonds.filter(b => b.maturityDate && new Date(b.maturityDate) < today);

    renderTable(active, 'activeBonds', 'activePagination', issuerNames, ratingNames);
    renderTable(expired, 'expiredBonds', 'expiredPagination', issuerNames, ratingNames);
  });

  // Bridge so the inline pagination handlers can re-render their own table.
  window.__renderPage = (tableId, page) => {
    const fn = window['__renderPage_' + tableId];
    if (fn) fn(page);
  };

  // BONDBP A.5: View opens the bond entry screen read-only.
  function viewBond(id) {
    window.location.href = '/bonds/bond-form.html?id=' + id + '&view=1';
  }

  function editBond(id) {
    // Redirect to bond-form with id for editing
    window.location.href = '/bonds/bond-form.html?id=' + id;
  }

  function deleteBond(id) {
    if (!canDelete) return; // BONDBP A.5 - the server 403s anyway
    if (confirm('Are you sure you want to delete this bond?')) {
      Api.guard(Api.del('/api/bonds/' + id), 'Could not delete bond')
        .then(res => { if (res !== null) location.reload(); });
    }
  }

  window.viewBond = viewBond;
  window.editBond = editBond;
  window.deleteBond = deleteBond;
})();