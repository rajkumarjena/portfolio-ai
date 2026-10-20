// bond-sip.js - BONDBP_V7 Bond SIP screen: create a SIP (#2/#3) and list the
// signed-in client's SIPs with their derived Bond Name / Actual SIP Amount
// (#1). All validation and lifecycle rules live on the server.
(function () {
  'use strict';

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const money = n => Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  // Local "YYYY-MM-DD" for the disabled Create Date field (#2).
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
      body.innerHTML = list.length ? list.map(s => `
        <tr>
          <td>${esc(s.issuer)}</td>
          <td>${esc(s.createDate)}</td>
          <td>${esc(s.sipDate)}</td>
          <td>${money(s.sipAmount)}</td>
          <td>${esc(s.frequency)}</td>
          <td>${s.actualSipAmount == null ? '&mdash;' : money(s.actualSipAmount)}</td>
          <td>${s.bondName ? esc(s.bondName) : '&mdash;'}</td>
          <td>${esc(s.status)}</td>
        </tr>`).join('')
        : '<tr><td colspan="8" class="muted">No Bond SIPs yet - create one above.</td></tr>';
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    // #2: Create Date auto-populates to the current date and is disabled.
    const createDate = document.getElementById('createDate');
    if (createDate) createDate.value = todayIso();

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
            if (createDate) createDate.value = todayIso();
            load();
          });
      });
    }

    loadIssuers();
    load();
    if (window.App && window.App.renderIcons) window.App.renderIcons();
  });
})();
