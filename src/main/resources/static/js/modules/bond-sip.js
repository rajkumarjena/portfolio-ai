// bond-sip.js - BONDBP_V9 Bond SIP screen: create a lean Bond SIP (#2/#3) and
// read its server-computed sync banner (#5). Instalments are bond records on
// the Bond Records screen now - there is no transaction screen any more.
(function () {
  'use strict';

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const money = n => Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  // Local "YYYY-MM-DD" for the disabled Investment Date field (#3).
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
    el.classList.toggle('error', !ok);
    el.classList.toggle('info', !!ok);
  }

  // BONDBP_V9 #5: green "Bond SIP Transaction is OK" or the pending count.
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

  // Issuer is configurable (app_param_config, param name: issuer) - the same
  // /api/bonds/issuer list the Bond screen uses, served from the App Config
  // object that refreshes every app.config.refreshRateDuration (15 min).
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
    Api.guard(Api.get('/api/bond-sips'), 'Could not load Bond SIPs').then(function (list) {
      if (!list) return;
      const body = tbody();
      if (!body) return;
      body.innerHTML = list.length ? list.map(s => {
        const cancellable = s.status === 'CREATED' || s.status === 'INPROGRESS';
        return `
        <tr data-id="${s.id}">
          <td>${esc(s.issuer)}</td>
          <td>${esc(s.investmentDate)}</td>
          <td>${esc(s.sipDate)}</td>
          <td class="amt">${money(s.sipAmount)}</td>
          <td>${esc(s.frequency)}</td>
          <td><span class="chip">${esc(s.status)}</span></td>
          <td class="left">${cancellable
            ? '<button type="button" class="btn btn-sm sip-cancel"><i data-lucide="x"></i>Cancel</button>'
            : ''}</td>
        </tr>`;
      }).join('')
        : '<tr><td colspan="7" class="muted">No Bond SIPs yet - create one above.</td></tr>';
      if (window.App && window.App.renderIcons) window.App.renderIcons();
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    // #3: Investment Date auto-populates to the current date and is disabled.
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

    // BONDBP_V9 #5: manual Sync Now - same idempotent job the scheduler runs.
    const syncBtn = document.getElementById('syncBtn');
    if (syncBtn) {
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
