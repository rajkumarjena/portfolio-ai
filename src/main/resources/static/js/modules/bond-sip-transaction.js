// bond-sip-transaction.js - BONDBP_V7 #5/#6/#7 Bond SIP Transaction screen.
// Issuer / Create Date / SIP Date / SIP Amount render as plain readonly cells;
// only the Actual SIP Amount input and the Bond Name drop-down are editable.
// Saving associates the bond and flips the transaction to EXECUTED (#7).
(function () {
  'use strict';

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const money = n => Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  let bondOptions = [];

  function tbody() {
    return document.querySelector('#sipTransactions tbody');
  }

  function banner(message, ok) {
    const el = document.getElementById('statusBanner');
    if (!el) return;
    el.textContent = message || '';
    el.style.display = message ? 'block' : 'none';
    el.classList.toggle('error', !ok);
  }

  function bondSelectHtml(tx) {
    const options = ['<option value="">-- Select Bond --</option>']
      .concat(bondOptions.map(o =>
        `<option value="${o.bondId}"${o.bondId === tx.bondId ? ' selected' : ''}>${esc(o.label)}</option>`));
    return `<select class="txn-bond"${tx.status === 'EXECUTED' ? ' disabled' : ''}>${options.join('')}</select>`;
  }

  function rowHtml(tx) {
    const done = tx.status === 'EXECUTED';
    const actualCell = done
      ? (tx.actualSipAmount == null ? '&mdash;' : money(tx.actualSipAmount))
      : `<input type="number" class="txn-actual" min="0" step="0.01"
                value="${tx.actualSipAmount == null ? '' : tx.actualSipAmount}">`;
    return `
      <tr data-id="${tx.id}">
        <td>${esc(tx.issuer)}</td>
        <td>${esc(tx.createDate)}</td>
        <td>${esc(tx.sipDate)}</td>
        <td>${money(tx.sipAmount)}</td>
        <td class="txn-actual-cell">${actualCell}</td>
        <td>${bondSelectHtml(tx)}</td>
        <td class="txn-status">${esc(tx.status)}</td>
        <td>${done ? '' : '<button type="button" class="primary txn-save">Save</button>'}</td>
      </tr>`;
  }

  function load() {
    Promise.all([
      Api.guard(Api.get('/api/bond-sips/bond-options'), 'Could not load Bond Names'),
      Api.guard(Api.get('/api/bond-sips/transactions'), 'Could not load Bond SIP Transactions')
    ]).then(function (results) {
      bondOptions = results[0] || [];
      const transactions = results[1] || [];
      const body = tbody();
      if (!body) return;
      body.innerHTML = transactions.length
        ? transactions.map(rowHtml).join('')
        : '<tr><td colspan="8" class="muted">No Bond SIP Transactions yet. '
          + 'A transaction appears here when a due Bond SIP executes.</td></tr>';
      if (window.App && window.App.renderIcons) window.App.renderIcons();
    });
  }

  function saveRow(row) {
    const id = row.getAttribute('data-id');
    const actualValue = row.querySelector('.txn-actual').value;
    const bondValue = row.querySelector('.txn-bond').value;
    if (!bondValue) {
      banner('Please select a Bond Name before saving.', false);
      return;
    }
    banner('');
    const payload = {
      actualSipAmount: actualValue === '' ? null : Number(actualValue),
      bondId: Number(bondValue)
    };
    Api.guard(Api.put('/api/bond-sips/transactions/' + id, payload),
      'Could not save the Bond SIP Transaction')
      .then(function (saved) {
        if (!saved) return;
        banner('Transaction ' + saved.id + ' is now ' + saved.status + '.', true);
        load();
      });
  }

  document.addEventListener('DOMContentLoaded', function () {
    const body = tbody();
    if (body) {
      body.addEventListener('click', function (event) {
        if (!event.target.classList.contains('txn-save')) return;
        saveRow(event.target.closest('tr'));
      });
    }
    load();
    if (window.App && window.App.renderIcons) window.App.renderIcons();
  });
})();
