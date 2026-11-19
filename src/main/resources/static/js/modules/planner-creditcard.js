// planner-creditcard.js - MPC_V1, the credit card popup of the Monthly Planner
// Spending screen. Every card has a billing cycle; the bill of the cycle that
// ends in the plan month is what the "Credit Card Bills" line plans. The server
// owns the cycle arithmetic, the totals and the ownership - this module only
// renders and submits (CONSISTENCY.md 3).
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

  function monthValue() {
    const input = el('planMonth');
    if (input && input.value) return input.value;
    const now = new Date();
    return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
  }

  function banner(message, ok) {
    const box = el('ccBanner');
    if (!box) return;
    box.textContent = message || '';
    box.style.display = message ? 'block' : 'none';
    box.classList.toggle('error', ok === false);
    box.classList.toggle('info', ok !== false);
  }

  function openModal() {
    editing = null;
    renderForm();
    banner('');
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
    el('ccMonthLabel').textContent = view.monthLabel || '';
    el('ccTotal').textContent = money(view.total);
    const wrap = el('ccCards');
    if (!view.cards || !view.cards.length) {
      wrap.innerHTML = '<p class="muted">No credit cards yet - add the first one above.</p>';
      return;
    }
    wrap.innerHTML = view.cards.map(cardHtml).join('');
  }

  /** How this cycle's figure was arrived at - the server says which. */
  function sourceText(card) {
    if (card.amountSource === 'BILL') {
      return 'The bill you entered for this cycle is planned.';
    }
    if (card.amountSource === 'PURCHASES') {
      return 'No bill entered yet - the purchases of this cycle are planned ('
        + money(card.purchaseTotal) + ').';
    }
    return 'Nothing planned for this cycle yet.';
  }

  function cardHtml(card) {
    const purchases = (card.purchases || []).map(p => `
        <tr>
          <td class="left">${esc(p.purchaseDate)}</td>
          <td class="left">${esc(p.description || '')}</td>
          <td class="amt">${money(p.amount)}</td>
          <td><button type="button" class="btn" data-cc-remove-purchase="${esc(p.id)}"
                title="Remove this purchase">&times;</button></td>
        </tr>`).join('');
    return `
      <div class="cc-card" data-card="${esc(card.id)}">
        <div class="cc-card-head">
          <div>
            <strong>${esc(card.name)}</strong>
            <div class="muted">Billing cycle ends on the ${ordinal(card.billingCycleDay)} &middot;
              ${esc(card.cycleLabel)}</div>
          </div>
          <div class="cc-card-actions">
            <button type="button" class="btn" data-cc-edit="${esc(card.id)}">Edit card</button>
            <button type="button" class="btn" data-cc-delete="${esc(card.id)}">Remove card</button>
          </div>
        </div>
        <div class="cc-card-body">
          <div>
            <div class="muted">Purchases in this cycle (${Number(card.purchaseCount) || 0})</div>
            <table class="data-table">
              <thead>
              <tr><th>Purchase date</th><th>Description</th><th>Amount (&#8377;)</th><th></th></tr>
              </thead>
              <tbody>${purchases
        || '<tr><td colspan="4" class="muted left">No purchases entered for this cycle.</td></tr>'}</tbody>
            </table>
            <div class="nav-buttons" style="justify-content:flex-start;gap:8px;margin-top:8px;">
              <input type="date" class="cc-pdate" aria-label="Purchase date">
              <input type="text" class="cc-pdesc" maxlength="120" placeholder="Description" style="width:180px;">
              <input type="number" class="cc-pamount" min="0" step="0.01" placeholder="Amount" style="width:120px;">
              <button type="button" class="btn" data-cc-add-purchase="${esc(card.id)}">Add purchase</button>
            </div>
          </div>
          <div>
            <div class="field">
              <label>Bill amount for this cycle (&#8377;)</label>
              <input type="number" class="cc-bill" min="0" step="0.01"
                     value="${Number(card.billAmount) || 0}">
            </div>
            <div class="muted" style="margin-bottom:8px;">${sourceText(card)}</div>
            <button type="button" class="btn primary" data-cc-save-bill="${esc(card.id)}"
                    data-cycle-end="${esc(card.cycleEnd)}">Save bill</button>
          </div>
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
    el('ccCardFormTitle').textContent = editing ? 'Edit ' + editing.name : 'Add a credit card';
    el('ccSaveCard').textContent = editing ? 'Save card' : 'Add card';
    el('ccCancelCard').style.display = editing ? '' : 'none';
  }

  /** Every answer is the refreshed month, so the popup and the planner agree. */
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
    if (!name) {
      banner('Give the card a name.', false);
      return;
    }
    const body = {
      id: editing ? editing.id : null,
      name: name,
      billingCycleDay: Number(el('ccDay').value) || 1
    };
    const url = '/api/credit-cards' + (editing ? '/' + editing.id : '')
      + '?month=' + encodeURIComponent(monthValue());
    const request = editing ? Api.put(url, body) : Api.post(url, body);
    const button = el('ccSaveCard');
    button.disabled = true;
    Api.guard(request, 'Could not save the card').then(function (result) {
      button.disabled = false;
      apply(result);
    });
  }

  function editCard(id) {
    if (!view) return;
    editing = (view.cards || []).find(card => String(card.id) === String(id)) || null;
    renderForm();
    if (editing) {
      banner('Editing ' + editing.name + ' - change the billing-cycle day and save the card.', true);
    }
  }

  function removeCard(id) {
    const card = ((view && view.cards) || []).find(item => String(item.id) === String(id));
    if (!card) return;
    if (!window.confirm('Remove ' + card.name + ' with its bills and purchases?')) return;
    Api.guard(Api.del('/api/credit-cards/' + id + '?month=' + encodeURIComponent(monthValue())),
      'Could not remove the card').then(apply);
  }

  function saveBill(id, button) {
    // MP_V2: saving one bill saves every card's bill on the screen.
    const cards = Array.from(document.querySelectorAll('.cc-card'));
    if (button) button.disabled = true;
    const requests = cards.map(function (block) {
      const saveBtn = block.querySelector('[data-cc-save-bill]');
      const cardId = saveBtn ? saveBtn.getAttribute('data-cc-save-bill') : null;
      if (!cardId) return Promise.resolve(null);
      const amount = Number(block.querySelector('.cc-bill').value) || 0;
      const cycleEnd = saveBtn.getAttribute('data-cycle-end');
      return Api.put('/api/credit-cards/' + cardId + '/bills?month=' + encodeURIComponent(monthValue()),
        { cycleEnd: cycleEnd, amount: amount });
    });
    Promise.all(requests).then(function (results) {
      if (button) button.disabled = false;
      const last = results.filter(Boolean).pop();
      apply(last || view);
    }).catch(function () {
      if (button) button.disabled = false;
      banner('Could not save the bills.', false);
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
    Api.guard(Api.post('/api/credit-cards/' + id + '/purchases?month=' + encodeURIComponent(monthValue()),
      { purchaseDate: purchaseDate, amount: amount, description: description }),
      'Could not add the purchase')
      .then(function (result) {
        button.disabled = false;
        apply(result);
      });
  }

  function removePurchase(id) {
    Api.guard(Api.del('/api/credit-cards/purchases/' + id
      + '?month=' + encodeURIComponent(monthValue())), 'Could not remove the purchase')
      .then(apply);
  }

  function onClick(event) {
    const button = event.target.closest('button');
    if (!button) return;
    const data = button.dataset;
    if (data.ccSaveBill) return saveBill(data.ccSaveBill, button);
    if (data.ccAddPurchase) return addPurchase(data.ccAddPurchase, button);
    if (data.ccRemovePurchase) return removePurchase(data.ccRemovePurchase);
    if (data.ccEdit) return editCard(data.ccEdit);
    if (data.ccDelete) return removeCard(data.ccDelete);
    if (button.id === 'ccSaveCard') return saveCard();
    if (button.id === 'ccCancelCard') {
      editing = null;
      renderForm();
      banner('');
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
    // The popup follows the planner's month - a cycle belongs to one month.
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
