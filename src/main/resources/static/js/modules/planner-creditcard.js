// planner-creditcard.js - MPC_V1 / MP_V5, the Credit Card Bills Entry screen of
// the Monthly Planner Spending page. A card is a name, a billing-cycle day and a
// grace period; every transaction is billed in one month (cycle end + grace by
// default, movable between this month and the next), and the Monthly Planner
// plans the sum of the transactions billed in a month. The server owns the month
// arithmetic, the totals and the ownership - this module only renders and
// submits (CONSISTENCY.md 3).
(function () {
  'use strict';

  let overlay = null;
  let view = null;
  let editing = null;

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const money = n => (Number(n) || 0).toLocaleString('en-IN', {
    minimumFractionDigits: 2, maximumFractionDigits: 2
  });

  function el(id) {
    return document.getElementById(id);
  }

  function ordinal(n) {
    const value = Number(n) || 0;
    const tens = value % 100;
    if (tens >= 11 && tens <= 13) return value + 'th';
    switch (value % 10) {
      case 1: return value + 'st';
      case 2: return value + 'nd';
      case 3: return value + 'rd';
      default: return value + 'th';
    }
  }

  /** The plan month the planner page is showing - the view is loaded for it. */
  function monthValue() {
    const input = el('planMonth');
    if (input && input.value) return input.value;
    const now = new Date();
    return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
  }

  /** A server LocalDate ("2026-11-01") as "2026-11". */
  function monthIso(value) {
    return value ? String(value).slice(0, 7) : '';
  }

  function banner(message, ok) {
    const box = el('ccBanner');
    if (!box) return;
    box.textContent = message || '';
    box.style.display = message ? 'block' : 'none';
    box.classList.toggle('error', ok === false);
    box.classList.toggle('info', ok !== false);
  }

  function activateTab(name) {
    document.querySelectorAll('[data-cc-tab]').forEach(function (button) {
      const active = button.getAttribute('data-cc-tab') === name;
      button.classList.toggle('active', active);
      button.setAttribute('aria-selected', active ? 'true' : 'false');
    });
    const transactions = el('ccTabTransactions');
    const add = el('ccTabAdd');
    if (transactions) transactions.hidden = name !== 'transactions';
    if (add) add.hidden = name !== 'add';
  }

  function openModal() {
    editing = null;
    renderForm();
    banner('');
    activateTab('transactions');
    overlay.classList.add('open');
    load();
  }

  function closeModal() {
    overlay.classList.remove('open');
  }

  function load() {
    Api.guard(Api.get('/api/credit-cards?month=' + encodeURIComponent(monthValue())),
      'Could not load your credit cards')
      .then(function (result) {
        if (!result) return;
        view = result;
        render();
      });
  }

  function render() {
    if (!view) return;
    el('ccCurrentMonthName').textContent = view.currentMonthLabel || 'this month';
    el('ccNextMonthName').textContent = view.nextMonthLabel || 'next month';
    el('ccCurrentTotal').textContent = money(view.currentBillTotal);
    el('ccNextTotal').textContent = money(view.nextBillTotal);

    const wrap = el('ccCards');
    if (!view.cards || !view.cards.length) {
      wrap.innerHTML = '<p class="muted">No credit cards yet - open the '
        + '<strong>Add Credit Card</strong> tab to add the first one.</p>';
      return;
    }
    wrap.innerHTML = view.cards.map(cardHtml).join('');
  }

  /** MP_V5 #3.iii: the two options carry the real month names. */
  function billRadio(purchase, kind, label) {
    const checked = kind === 'current' ? purchase.currentSelected : purchase.nextSelected;
    const disabled = kind === 'next' && purchase.nextDisabled;
    return '<label class="cc-bill-radio' + (disabled ? ' is-locked' : '') + '">'
      + '<input type="radio" name="cc-bill-' + esc(purchase.id) + '" value="' + kind + '"'
      + (checked ? ' checked' : '') + (disabled ? ' disabled' : '') + '>'
      + ' Bill in ' + esc(label) + '</label>';
  }

  function cardHtml(card) {
    const currentLabel = view.currentMonthLabel || 'this month';
    const nextLabel = view.nextMonthLabel || 'next month';
    const purchases = (card.purchases || []).map(function (p) {
      const remove = p.removable
        ? '<button type="button" class="btn" data-cc-remove-purchase="' + esc(p.id) + '"'
          + ' title="Remove this purchase">&times;</button>'
        : '';
      return `
        <tr class="cc-bill-row" data-purchase="${esc(p.id)}"
            data-bill-month="${esc(monthIso(p.billMonth))}">
          <td class="left">${esc(p.purchaseDate)}</td>
          <td class="left">${esc(p.description || '')}</td>
          <td class="amt">${money(p.amount)}</td>
          <td class="cc-bill-cell">${billRadio(p, 'current', currentLabel)}</td>
          <td class="cc-bill-cell">${billRadio(p, 'next', nextLabel)}</td>
          <td class="cc-remove-cell">${remove}</td>
        </tr>`;
    }).join('');
    return `
      <div class="cc-card" data-card="${esc(card.id)}">
        <div class="cc-card-head">
          <div>
            <strong>${esc(card.name)}</strong>
            <div class="muted">Billing cycle ends on the ${ordinal(card.billingCycleDay)} &middot;
              ${esc(card.cycleLabel)} &middot; grace period ${Number(card.gracePeriodDays) || 0} day(s)</div>
          </div>
          <div class="cc-card-actions">
            <button type="button" class="btn" data-cc-edit="${esc(card.id)}">Edit card</button>
            <button type="button" class="btn" data-cc-delete="${esc(card.id)}">Remove card</button>
          </div>
        </div>

        <!-- MP_V5 #3.vi: this card's own totals for the two months. -->
        <div class="cc-month-totals">
          <span>Bill amount for <strong>${esc(currentLabel)}</strong>:
            <strong>${money(card.currentBillTotal)}</strong></span>
          <span>Bill amount for <strong>${esc(nextLabel)}</strong>:
            <strong>${money(card.nextBillTotal)}</strong></span>
        </div>

        <div class="cc-table-wrap">
          <table class="data-table cc-bill-table">
            <thead>
            <tr>
              <th>Purchase Date</th>
              <th>Description</th>
              <th>Amount (&#8377;)</th>
              <th>Bill in ${esc(currentLabel)}</th>
              <th>Bill in ${esc(nextLabel)}</th>
              <th></th>
            </tr>
            </thead>
            <tbody>${purchases
        || '<tr><td colspan="6" class="muted left">No transactions billed in these months.</td></tr>'}</tbody>
          </table>
        </div>

        <div class="cc-add-row">
          <input type="date" class="cc-pdate" aria-label="Purchase date">
          <input type="text" class="cc-pdesc" maxlength="120" placeholder="Description">
          <input type="number" class="cc-pamount" min="0" step="0.01" placeholder="Amount">
          <button type="button" class="btn" data-cc-add-purchase="${esc(card.id)}">Add Purchase</button>
          <button type="button" class="btn primary" data-cc-save-bill="1">Save Bill</button>
        </div>
      </div>`;
  }

  function renderForm() {
    const day = el('ccDay');
    const current = editing ? Number(editing.billingCycleDay) : 1;
    let options = '';
    for (let d = 1; d <= 31; d++) {
      options += `<option value="${d}"${d === current ? ' selected' : ''}>${ordinal(d)} of the month</option>`;
    }
    day.innerHTML = options;
    el('ccName').value = editing ? (editing.name || '') : '';
    el('ccGrace').value = editing ? String(Number(editing.gracePeriodDays) || 0) : '';
    el('ccCardFormTitle').textContent = editing
      ? 'Edit ' + editing.name
      : 'Add a credit card';
    el('ccSaveCard').textContent = editing ? 'Save Card' : 'Add Card';
    el('ccCancelCard').style.display = editing ? '' : 'none';
  }

  /** Every answer is the refreshed month, so the screen and the planner agree. */
  function apply(result) {
    if (!result) return;
    view = result;
    if (editing) {
      editing = null;
      renderForm();
    }
    render();
    document.dispatchEvent(new CustomEvent('planner:refresh'));
  }

  function saveCard() {
    const name = (el('ccName').value || '').trim();
    const cycleDay = el('ccDay').value;
    const graceRaw = String(el('ccGrace').value || '').trim();
    // MP_V5 #2: all three fields are required.
    if (!name) {
      banner('Give the card a name.', false);
      return;
    }
    if (!cycleDay) {
      banner('Choose the billing cycle.', false);
      return;
    }
    if (!graceRaw) {
      banner('Enter the grace period in days.', false);
      return;
    }
    const grace = Number(graceRaw);
    if (!Number.isInteger(grace) || grace < 0) {
      banner('The grace period must be a whole number of days (0 or more).', false);
      return;
    }
    const body = {
      id: editing ? editing.id : null,
      name: name,
      billingCycleDay: Number(cycleDay),
      gracePeriodDays: grace
    };
    const url = '/api/credit-cards' + (editing ? '/' + editing.id : '')
      + '?month=' + encodeURIComponent(monthValue());
    const request = editing ? Api.put(url, body) : Api.post(url, body);
    const button = el('ccSaveCard');
    button.disabled = true;
    Api.guard(request, 'Could not save the card').then(function (result) {
      button.disabled = false;
      if (!result) return;
      apply(result);
      banner('Card saved.', true);
      activateTab('transactions');
    });
  }

  function editCard(id) {
    if (!view) return;
    editing = (view.cards || []).find(card => String(card.id) === String(id)) || null;
    renderForm();
    activateTab('add');
    if (editing) {
      banner('Editing ' + editing.name + ' - change its details and save the card.', true);
    }
  }

  function removeCard(id) {
    const card = ((view && view.cards) || []).find(item => String(item.id) === String(id));
    if (!card) return;
    if (!window.confirm('Remove ' + card.name + ' with its transactions?')) return;
    Api.guard(Api.del('/api/credit-cards/' + id + '?month=' + encodeURIComponent(monthValue())),
      'Could not remove the card').then(apply);
  }

  /**
   * MP_V5 #3.iv: every changed transaction of the whole screen is saved in one
   * call - the button belongs to one card but the payload covers all of them.
   */
  function saveBill(button) {
    const currentMonth = monthIso(view && view.currentMonth);
    const nextMonth = monthIso(view && view.nextMonth);
    const transactions = [];
    document.querySelectorAll('#ccCards tr.cc-bill-row').forEach(function (row) {
      const purchaseId = row.getAttribute('data-purchase');
      const checked = row.querySelector('input[type="radio"]:checked');
      if (!purchaseId || !checked) return;
      const billMonth = checked.value === 'next' ? nextMonth : currentMonth;
      if (!billMonth || billMonth === row.getAttribute('data-bill-month')) return;
      transactions.push({ purchaseId: Number(purchaseId), billMonth: billMonth });
    });
    if (!transactions.length) {
      banner('Every transaction already has its bill month - nothing to save.', true);
      return;
    }
    if (button) button.disabled = true;
    Api.guard(Api.put('/api/credit-cards/transactions?month='
      + encodeURIComponent(monthValue()), transactions),
      'Could not save the bills')
      .then(function (result) {
        if (button) button.disabled = false;
        if (!result) return;
        apply(result);
        banner('Saved ' + transactions.length + ' transaction(s).', true);
      });
  }

  function addPurchase(id, button) {
    const block = button.closest('.cc-card');
    const purchaseDate = block.querySelector('.cc-pdate').value;
    const description = block.querySelector('.cc-pdesc').value;
    const amount = Number(block.querySelector('.cc-pamount').value) || 0;
    if (!purchaseDate) {
      banner('Choose the date of the purchase.', false);
      return;
    }
    if (amount <= 0) {
      banner('The purchase amount must be more than zero.', false);
      return;
    }
    button.disabled = true;
    Api.guard(Api.post('/api/credit-cards/' + id + '/purchases?month='
      + encodeURIComponent(monthValue()),
      { purchaseDate: purchaseDate, amount: amount, description: description }),
      'Could not add the purchase')
      .then(function (result) {
        button.disabled = false;
        if (!result) return;
        apply(result);
        banner('Purchase added - its bill month is set from the cycle and grace period.', true);
      });
  }

  function removePurchase(id) {
    Api.guard(Api.del('/api/credit-cards/purchases/' + id
      + '?month=' + encodeURIComponent(monthValue())), 'Could not remove the purchase')
      .then(apply);
  }

  function onClick(event) {
    const tab = event.target.closest('[data-cc-tab]');
    if (tab) {
      activateTab(tab.getAttribute('data-cc-tab'));
      return;
    }
    const button = event.target.closest('button');
    if (!button) return;
    const data = button.dataset;
    if (data.ccSaveBill) return saveBill(button);
    if (data.ccAddPurchase) return addPurchase(data.ccAddPurchase, button);
    if (data.ccRemovePurchase) return removePurchase(data.ccRemovePurchase);
    if (data.ccEdit) return editCard(data.ccEdit);
    if (data.ccDelete) return removeCard(data.ccDelete);
    if (button.id === 'ccSaveCard') return saveCard();
    if (button.id === 'ccCancelCard') {
      editing = null;
      renderForm();
      banner('');
      activateTab('transactions');
      return;
    }
    if (button.id === 'ccClose' || button.id === 'ccDone') return closeModal();
  }

  // Expose the opener so the planner's "open" link can trigger the popup.
  window.openCreditCards = openModal;

  function init() {
    overlay = el('ccModal');
    if (!overlay) return;
    const opener = el('ccOpen');
    if (opener) opener.addEventListener('click', openModal);
    overlay.addEventListener('click', onClick);
    // The popup follows the planner's month - the planner figure is that month's.
    const month = el('planMonth');
    if (month) {
      month.addEventListener('change', function () {
        if (overlay.classList.contains('open')) load();
      });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
