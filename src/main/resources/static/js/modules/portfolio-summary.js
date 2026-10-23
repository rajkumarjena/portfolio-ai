// portfolio-summary.js - BONDBP_V6: the Portfolio Summary widget strip on top
// of the dashboard (#2), currently the Bonds card (#1).
//
// #6 is the important one: every figure and every date boundary arrives from
// the server already computed (/api/portfolio/summary/bonds). This module only
// formats (Rs + "25 Oct 2026") and paints what it is given - no arithmetic
// here, ever.
(function () {
  'use strict';

  const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  const money = n => '₹' + (Number(n) || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  // "2026-10-25" -> "25 Oct 2026"
  function fmtDate(iso) {
    if (!iso) return '';
    const parts = String(iso).split('-');
    if (parts.length !== 3) return String(iso);
    return Number(parts[2]) + ' ' + MONTHS[Number(parts[1]) - 1] + ' ' + parts[0];
  }

  // BONDBP_V6 #5: each period row shows its range under the caption,
  // e.g. "20 Oct 2026 - 25 Oct 2026" for the current week. BONDBP_V12 #2: the
  // realised-return row is "up to today" with no lower bound, so it renders as
  // "... - 20 Oct 2026".
  function fmtRange(row) {
    if (row.since && row.to) return fmtDate(row.since) + ' - ' + fmtDate(row.to);
    if (row.from && row.to) return fmtDate(row.from) + ' - ' + fmtDate(row.to);
    if (row.to) return '\u2026 - ' + fmtDate(row.to);
    return '';
  }

  function tbody() {
    return document.querySelector('#bondWidgetTable tbody');
  }

  function render(summary) {
    const body = tbody();
    if (!body) return;
    body.innerHTML = (summary.rows || []).map(row => `
      <tr>
        <td class="left">
          <span class="widget-label">${esc(row.label)}</span>
          <span class="widget-range">${esc(fmtRange(row))}</span>
        </td>
        <td class="amt">${money(row.self)}</td>
        <td class="amt family">${money(row.family)}</td>
      </tr>`).join('');

    const foot = document.getElementById('bondWidgetFoot');
    if (foot) {
      // #4 in one line so the columns are self-explanatory on the widget itself.
      foot.textContent = 'As on ' + fmtDate(summary.asOf)
        + ' · Family = you + your family members';
    }
    if (window.App && window.App.renderIcons) window.App.renderIcons();
  }

  function renderMessage(message) {
    const body = tbody();
    if (body) {
      body.innerHTML = `<tr><td colspan="3" class="muted">${esc(message)}</td></tr>`;
    }
  }

  Api.guard(Api.get('/api/portfolio/summary/bonds'), 'Could not load the Bonds widget')
    .then(function (summary) {
      if (!summary) {
        renderMessage('Summary unavailable');
        return;
      }
      render(summary);
    });
})();
