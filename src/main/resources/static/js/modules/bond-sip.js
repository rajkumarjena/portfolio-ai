// bond-sip.js - BONDBP_V9/V10 Bond SIP screen
(function () {
  'use strict';

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;');

  const money = n => Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  const role = window.APP_USER_ROLE || 'USER';
  const isAdmin = role === 'ADMIN';
  const isPrivileged = isAdmin || role === 'SUPERVISOR';

  const HEADERS = ['Issuer', 'Investment Date', 'SIP Date', 'SIP Amount', 'Frequency', 'Status']
    .concat(isPrivileged ? ['Client'] : []).concat(['Actions']);

  let sipClientFilterId = '';

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

  function loadSyncStatus() {
    return Api.guard(Api.get('/api/bond-sips/sync-status'), 'Could not read the sync status')
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

  let sipFilterMode = 'me';

  function load() {
    let url = '/api/bond-sips';
    if (sipFilterMode === 'all') {
      url += '?all=true';
    } else if (sipFilterMode && sipFilterMode !== 'me') {
      url += '?clientId=' + encodeURIComponent(sipFilterMode);
    }
    Api.guard(Api.get(url), 'Could not load Bond SIPs').then(function (list) {
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

  function populateSipClientFilter() {
    if (!isPrivileged) return;
    const wrapper = document.getElementById('sipClientFilterWrapper');
    const select = document.getElementById('sipFilterClients');
    const btn = document.getElementById('sipFilterBtn');
    if (!wrapper || !select) return;
    Api.guard(Api.get('/api/clients'), 'Could not load clients').then(clients => {
      if (!clients) return;
      // clear existing options except first two
      while (select.options.length > 2) {
        select.remove(2);
      }
      clients.forEach(c => {
        if (!c || c.id == null) return;
        // skip if it's the current user? not necessary
        const option = document.createElement('option');
        option.value = c.id;
        option.textContent = (c.name || '') + (c.pan ? ' [' + c.pan + ']' : '');
        select.appendChild(option);
      });
      wrapper.style.display = 'flex';
      wrapper.style.alignItems = 'center';
      wrapper.style.gap = '6px';
    });
    if (btn) {
      btn.onclick = function () {
        const selectEl = document.getElementById('sipFilterClients');
        sipFilterMode = selectEl && selectEl.value ? selectEl.value : 'me';
        load();
      };
    }
    select.onchange = function () {
      sipFilterMode = select.value || 'me';
      load();
    };
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

    const syncBtn = document.getElementById('syncBtn');
    if (syncBtn && !isAdmin) {
      syncBtn.hidden = true;
    }
    if (syncBtn && isAdmin) {
      syncBtn.addEventListener('click', function () {
        syncBtn.disabled = true;
        banner('');
        Api.guard(Api.post('/api/bond-sips/reconcile', {}), 'Sync failed')
          .then(function (result) {
            if (result) {
              renderSyncStatus(result, result.ok);
              if (result.created > 0) {
                banner(result.created + ' missing instalment record(s) were created.', true);
              }
              load();
            }
            syncBtn.disabled = false;
          });
      });
    }

    if (window.App && window.App.renderIcons) window.App.renderIcons();
  });
})();
