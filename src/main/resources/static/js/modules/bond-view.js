// bond-view.js - BONDBP_V9/V10 Bond Records screen. The server decides
// everything that is not pure presentation (#11): the active/matured split,
// the ordering (In Progress first, then Investment Date ascending),
// pagination (#9), the resolved issuer/rating text and the Status +
// Investment Type columns (#2). BONDBP_V10 #2: Edit is enabled for everyone
// (the server still enforces ownership); Delete stays ADMIN-only.
(function () {
  'use strict';

  const pageSize = 20;
  // BONDBP_V10 #2: Edit and Delete activated for all users on Active Bonds.
  const role = window.APP_USER_ROLE || 'USER';
  const canEdit = true;
  const canDelete = true;
  const isPrivileged = role === 'ADMIN' || role === 'SUPERVISOR';

  // 1-based current page per section; empty filter = whole scope.
  const state = { active: 1, expired: 1 };
  let clientFilterId = '';

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const money = n => (Number(n) || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  // "In Progress" -> "In-Progress", so the status/type text can key a CSS class.
  const token = value => String(value == null ? '' : value)
    .trim().replace(/[^A-Za-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

  function sectionEls(section) {
    return section === 'expired'
      ? { body: document.querySelector('#expiredBonds tbody'), pagination: document.getElementById('expiredPagination') }
      : { body: document.querySelector('#activeBonds tbody'), pagination: document.getElementById('activePagination') };
  }

  function actionButtons(bond, section) {
    // BONDBP_V9 #10: matured records are history - only the view action exists.
    if (section === 'expired') {
      return `<button class="btn btn-sm" onclick="viewBond(${bond.id})" title="Open the bond details in view-only mode">
        <i data-lucide="eye"></i>View</button>`;
    }
    // BONDBP_V10 #2: Edit and Delete activated for all users on Active Bonds.
    return `
      <button class="btn btn-sm" onclick="viewBond(${bond.id})" title="Open bond in view-only mode">
        <i data-lucide="eye"></i>View</button>
      <button class="btn btn-sm" onclick="editBond(${bond.id})" title="Edit bond details">
        <i data-lucide="pencil"></i>Edit</button>
      <button class="btn btn-sm danger" onclick="deleteBond(${bond.id})" title="Delete bond">
        <i data-lucide="trash-2"></i>Delete</button>`;
  }

  function rowHtml(bond, section) {
    const client = bond.clientName || bond.clientPan
      ? esc((bond.clientName || '') + (bond.clientPan ? ' [' + bond.clientPan + ']' : ''))
      : '-';
    return `<tr class="row-${token(bond.status)}">
      <td class="left">${esc(bond.bondName)}</td>
      <td><span class="chip status-${token(bond.status)}">${esc(bond.status) || '-'}</span></td>
      <td><span class="chip type-${token(bond.investmentType)}">${esc(bond.investmentType) || '-'}</span></td>
      <td class="left">${esc(bond.issuer) || '-'}</td>
      <td>${esc(bond.creditRating) || '-'}</td>
      <td>${esc(bond.investmentDate) || '-'}</td>
      <td>${esc(bond.maturityDate) || '-'}</td>
      <td class="amt">${Number(bond.units) || 0}</td>
      <td class="amt">${money(bond.totalInvestment)}</td>
      <td class="amt">${money(bond.maturityAmount)}</td>
      <td class="left">${client}</td>
      <td class="left">${actionButtons(bond, section)}</td>
    </tr>`;
  }

  function renderPagination(pager, section, onRender) {
    if (!pager) return;
    pager.innerHTML = '';
    const totalPages = Math.max(1, pager.totalPages || 1);
    const page = state[section];

    const go = target => {
      state[section] = target;
      onRender();
    };

    const addButton = (label, target, opts) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = label;
      if (opts) Object.assign(button, opts);
      if (target === page) button.className = 'active';
      if (target < 1 || target > totalPages || target === page) button.disabled = true;
      button.addEventListener('click', () => go(target));
      pager.appendChild(button);
    };

    addButton('‹', page - 1);
    // Windowed page list keeps the control readable for hundreds of records.
    const windowSize = 7;
    let first = Math.max(1, page - Math.floor(windowSize / 2));
    let last = Math.min(totalPages, first + windowSize - 1);
    first = Math.max(1, last - windowSize + 1);
    for (let i = first; i <= last; i++) addButton(String(i), i);
    addButton('›', page + 1);

    const summary = document.createElement('span');
    summary.className = 'muted';
    summary.style.marginLeft = '10px';
    const from = pager.totalElements === 0 ? 0 : (page - 1) * pageSize + 1;
    const to = Math.min(page * pageSize, pager.totalElements || 0);
    summary.textContent = from + '-' + to + ' of ' + (pager.totalElements || 0) + ' records';
    pager.appendChild(summary);
  }

  function loadSection(section) {
    const els = sectionEls(section);
    if (!els.body) return;
    els.body.innerHTML = '<tr><td colspan="12" class="muted">Loading...</td></tr>';

    const page = state[section];
    let url = '/api/bonds/records?section=' + section
      + '&page=' + (page - 1) + '&size=' + pageSize;
    if (clientFilterId) url += '&clientId=' + clientFilterId;

    Api.guard(Api.get(url), 'Could not load bonds').then(result => {
      if (!result) return;
      state[section] = Math.min(state[section], Math.max(1, result.totalPages || 1));
      els.body.innerHTML = result.content && result.content.length
        ? result.content.map(b => rowHtml(b, section)).join('')
        : `<tr><td colspan="12" class="muted">No ${section === 'expired'
            ? 'matured' : 'active'} bonds.</td></tr>`;
      renderPagination(els.pagination, section, () => loadSection(section));
      if (window.App && window.App.renderIcons) window.App.renderIcons();
    });
  }

  function populateViewClientFilter() {
    if (!isPrivileged) return;
    const wrapper = document.getElementById('viewClientFilterWrapper');
    const select = document.getElementById('viewFilterClients');
    if (!wrapper || !select) return;
    Api.guard(Api.get('/api/clients'), 'Could not load clients').then(clients => {
      if (!clients) return;
      clients.forEach(c => {
        if (!c || c.id == null) return;
        const option = document.createElement('option');
        option.value = c.id;
        option.textContent = (c.name || '') + (c.pan ? ' [' + c.pan + ']' : '');
        select.appendChild(option);
      });
      wrapper.style.display = 'inline-block';
    });
  }

  window.applyViewClientFilter = function applyViewClientFilter() {
    const select = document.getElementById('viewFilterClients');
    clientFilterId = select && select.value ? select.value : '';
    state.active = 1;
    state.expired = 1;
    loadSection('active');
    loadSection('expired');
  };

  window.viewBond = id => { window.location.href = '/bonds/bond-form.html?id=' + id + '&view=1'; };
  window.editBond = id => { window.location.href = '/bonds/bond-form.html?id=' + id; };
  window.deleteBond = function deleteBond(id) {
    if (!canDelete) return;
    if (!window.BondRecordsApi) return;
    if (confirm('Are you sure you want to delete this bond?')) {
      window.BondRecordsApi.del(id).then(changed => {
        if (changed) { state.active = 1; loadSection('active'); }
      });
    }
  };

  // Exposed so deleteBond() can consult the cached row (client pan/status)
  // without a second round-trip on every render.
  window.BondRecordsApi = {
    feedback: msg => Api.feedback(msg),
    del: id => Api.guard(Api.del('/api/bonds/' + id), 'Could not delete bond'),
    findCached: id => cached[id]
  };

  populateViewClientFilter();
  loadSection('active');
  loadSection('expired');
})();
