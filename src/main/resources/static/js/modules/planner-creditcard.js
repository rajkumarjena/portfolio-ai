// planner-creditcard.js - MPC_V1 / MP_V5 / MP_V6, the Credit Card Bills Entry
// screen of the Monthly Planner Spending page. A card is a name, a billing-cycle
// day and a grace period; every transaction is billed in one month (cycle end +
// grace by default, movable between the two months on screen), and the Monthly
// Planner plans the sum of the transactions billed in a month. MP_V6: the screen
// carries its own month picker, so any month may be shown, and it opens on the
// server's default month. The server owns the month arithmetic, the totals and
// the ownership - this module only renders and submits (CONSISTENCY.md 3).
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

  /** MP_V6 #1: the month the screen shows - empty means "the server's default". */
  function monthValue() {
    const input = el('ccMonth');
    return input && input.value ? input.value : '';
  }

  function monthQuery() {
    const value = monthValue();
    return value ? '?month=' + encodeURIComponent(value) : '';
  }

  /** The selected month of the loaded view - the pair the screen is showing. */
  function selectedIso() {
    return view && view.selectedMonth ? monthIso(view.selectedMonth) : monthValue();
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
    // MP_V6 #3: no month chosen - the server returns its default month.
    const input = el('ccMonth');
    if (input) input.value = '';
    overlay.classList.add('open');
    load();
  }

  function closeModal() {
    overlay.classList.remove('open');
  }

  function load(month) {
    const wanted = month == null ? monthValue() : month;
    const query = wanted ? '?month=' + encodeURIComponent(wanted) : '';
    Api.guard(Api.get('/api/credit-cards' + query),
      'Could not load your credit cards')
      .then(function (result) {
        if (!result) return;
        apply(result, false);
      });
  }

  /** MP_V6 #3: the picker always shows the month the server rendered. */
  function apply(result, refresh) {
    if (!result) return;
    view = result;
    const input = el('ccMonth');
    if (input && result.selectedMonth) input.value = monthIso(result.selectedMonth);
    if (editing) {
      editing = null;
      renderForm();
    }
    render();
    if (refresh !== false) {
      document.dispatchEvent(new CustomEvent('planner:refresh'));
    }
  }

  function shiftMonth(delta) {
    const input = el('ccMonth');
    if (!input || !input.value) {
      load();
      return;
    }
    const parts = input.value.split('-');
    const moved = new Date(Number(parts[0]), Number(parts[1]) - 1 + delta, 1);
    input.value = moved.getFullYear() + '-'
      + String(moved.getMonth() + 1).padStart(2, '0');
    load();
  }

  function render() {
    if (!view) return;
    const selectedLabel = view.selectedMonthLabel || 'this month';
    const nextLabel = view.nextMonthLabel || 'next month';
    el('ccSelectedMonthName').textContent = selectedLabel;
    el('ccNextMonthName').textContent = nextLabel;
    el('ccSelectedTotal').textContent = money(view.selectedBillTotal);
    el('ccNextTotal').textContent = money(view.nextBillTotal);

    const wrap = el('ccCards');
    if (!view.cards || !view.cards.length) {
      wrap.innerHTML = '<p class="muted">No credit cards yet - open the '
        + '<strong>Add Credit Card</strong> tab to add the first one.</p>';
      return;
    }
    const note = view.editable ? ''
      : '<p class="muted">' + esc(selectedLabel)
        + ' is in the past - its bills are shown read only.</p>';
    wrap.innerHTML = note + view.cards.map(cardHtml).join('');
  }

  /**
   * MP_V5 #3.iii / MP_V6 #2: the two columns are the selected month and the one
   * after it. A past month only displays which of them the row is billed in.
   */
  function billCell(purchase, iso, label, selected) {
    if (!view.editable) {
      return selected ? '<span class="cc-bill-static">Bill in ' + esc(label) + '</span>' : '';
    }
    const nextIso = monthIso(view.nextMonth);
    const disabled = iso === nextIso && purchase.nextDisabled;
    return '<label class="cc-bill-radio' + (disabled ? ' is-locked' : '') + '">'
      + '<input type="radio" name="cc-bill-' + esc(purchase.id) + '" value="' + esc(iso) + '"'
      + (selected ? ' checked' : '') + (disabled ? ' disabled' : '') + '>'
      + ' Bill in ' + esc(label) + '</label>';
  }

  function cardHtml(card) {
    const selectedLabel = view.selectedMonthLabel || 'this month';
    const nextLabel = view.nextMonthLabel || 'next month';
    const selectedIso = monthIso(view.selectedMonth);
    const nextIso = monthIso(view.nextMonth);
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
          <td class="cc-bill-cell">${billCell(p, selectedIso, selectedLabel, p.inSelectedMonth)}</td>
          <td class="cc-bill-cell">${billCell(p, nextIso, nextLabel, p.inNextMonth)}</td>
          <td class="cc-remove-cell">${remove}</td>
        </tr>`;
    }).join('');
    const saveBill = view.editable
      ? '<button type="button" class="btn primary" data-cc-save-bill="1">Save Bill</button>'
      : '';
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

        <!-- MP_V5 #3.vi / MP_V6 #2: this card's own totals for the two months. -->
        <div class="cc-month-totals">
          <span>Bill amount for <strong>${esc(selectedLabel)}</strong>:
            <strong>${money(card.selectedBillTotal)}</strong></span>
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
              <th>Bill in ${esc(selectedLabel)}</th>
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
          ${saveBill}
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
    const url = '/api/credit-cards' + (editing ? '/' + editing.id : '') + monthQuery();
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
    Api.guard(Api.del('/api/credit-cards/' + id + monthQuery()),
      'Could not remove the card').then(apply);
  }

  /**
   * MP_V5 #3.iv: every changed transaction of the whole screen is saved in one
   * call - the button belongs to one card but the payload covers all of them.
   * MP_V6 #2: the month sent is the one on screen, so the server can accept
   * exactly its two months.
   */
  function saveBill(button) {
    const transactions = [];
    document.querySelectorAll('#ccCards tr.cc-bill-row').forEach(function (row) {
      const purchaseId = row.getAttribute('data-purchase');
      const checked = row.querySelector('input[type="radio"]:checked');
      if (!purchaseId || !checked || !checked.value) return;
      if (checked.value === row.getAttribute('data-bill-month')) return;
      transactions.push({ purchaseId: Number(purchaseId), billMonth: checked.value });
    });
    if (!transactions.length) {
      banner('Every transaction already has its bill month - nothing to save.', true);
      return;
    }
    const month = selectedIso();
    if (!month) return;
    if (button) button.disabled = true;
    Api.guard(Api.put('/api/credit-cards/transactions?month=' + encodeURIComponent(month),
      transactions), 'Could not save the bills')
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
    Api.guard(Api.post('/api/credit-cards/' + id + '/purchases' + monthQuery(),
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
    Api.guard(Api.del('/api/credit-cards/purchases/' + id + monthQuery()),
      'Could not remove the purchase').then(apply);
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
    if (button.id === 'ccPrevMonth') return shiftMonth(-1);
    if (button.id === 'ccNextMonth') return shiftMonth(1);
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
    // MP_V6 #1: the picker drives the screen on its own, independent of the
    // planner's month.
    const month = el('ccMonth');
    if (month) {
      month.addEventListener('change', function () {
        load();
      });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
