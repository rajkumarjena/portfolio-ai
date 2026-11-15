// bond-sip.js - BONDBP_V9/V10 + BOND_V11 Bond SIP screen
(function () {
  'use strict';

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;');

  const money = n => Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  const role = window.APP_USER_ROLE || 'USER';
  const isAdmin = role === 'ADMIN';
  const isPrivileged = isAdmin || role === 'SUPERVISOR';

  // BOND_V12 #2: the SIP date is the SIP Start Date shown on the screen.
  const HEADERS = ['Issuer', 'Investment Date', 'SIP Start Date', 'SIP Amount', 'Frequency', 'Status']
    .concat(isPrivileged ? ['Client'] : []).concat(['Actions']);

  // BOND_V11 #5: the ids an ADMIN picked in the multi-select (empty = own SIPs).
  let sipClientIds = [];

  function todayIso() {
    const d = new Date();
    const pad = v => String(v).padStart(2, '0');
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  function tbody() {
    return document.querySelector('#sipList tbody');
  }

  function banner(message, ok) {
    const el = document.getElementById('statusBanner');
    if (!el) return;
    el.textContent = message || '';
    el.style.display = message ? 'block' : 'none';
    el.classList.toggle('error', ok === false);
    el.classList.toggle('info', ok === true);
  }

  function renderSyncStatus(status, ok) {
    const el = document.getElementById('syncBanner');
    if (!el || !status) return;
    const isSuccess = ok != null ? ok : status.ok;
    el.textContent = status.message || '';
    el.style.display = status.message ? 'block' : 'none';
    el.classList.toggle('error', !isSuccess);
    el.classList.toggle('info', !!isSuccess);
  }

  // BOND_V11 #4: the sync scope - the selected clients for an ADMIN, otherwise
  // the signed-in client (the server ignores ids from a non-admin anyway).
  function scopeQuery() {
    return isPrivileged && sipClientIds.length
      ? 'clientIds=' + encodeURIComponent(sipClientIds.join(','))
      : '';
  }

  function loadSyncStatus() {
    const query = scopeQuery();
    return Api.guard(Api.get('/api/bond-sips/sync-status' + (query ? '?' + query : '')),
      'Could not read the sync status')
      .then(function (status) {
        if (status) renderSyncStatus(status);
        return status;
      });
  }

  function loadIssuers() {
    const select = document.getElementById('issuer');
    if (!select) return;
    Api.guard(Api.get('/api/bonds/issuer'), 'Could not load issuers').then(function (rows) {
      if (!rows) return;
      select.innerHTML = '<option value="" disabled selected>-- Select Issuer --</option>';
      rows.forEach(function (row) {
        if (!row || !row.paramValue) return;
        const option = document.createElement('option');
        option.value = row.paramValue;
        option.textContent = row.paramValue;
        select.appendChild(option);
      });
    });
  }

  function load() {
    const query = scopeQuery();
    Api.guard(Api.get('/api/bond-sips' + (query ? '?' + query : '')), 'Could not load Bond SIPs')
      .then(function (list) {
        if (!list) return;
        const head = document.querySelector('#sipList thead');
        if (head) {
          head.innerHTML = '<tr>' + HEADERS.map(h => '<th>' + h + '</th>').join('') + '</tr>';
        }
        const body = tbody();
        if (!body) return;
        if (!list.length) {
          body.innerHTML = `<tr><td colspan="${HEADERS.length}" class="muted">No Bond SIPs yet - create one above.</td></tr>`;
        } else {
          body.innerHTML = list.map(s => {
            const cancellable = s.status === 'CREATED' || s.status === 'INPROGRESS';
            const clientCell = isPrivileged
              ? `<td class="left">${esc((s.clientName || '') + (s.clientPan ? ' [' + s.clientPan + ']' : '')) || '-'}</td>`
              : '';
            return `
        <tr data-id="${s.id}">
          <td>${esc(s.issuer)}</td>
          <td>${esc(s.investmentDate)}</td>
          <td>${esc(s.sipDate)}</td>
          <td class="amt">${money(s.sipAmount)}</td>
          <td>${esc(s.frequency)}</td>
          <td><span class="chip">${esc(s.status)}</span></td>
          ${clientCell}
          <td class="left">${cancellable
              ? '<button type="button" class="btn btn-sm sip-cancel"><i data-lucide="x"></i>Cancel</button>'
              : ''}</td>
        </tr>`;
          }).join('');
        }
        if (window.App && window.App.renderIcons) window.App.renderIcons();
      });
  }

  function selectedClientIds() {
    const select = document.getElementById('sipFilterClients');
    if (!select) return [];
    return Array.from(select.selectedOptions || [])
      .map(option => option.value)
      .filter(value => value != null && value !== '');
  }

  function applySelection() {
    sipClientIds = selectedClientIds();
    load();
    loadSyncStatus();
  }

  function populateSipClientFilter() {
    const wrapper = document.getElementById('sipClientFilterWrapper');
    const select = document.getElementById('sipFilterClients');
    if (!wrapper || !select) return;
    // BOND_V11 #2: only an ADMIN picks other clients; everyone else sees the
    // Sync Now button with an implicit "my SIPs" scope.
    if (!isPrivileged) return;
    Api.guard(Api.get('/api/clients'), 'Could not load clients').then(clients => {
      if (!clients) return;
      select.innerHTML = '';
      clients.forEach(c => {
        if (!c || c.id == null) return;
        const option = document.createElement('option');
        option.value = String(c.id);
        option.textContent = (c.name || '') + (c.pan ? ' [' + c.pan + ']' : '');
        // #5: pre-select the signed-in admin, who can add or remove anyone.
        if (window.APP_USER_PAN && c.pan === window.APP_USER_PAN) {
          option.selected = true;
        }
        select.appendChild(option);
      });
      wrapper.style.display = 'flex';
      sipClientIds = selectedClientIds();
      load();
      loadSyncStatus();
      select.addEventListener('change', applySelection);
    });
  }

  function syncNow() {
    const btn = document.getElementById('syncBtn');
    if (btn) btn.disabled = true;
    banner('');
    const payload = isPrivileged && sipClientIds.length ? { clientIds: sipClientIds } : {};
    Api.guard(Api.post('/api/bond-sips/reconcile', payload), 'Sync failed')
      .then(function (result) {
        if (result) {
          renderSyncStatus(result, result.ok);
          if (result.created > 0) {
            banner(result.created + ' missing instalment record(s) were created.', true);
          } else {
            banner('No missing instalment record - Bond Records is already up to date.', true);
          }
          load();
        }
        if (btn) btn.disabled = false;
      });
  }

  document.addEventListener('DOMContentLoaded', function () {
    const investmentDate = document.getElementById('investmentDate');
    if (investmentDate) investmentDate.value = todayIso();

    const form = document.getElementById('bondSipForm');
    if (form) {
      form.addEventListener('submit', function (event) {
        event.preventDefault();
        banner('');
        const payload = {
          issuer: document.getElementById('issuer').value.trim(),
          sipDate: document.getElementById('sipDate').value,
          sipAmount: Number(document.getElementById('sipAmount').value),
          frequency: document.getElementById('frequency').value
        };
        Api.guard(Api.post('/api/bond-sips', payload), 'Could not create Bond SIP')
          .then(function (created) {
            if (!created) return;
            banner('Bond SIP created with status ' + created.status + '.', true);
            form.reset();
            if (investmentDate) investmentDate.value = todayIso();
            load();
            loadSyncStatus();
          });
      });
    }

    loadIssuers();
    populateSipClientFilter();
    load();
    loadSyncStatus();

    const body = tbody();
    if (body) {
      body.addEventListener('click', function (event) {
        const button = event.target.closest('.sip-cancel');
        if (!button) return;
        const row = button.closest('tr');
        const id = row.getAttribute('data-id');
        if (!id) return;
        if (!confirm('Cancel this Bond SIP?')) return;
        banner('');
        Api.guard(Api.post('/api/bond-sips/' + id + '/cancel', {}), 'Could not cancel Bond SIP')
          .then(function (res) {
            if (!res) return;
            banner('Bond SIP cancelled.', true);
            load();
          });
      });
    }

    // BOND_V11 #3: every client may run Sync Now for their own Bond SIPs.
    const syncBtn = document.getElementById('syncBtn');
    if (syncBtn) syncBtn.addEventListener('click', syncNow);

    if (window.App && window.App.renderIcons) window.App.renderIcons();
  });
})();
