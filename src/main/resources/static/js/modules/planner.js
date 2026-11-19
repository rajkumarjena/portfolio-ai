// planner.js - MP_V1 Monthly Planner, shared by the Earnings and the Spending
// screen. The template sets data-planner-kind on #content; the server decides
// every figure (explicit, recurring carry-over or SIP auto-population) and the
// page only renders and submits what it is given (CONSISTENCY.md 3).
(function () {
  'use strict';

  const content = document.getElementById('content');
  const KIND = (content && content.dataset && content.dataset.plannerKind) || 'EARNING';

  let view = null;

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;');

  const money = n => (Number(n) || 0).toLocaleString('en-IN', {
    minimumFractionDigits: 2, maximumFractionDigits: 2
  });

  function banner(message, ok) {
    const el = document.getElementById('plannerBanner');
    if (!el) return;
    el.textContent = message || '';
    el.style.display = message ? 'block' : 'none';
    el.classList.toggle('error', ok === false);
    el.classList.toggle('info', ok !== false);
  }

  function tbody() {
    return document.querySelector('#plannerTable tbody');
  }

  function monthInput() {
    return document.getElementById('planMonth');
  }

  function currentMonthValue() {
    const input = monthInput();
    if (input && input.value) return input.value;
    const now = new Date();
    return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
  }

  function setMonth(isoDate) {
    const input = monthInput();
    if (input && isoDate) input.value = String(isoDate).slice(0, 7);
  }

  function shiftMonth(delta) {
    const parts = currentMonthValue().split('-');
    const shifted = new Date(Number(parts[0]), Number(parts[1]) - 1 + delta, 1);
    const input = monthInput();
    if (input) {
      input.value = shifted.getFullYear() + '-'
        + String(shifted.getMonth() + 1).padStart(2, '0');
    }
    load();
  }

  function lines() {
    if (!view) return [];
    return KIND === 'EARNING' ? (view.earnings || []) : (view.spending || []);
  }

  function statusText(line) {
    const occurrences = Number(line.occurrences) || 1;
    const weekly = line.frequency === 'WEEKLY' && occurrences > 0
      ? ' - ' + occurrences + ' time' + (occurrences === 1 ? '' : 's') + ' this month at '
        + money(line.entryAmount) + ' each'
      : '';
    if (line.auto) return 'Auto-populated from its own screen';
    if (line.counted === false) {
      return 'Starts ' + longDate(line.startDate) + ' - not counted this month';
    }
    if (line.frequency === 'ONE_TIME') {
      return 'One time on ' + longDate(line.startDate) + ' - not repeated';
    }
    if (line.inherited) {
      return 'Carried over from ' + String(line.inheritedFrom || '').slice(0, 7) + weekly;
    }
    if (line.recurring) return 'Repeats - carried into later months' + weekly;
    return 'This month only' + weekly;
  }

  /** "2026-11-01" -> "2026-11" for the month input; anything else stays blank. */
  function monthValue(isoDate) {
    return isoDate ? String(isoDate).slice(0, 7) : '';
  }

  /** "2026-01-29" -> "29 January 2026" for the "From" input. */
  function dateValue(isoDate) {
    return isoDate ? String(isoDate).slice(0, 10) : '';
  }

  function longDate(isoDate) {
    const value = dateValue(isoDate);
    if (!value) return '';
    const parts = value.split('-');
    const date = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    return date.toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' });
  }

  function frequencyOptions(current) {
    const options = (view && view.frequencyOptions) || {};
    return Object.keys(options).map(key =>
      `<option value="${esc(key)}"${key === current ? ' selected' : ''}>${esc(options[key])}</option>`
    ).join('');
  }

  function render() {
    if (!view) return;
    document.getElementById('monthLabel').textContent = view.monthLabel || '';
    setMonth(view.month);

    tbody().innerHTML = lines().map(line => {
      const editable = line.editable !== false;
      const disabled = editable ? '' : ' disabled';
      const source = line.sourceLink
        ? ` <a class="muted" href="${esc(line.sourceLink)}" title="Maintain this figure on its own screen">open</a>`
        : '';
      // The month's total uses the server's count: a weekly figure covers the
      // month's own weekdays, and an entry that starts later is not counted yet.
      const counted = line.counted !== false;
      const occurrences = counted ? (Number(line.occurrences) || 1) : 0;
      return `
        <tr data-category="${esc(line.category)}" data-counted="${counted}"
            data-occurrences="${occurrences}">
          <td class="left">${esc(line.label)}${source}</td>
          <td class="amt"><input type="number" class="planner-amount" min="0" step="0.01"
                 value="${Number(line.entryAmount != null ? line.entryAmount : line.amount) || 0}"${disabled}
                 aria-label="${esc(line.label)} amount"></td>
          <td><input type="checkbox" class="planner-recurring"${line.recurring ? ' checked' : ''}${disabled}
                 aria-label="${esc(line.label)} repeats"></td>
          <td><select class="planner-frequency"${disabled}>${frequencyOptions(line.frequency)}</select></td>
          <td><input type="date" class="planner-start" value="${dateValue(line.startDate)}"${disabled}
                 title="First date this earning or expense applies from"
                 aria-label="${esc(line.label)} starts on"></td>
          <td><input type="month" class="planner-end" value="${monthValue(line.endDate)}"${disabled}
                 title="Last month this repeat applies to - blank means open ended"
                 aria-label="${esc(line.label)} repeats until"></td>
          <td class="muted left">${esc(statusText(line))}${line.note ? '<div>' + esc(line.note) + '</div>' : ''}</td>
        </tr>`;
    }).join('');

    tbody().querySelectorAll('tr').forEach(syncFrequency);
    updateTotal();
    if (window.App && window.App.renderIcons) window.App.renderIcons();
  }

  /**
   * A one-time entry has one date: its From date is also its end, so the Repeat
   * checkbox and the Until month have nothing to say for it.
   */
  function syncFrequency(row) {
    const select = row.querySelector('.planner-frequency');
    const repeat = row.querySelector('.planner-recurring');
    const until = row.querySelector('.planner-end');
    if (!select || !repeat || !until) return;
    const locked = select.disabled;
    const oneTime = select.value === 'ONE_TIME';
    if (oneTime) {
      repeat.checked = false;
      until.value = '';
    }
    repeat.disabled = locked || oneTime;
    until.disabled = locked || oneTime;
    until.title = oneTime
      ? 'A one-time entry has a single date - its From date'
      : 'Last month this repeat applies to - blank means open ended';
  }

  function updateTotal() {
    let total = 0;
    document.querySelectorAll('#plannerTable tbody tr').forEach(row => {
      if (row.getAttribute('data-counted') === 'false') return;
      const input = row.querySelector('.planner-amount');
      const occurrences = Number(row.getAttribute('data-occurrences')) || 1;
      total += (Number(input && input.value) || 0) * occurrences;
    });
    const el = document.getElementById('kindTotal');
    if (el) el.textContent = money(total);
  }

  function load() {
    const month = currentMonthValue();
    Api.guard(Api.get('/api/planner?month=' + encodeURIComponent(month)),
      'Could not load the monthly planner')
      .then(function (result) {
        if (!result) return;
        view = result;
        render();
      });
  }

  function save() {
    const entries = [];
    document.querySelectorAll('#plannerTable tbody tr').forEach(row => {
      entries.push({
        category: row.getAttribute('data-category'),
        amount: Number(row.querySelector('.planner-amount').value) || 0,
        recurring: row.querySelector('.planner-recurring').checked,
        frequency: row.querySelector('.planner-frequency').value,
        startDate: row.querySelector('.planner-start').value || null,
        endDate: row.querySelector('.planner-end').value || null
      });
    });
    const button = document.getElementById('saveBtn');
    if (button) button.disabled = true;
    Api.guard(Api.put('/api/planner/entries',
      { month: currentMonthValue(), entries: entries }),
      'Could not save the month')
      .then(function (result) {
        if (result) {
          view = result;
          render();
          banner('Saved ' + result.monthLabel + ' - planned earnings '
            + money(result.earningsTotal) + ', planned spending '
            + money(result.spendingTotal) + ', net ' + money(result.net) + '.', true);
        }
        if (button) button.disabled = false;
      });
  }

  document.addEventListener('DOMContentLoaded', function () {
    const input = monthInput();
    if (input) {
      input.value = currentMonthValue();
      input.addEventListener('change', load);
    }
    const prev = document.getElementById('prevMonth');
    if (prev) prev.addEventListener('click', () => shiftMonth(-1));
    const next = document.getElementById('nextMonth');
    if (next) next.addEventListener('click', () => shiftMonth(1));
    const button = document.getElementById('saveBtn');
    if (button) button.addEventListener('click', save);
    const body = tbody();
    if (body) {
      body.addEventListener('input', updateTotal);
      body.addEventListener('change', function (event) {
        const row = event.target.closest('tr');
        if (row && event.target.classList.contains('planner-frequency')) syncFrequency(row);
      });
    }
    // The credit card popup saves bills that this screen shows, so it asks for a reload.
    document.addEventListener('planner:refresh', load);

    load();
  });
})();
