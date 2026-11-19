// portfolio-summary.js - the Portfolio Summary widget strip on top of the
// dashboard (BONDBP_V6 #2 for the layout, MFR_V11 for the Mutual Funds card).
//
// #6 is the important one: every figure and every date boundary arrives from
// the server already computed (/api/portfolio/summary/bonds and
// /api/portfolio/summary/mutual-funds). This module only formats (Rs +
// "25 Oct 2026") and paints what it is given - no arithmetic here, ever.
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

  // ------------------------------------------------- MFR_V11 Mutual Funds --
  // #2: one row per period, four figures - Investment and Market Value for
  // Self, then the same two for Family. MFR_V12 #2-#5: both figures are
  // computed on the server (amount sum without the ADJUSTED rows, and the
  // period's units x each scheme's own NAV; the total comes from the statement),
  // so this function only formats them. #6: a market value below the money put
  // in arrives with its loss flag set and is painted red here.
  function mfBody() {
    return document.querySelector('#mfWidgetTable tbody');
  }

  function renderMf(summary) {
    const body = mfBody();
    if (!body) return;
    body.innerHTML = (summary.rows || []).map(row => `
      <tr>
        <td class="left">
          <span class="widget-label">${esc(row.label)}</span>
          <span class="widget-range">${esc(fmtRange(row))}</span>
        </td>
        <td class="amt">${money(row.selfInvestment)}</td>
        <td class="amt${row.selfInLoss ? ' loss' : ''}">${money(row.selfMarketValue)}</td>
        <td class="amt family">${money(row.familyInvestment)}</td>
        <td class="amt family${row.familyInLoss ? ' loss' : ''}">${money(row.familyMarketValue)}</td>
      </tr>`).join('');

    const foot = document.getElementById('mfWidgetFoot');
    if (foot) {
      foot.textContent = 'As on ' + fmtDate(summary.asOf) + ' · valued at scheme NAV';
    }
    if (window.App && window.App.renderIcons) window.App.renderIcons();
  }

  function renderMfMessage(message) {
    const body = mfBody();
    if (body) {
      body.innerHTML = `<tr><td colspan="5" class="muted">${esc(message)}</td></tr>`;
    }
  }

  // ------------------------------------------------- MP_V4 Monthly Planner --
  // #4/#5: four figures per row (Self current / next, Family current / next).
  // #6: the server has already merged every Miscellaneous row into one, and
  // only rows with a positive amount somewhere are present. The page only
  // formats the values it receives.
  function plannerBody() {
    return document.querySelector('#plannerWidgetTable tbody');
  }

  function plannerCells(row) {
    return '<td class="amt">' + money(row.selfCurrent) + '</td>'
      + '<td class="amt">' + money(row.selfNext) + '</td>'
      + '<td class="amt family">' + money(row.familyCurrent) + '</td>'
      + '<td class="amt family">' + money(row.familyNext) + '</td>';
  }

  function plannerRows(rows) {
    return rows.map(row => `
      <tr${row.misc ? ' class="widget-misc"' : ''}>
        <td class="left"><span class="widget-label">${esc(row.label)}</span></td>
        ${plannerCells(row)}
      </tr>`).join('');
  }

  function plannerTotalRow(label, selfCurrent, selfNext, familyCurrent, familyNext) {
    return `<tr class="widget-total">
        <td class="left"><strong>${esc(label)}</strong></td>
        <td class="amt"><strong>${money(selfCurrent)}</strong></td>
        <td class="amt"><strong>${money(selfNext)}</strong></td>
        <td class="amt family"><strong>${money(familyCurrent)}</strong></td>
        <td class="amt family"><strong>${money(familyNext)}</strong></td>
      </tr>`;
  }

  function plannerSection(title, rows, totalLabel, selfCurrent, selfNext,
                          familyCurrent, familyNext, emptyText) {
    return '<tr class="widget-section"><td colspan="5">' + esc(title) + '</td></tr>'
      + (rows.length
          ? plannerRows(rows)
          : '<tr><td colspan="5" class="muted left">' + esc(emptyText) + '</td></tr>')
      + plannerTotalRow(totalLabel, selfCurrent, selfNext, familyCurrent, familyNext);
  }

  function netCell(value, family) {
    return '<td class="amt' + (family ? ' family' : '') + (value < 0 ? ' loss' : '') + '">'
      + money(value) + '</td>';
  }

  function plannerNetRow(summary) {
    return '<tr class="widget-net">'
      + '<td class="left"><strong>Net</strong></td>'
      + netCell(summary.selfNetCurrent, false)
      + netCell(summary.selfNetNext, false)
      + netCell(summary.familyNetCurrent, true)
      + netCell(summary.familyNetNext, true)
      + '</tr>';
  }

  function renderPlanner(summary) {
    const body = plannerBody();
    if (!body) return;
    const earnings = summary.earnings || [];
    const spending = summary.spending || [];

    body.innerHTML = plannerSection('Earnings', earnings, 'Total Earnings',
        summary.selfEarningsCurrent, summary.selfEarningsNext,
        summary.familyEarningsCurrent, summary.familyEarningsNext,
        'No earnings recorded for these months')
      + plannerSection('Spending (Obligations)', spending, 'Total Spending',
        summary.selfSpendingCurrent, summary.selfSpendingNext,
        summary.familySpendingCurrent, summary.familySpendingNext,
        'No spending recorded for these months')
      + plannerNetRow(summary);

    const foot = document.getElementById('plannerWidgetFoot');
    if (foot) {
      foot.textContent = 'As on ' + fmtDate(summary.asOf)
        + ' · ' + summary.currentMonthLabel + ' / ' + summary.nextMonthLabel
        + ' · Family = you + your family members';
    }
    if (window.App && window.App.renderIcons) window.App.renderIcons();
  }

  function renderPlannerMessage(message) {
    const body = plannerBody();
    if (body) {
      body.innerHTML = `<tr><td colspan="5" class="muted">${esc(message)}</td></tr>`;
    }
  }

  Api.guard(Api.get('/api/portfolio/summary/monthly-planner'),
            'Could not load the Monthly Planner widget')
    .then(function (summary) {
      if (!summary) {
        renderPlannerMessage('Summary unavailable');
        return;
      }
      renderPlanner(summary);
    });

  Api.guard(Api.get('/api/portfolio/summary/bonds'), 'Could not load the Bonds widget')
    .then(function (summary) {
      if (!summary) {
        renderMessage('Summary unavailable');
        return;
      }
      render(summary);
    });

  Api.guard(Api.get('/api/portfolio/summary/mutual-funds'),
            'Could not load the Mutual Funds widget')
    .then(function (summary) {
      if (!summary) {
        renderMfMessage('Summary unavailable');
        return;
      }
      renderMf(summary);
    });
})();
