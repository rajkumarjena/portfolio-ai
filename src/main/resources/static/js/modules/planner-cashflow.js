// planner-cashflow.js - MP_V1 #2 Cash-Flow Report: the selected month and the
// projected month(s) side by side, computed entirely on the server.
(function () {
  'use strict';

  let months = [];

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;');

  const money = n => (Number(n) || 0).toLocaleString('en-IN', {
    minimumFractionDigits: 2, maximumFractionDigits: 2
  });

  function monthInput() {
    return document.getElementById('planMonth');
  }

  function currentMonthValue() {
    const input = monthInput();
    if (input && input.value) return input.value;
    const now = new Date();
    return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
  }

  function banner(message, ok) {
    const el = document.getElementById('plannerBanner');
    if (!el) return;
    el.textContent = message || '';
    el.style.display = message ? 'block' : 'none';
    el.classList.toggle('error', ok === false);
  }

  /** One block of category rows plus its total, one cell per month. */
  function section(title, linesKey, totalKey) {
    const first = months[0] || {};
    const lines = first[linesKey] || [];
    let html = `<tr><td class="left" colspan="${months.length + 1}"><strong>${esc(title)}</strong></td></tr>`;
    lines.forEach(function (line, index) {
      html += '<tr><td class="left">' + esc(line.label) + '</td>'
        + months.map(function (month) {
            const row = (month[linesKey] || [])[index] || {};
            return '<td class="amt">' + money(row.amount) + '</td>';
          }).join('')
        + '</tr>';
    });
    html += '<tr><td class="left"><strong>Total ' + esc(title) + '</strong></td>'
      + months.map(month => '<td class="amt"><strong>' + money(month[totalKey]) + '</strong></td>').join('')
      + '</tr>';
    return html;
  }

  function render() {
    const head = document.getElementById('cashFlowHead');
    if (head) {
      head.innerHTML = '<tr><th>Category</th>'
        + months.map(month => '<th>' + esc(month.monthLabel) + '</th>').join('')
        + '</tr>';
    }
    const body = document.querySelector('#cashFlowTable tbody');
    if (!body) return;

    let html = '';
    html += section('Earnings', 'earnings', 'earningsTotal');
    html += section('Spending (Obligations)', 'spending', 'spendingTotal');
    html += '<tr><td class="left"><strong>Net cash flow</strong></td>'
      + months.map(month => '<td class="amt"><strong>' + money(month.net) + '</strong></td>').join('')
      + '</tr>';
    body.innerHTML = html;

    if (window.App && window.App.renderIcons) window.App.renderIcons();

    // MP_V2 #6/#7/#8: one status per displayed month.
    const container = document.getElementById('cashFlowStatuses');
    const noteEl = document.getElementById('cashFlowStatusNote');
    const noteReasons = [];
    if (container && months.length) {
      container.innerHTML = months.map(function (m) {
        const earning = Number(m.earningsTotal) || 0;
        const spending = Number(m.spendingTotal) || 0;
        const spendingLines = m.spending || [];
        const unpaidLines = spendingLines.filter(function (l) { return (Number(l.amount) || 0) > 0 && l.paid !== true; });
        const allPaid = spendingLines.length > 0 && unpaidLines.length === 0;
        const covers = earning >= spending;
        const matched = spending === 0 ? (earning === 0) : ((earning - spending) <= 0.01 * spending);
        const green = allPaid && covers && matched;
        const style = 'display:block; background:' + (green ? '#1f7a3d' : '#b26a00') + '; color:#fff;';
        const text = green ? 'Done (all bills paid, earnings cover spending)' : 'In progress';
        if (!green) {
          const reasons = [];
          if (unpaidLines.length) {
            reasons.push(unpaidLines.length + ' spending bill(s) not marked paid'
              + ' (' + unpaidLines.map(function (l) { return esc(l.label); }).join(', ') + ')');
          }
          if (spendingLines.length === 0) {
            reasons.push('no spending obligations found');
          }
          if (earning < spending) {
            reasons.push('earnings are less than spending');
          } else if (!matched) {
            reasons.push('earnings exceed spending by more than 1%');
          }
          if (reasons.length === 0) reasons.push('status conditions not met');
          noteReasons.push('<strong>' + esc(m.monthLabel) + ':</strong> ' + reasons.join('; ') + '.');
        }
        return '<div class="status-banner" style="' + style + '">' + esc(m.monthLabel) + ' Status: ' + text + '</div>';
      }).join('');
    }
    if (noteEl) {
      if (noteReasons.length) {
        noteEl.innerHTML = '<div style="padding:8px 12px; border:1px solid #b26a00; background:#fff7e6; color:#5a3e00; border-radius:6px;">'
          + '<strong>Why status is in progress:</strong> '
          + noteReasons.join(' ') + '</div>';
        noteEl.style.display = 'block';
      } else {
        noteEl.innerHTML = '';
        noteEl.style.display = 'none';
      }
    }
  }

  function load() {
    Api.guard(Api.get('/api/planner/cashflow?month=' + encodeURIComponent(currentMonthValue())),
      'Could not load the cash-flow report')
      .then(function (result) {
        if (!result || !result.length) return;
        months = result;
        render();
      });
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
    const refresh = document.getElementById('refreshBtn');
    if (refresh) refresh.addEventListener('click', load);
    banner('');

    load();
  });
})();
