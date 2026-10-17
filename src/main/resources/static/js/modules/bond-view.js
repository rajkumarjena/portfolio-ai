// bond-view.js - BONDBP A: Bond records list.
(function () {
  'use strict';

  const pageSize = 20;
  const role = window.APP_USER_ROLE || 'USER';
  const canEdit = role === 'SUPERVISOR' || role === 'ADMIN';
  const canDelete = role === 'ADMIN';
  const isPrivileged = role === 'ADMIN' || role === 'SUPERVISOR';

  let allBonds = [];
  let issuerNamesMap;
  let ratingNamesMap;

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const money = n => (Number(n) || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  const nameMap = rows => {
    const map = new Map();
    (rows || []).forEach(r => {
      if (r && r.id != null && r.paramValue) map.set(String(r.id), r.paramValue);
    });
    return map;
  };
  const resolve = (map, raw) => (raw == null || raw === '' ? '-' : (map.get(String(raw)) || String(raw)));

  function clientDisplayBond(bond) {
    if (bond.clientName && bond.clientPan) return bond.clientName + ' [' + bond.clientPan + ']';
    if (bond.clientName) return bond.clientName;
    if (bond.clientPan) return bond.clientPan;
    return '-';
  }

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
      <td class="left">${esc(clientDisplayBond(bond))}</td>
      <td class="left">${actionButtons(bond)}</td>
    </tr>`;
  }

  function renderTablesForBonds(bonds, issuerNames, ratingNames) {
    const tableBodyActive = document.querySelector('#activeBonds tbody');
    const paginationActive = document.getElementById('activePagination');
    const tableBodyExpired = document.querySelector('#expiredBonds tbody');
    const paginationExpired = document.getElementById('expiredPagination');

    const today = new Date();
    const active = bonds.filter(b => b.maturityDate && new Date(b.maturityDate) >= today);
    const expired = bonds.filter(b => b.maturityDate && new Date(b.maturityDate) < today);

    function renderTableSlice(data, tableBody, pagination, tableId) {
      function displayPage(page) {
        const start = (page - 1) * pageSize;
        const pageData = data.slice(start, start + pageSize);
        tableBody.innerHTML = pageData.map(b => rowHtml(b, issuerNames, ratingNames)).join('');
        pagination.innerHTML = '';
        const totalPages = Math.max(1, Math.ceil(data.length / pageSize));
        for (let i = 1; i <= totalPages; i++) {
          const cls = i === page ? 'active' : '';
          pagination.innerHTML += '<button class="' + cls + '" onclick="window.__renderPage_' + tableId + '(' + i + ')">' + i + '</button>';
        }
        if (window.App && window.App.renderIcons) window.App.renderIcons();
      }
      window['__renderPage_' + tableId] = displayPage;
      displayPage(1);
    }

    renderTableSlice(active, tableBodyActive, paginationActive, 'active');
    renderTableSlice(expired, tableBodyExpired, paginationExpired, 'expired');
  }

  function populateViewClientFilter() {
    if (!isPrivileged) return;
    const wrapper = document.getElementById('viewClientFilterWrapper');
    const select = document.getElementById('viewFilterClients');
    if (!wrapper || !select) return;
    const seen = new Set();
    (allBonds || []).forEach(b => {
      const disp = clientDisplayBond(b);
      if (!seen.has(disp)) {
        seen.add(disp);
        const opt = document.createElement('option');
        opt.value = disp;
        opt.textContent = disp;
        select.appendChild(opt);
      }
    });
    wrapper.style.display = 'inline-block';
  }

  function applyViewClientFilter() {
    const select = document.getElementById('viewFilterClients');
    if (!select) {
      renderTablesForBonds(allBonds, issuerNamesMap, ratingNamesMap);
      return;
    }
    const selected = Array.from(select.selectedOptions).map(o => o.value);
    let filtered = allBonds;
    if (selected.length > 0 && !selected.includes('__ALL__')) {
      filtered = allBonds.filter(b => selected.includes(clientDisplayBond(b)));
    }
    renderTablesForBonds(filtered, issuerNamesMap, ratingNamesMap);
  }

  Promise.all([
    Api.guard(Api.get('/api/bonds'), 'Could not load bonds'),
    Api.guard(Api.get('/api/bonds/issuer'), 'Could not load issuers'),
    Api.guard(Api.get('/api/bonds/credit/rating'), 'Could not load credit ratings')
  ]).then(([bonds, issuers, ratings]) => {
    if (!bonds) return;
    allBonds = bonds;
    issuerNamesMap = nameMap(issuers);
    ratingNamesMap = nameMap(ratings);
    populateViewClientFilter();
    renderTablesForBonds(allBonds, issuerNamesMap, ratingNamesMap);
  });

  function viewBond(id) { window.location.href = '/bonds/bond-form.html?id=' + id + '&view=1'; }
  function editBond(id) { window.location.href = '/bonds/bond-form.html?id=' + id; }
  function deleteBond(id) {
    if (!canDelete) return;
    if (confirm('Are you sure you want to delete this bond?')) {
      Api.guard(Api.del('/api/bonds/' + id), 'Could not delete bond').then(res => { if (res !== null) location.reload(); });
    }
  }

  window.viewBond = viewBond;
  window.editBond = editBond;
  window.deleteBond = deleteBond;
  window.applyViewClientFilter = applyViewClientFilter;
})();
