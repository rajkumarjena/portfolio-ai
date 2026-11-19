// planner.js - MP_V1 Monthly Planner, shared by the Earnings and the Spending
// screen. The template sets data-planner-kind on #content; the server decides
// every figure (explicit, recurring carry-over or SIP auto-population) and the
// page only renders and submits what it is given (CONSISTENCY.md 3).
(function () {
  'use strict';

  const content = document.getElementById('content');
  const KIND = (content && content.dataset && content.dataset.plannerKind) || 'EARNING';
  const IS_SPENDING = KIND === 'SPENDING';

  let view = null;
  let selectedRow = null;

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

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

  function monthValue(isoDate) {
    return isoDate ? String(isoDate).slice(0, 7) : '';
  }

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

  function isMiscLine(line) {
    if (line && line.misc) return true;
    const category = String((line && line.category) || '');
    return category === 'MISC_EARNING' || category.indexOf('MISC_EARNING#') === 0
      || category === 'MISC_SPENDING' || category.indexOf('MISC_SPENDING#') === 0;
  }

  function labelHtml(line) {
    return isMiscLine(line)
      ? 'Miscellaneous<a href="#" class="misc-note-link" data-misc-note title="Add a note for this Miscellaneous amount">(Note)</a>'
      : esc(line.label);
  }

  function rowHtml(line) {
    const editable = line.editable !== false;
    const disabled = editable ? '' : ' disabled';
    const source = line.sourceLink === 'credit-cards'
      ? ` <a class="muted" href="#" data-cc-open title="Maintain this figure on its own screen">open</a>`
      : (line.sourceLink
          ? ` <a class="muted" href="${esc(line.sourceLink)}" title="Maintain this figure on its own screen">open</a>`
          : '');
    const counted = line.counted !== false;
    const occurrences = counted ? (Number(line.occurrences) || 1) : 0;
    const paidChecked = line.paid ? ' checked' : '';
    const paidCell = IS_SPENDING
      ? `<td data-label="Paid"><input type="checkbox" class="planner-paid"${paidChecked} aria-label="${esc(line.label)} paid"></td>`
      : '';
    return `
      <tr data-category="${esc(line.category)}" data-counted="${counted}"
          data-occurrences="${occurrences}">
        <td class="left" data-label="${IS_SPENDING ? 'Obligation' : 'Earning'}">${labelHtml(line)}${source}</td>
        <td class="amt" data-label="Amount (\u20b9)"><input type="number" class="planner-amount" min="0" step="0.01"
               value="${Number(line.entryAmount != null ? line.entryAmount : line.amount) || 0}"${disabled}
               aria-label="${esc(line.label)} amount"></td>
        <td data-label="Repeat"><input type="checkbox" class="planner-recurring"${line.recurring ? ' checked' : ''}${disabled}
               aria-label="${esc(line.label)} repeats"></td>
        <td data-label="Frequency"><select class="planner-frequency"${disabled}>${frequencyOptions(line.frequency)}</select></td>
        <td data-label="From"><input type="date" class="planner-start" value="${dateValue(line.startDate)}"${disabled}
               title="First date this earning or expense applies from"
               aria-label="${esc(line.label)} starts on"></td>
        <td data-label="Until"><input type="month" class="planner-end" value="${monthValue(line.endDate)}"${disabled}
               title="Last month this repeat applies to - blank means open ended"
               aria-label="${esc(line.label)} repeats until"></td>
        ${paidCell}
        <td style="display:none" aria-hidden="true"><textarea class="planner-note" hidden>${esc(line.note || '')}</textarea></td>
      </tr>`;
  }

  function updateSelectedNote(value) {
    if (!selectedRow) return;
    const noteHidden = selectedRow.querySelector('.planner-note');
    if (noteHidden) noteHidden.value = value;
    const cat = selectedRow.getAttribute('data-category');
    const line = lines().find(l => l.category === cat);
    if (line) line.note = value;
    const bannerEl = document.getElementById('notesBanner');
    if (bannerEl && line) {
      const text = (statusText(line) || '') + (value ? '  |  Notes: ' + value : '');
      bannerEl.textContent = text;
      bannerEl.style.display = text ? 'block' : 'none';
    }
    const noteInput = document.getElementById('notesInput');
    if (noteInput && noteInput.value !== value) noteInput.value = value;
    const textarea = document.getElementById('miscNoteText');
    if (textarea && textarea.value !== value) textarea.value = value;
  }

  function bindRow(row) {
    row.addEventListener('focusin', () => showNotes(row));
    row.addEventListener('click', () => showNotes(row));
    const input = row.querySelector('.planner-amount');
    if (!input) return;
    // amount input: auto-clear note when amount becomes 0 (MP_V2 #9, MISC comments)
    input.addEventListener('input', function () {
      const val = Number(input.value) || 0;
      if (val === 0) {
        const nh = row.querySelector('.planner-note');
        if (nh) nh.value = '';
        if (selectedRow === row) {
          const line = lines().find(l => l.category === row.getAttribute('data-category'));
          if (line) line.note = '';
          const ni = document.getElementById('notesInput');
          if (ni) ni.value = '';
          const textarea = document.getElementById('miscNoteText');
          if (textarea) textarea.value = '';
        }
      }
      updateTotal();
    });
  }

  function showNotes(row) {
    const bannerEl = document.getElementById('notesBanner');
    const input = document.getElementById('notesInput');
    if (!row) return;
    selectedRow = row;
    const cat = row.getAttribute('data-category');
    const line = lines().find(l => l.category === cat);
    if (!line) return;
    const text = (statusText(line) || '') + (line.note ? '  |  Notes: ' + line.note : '');
    if (bannerEl) {
      bannerEl.textContent = text;
      bannerEl.style.display = text ? 'block' : 'none';
    }
    if (input) {
      input.value = line.note || '';
      input.disabled = false;
    }
  }

  function openMiscNote(row) {
    const modal = document.getElementById('miscNoteModal');
    const textarea = document.getElementById('miscNoteText');
    if (!modal || !textarea || !row) return;
    showNotes(row);
    const hidden = row.querySelector('.planner-note');
    textarea.value = hidden ? hidden.value : '';
    modal.classList.add('open');
    textarea.focus();
  }

  function closeMiscNote() {
    const modal = document.getElementById('miscNoteModal');
    if (modal) modal.classList.remove('open');
  }

  function addMisc() {
    if (!view) return;
    const base = IS_SPENDING ? 'MISC_SPENDING' : 'MISC_EARNING';
    const line = {
      category: base + '#new-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7),
      label: 'Miscellaneous', kind: KIND, amount: 0, entryAmount: 0,
      frequency: 'MONTHLY', recurring: false, editable: true, auto: false,
      inherited: false, startDate: view.month, endDate: null, counted: true,
      occurrences: 1, sourceLink: null, note: '', paid: IS_SPENDING, misc: true
    };
    (IS_SPENDING ? view.spending : view.earnings).push(line);
    tbody().insertAdjacentHTML('beforeend', rowHtml(line));
    const row = tbody().lastElementChild;
    if (!row) return;
    bindRow(row);
    syncFrequency(row);
    updateTotal();
    if (window.App && window.App.renderIcons) window.App.renderIcons();
    row.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    const amount = row.querySelector('.planner-amount');
    if (amount) amount.focus();
  }

  function render() {
    if (!view) return;
    document.getElementById('monthLabel').textContent = view.monthLabel || '';
    setMonth(view.month);

    tbody().innerHTML = lines().map(rowHtml).join('');
    tbody().querySelectorAll('tr').forEach(row => {
      bindRow(row);
      syncFrequency(row);
    });

    updateTotal();
    if (window.App && window.App.renderIcons) window.App.renderIcons();
  }

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
        endDate: row.querySelector('.planner-end').value || null,
        note: (row.querySelector('.planner-note') && row.querySelector('.planner-note').value) || null,
        paid: IS_SPENDING ? (row.querySelector('.planner-paid') ? row.querySelector('.planner-paid').checked : false) : undefined
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

  // credit card open link -> open modal
  document.addEventListener('click', function (event) {
    const link = event.target.closest('[data-cc-open]');
    if (!link) return;
    event.preventDefault();
    const opener = document.getElementById('ccOpen');
    if (opener) opener.click();
    else if (window.openCreditCards) window.openCreditCards();
  });

  // MP_V3 #1: the (Note) link on a Miscellaneous row -> its text box.
  document.addEventListener('click', function (event) {
    const link = event.target.closest('[data-misc-note]');
    if (!link) return;
    event.preventDefault();
    const row = link.closest('tr');
    if (row) openMiscNote(row);
  });

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

    // The "Notes for selected row" box and the Miscellaneous text box edit the
    // same hidden note, so either one can be used for any row.
    const noteInput = document.getElementById('notesInput');
    if (noteInput) {
      noteInput.addEventListener('input', function () {
        updateSelectedNote(noteInput.value);
      });
    }

    const addMiscBtn = document.getElementById('addMiscBtn');
    if (addMiscBtn) addMiscBtn.addEventListener('click', addMisc);

    const miscNoteText = document.getElementById('miscNoteText');
    if (miscNoteText) {
      miscNoteText.addEventListener('input', function () {
        updateSelectedNote(miscNoteText.value);
      });
    }
    const miscNoteClose = document.getElementById('miscNoteClose');
    if (miscNoteClose) miscNoteClose.addEventListener('click', closeMiscNote);
    const miscNoteDone = document.getElementById('miscNoteDone');
    if (miscNoteDone) miscNoteDone.addEventListener('click', closeMiscNote);
    const miscNoteModal = document.getElementById('miscNoteModal');
    if (miscNoteModal) {
      miscNoteModal.addEventListener('click', function (event) {
        if (event.target === miscNoteModal) closeMiscNote();
      });
    }

    document.addEventListener('planner:refresh', load);

    load();
  });
})();
