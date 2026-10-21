// admin-db-query.js - ADMIN_V1 Database Query screen: pick a SQL category,
// type the statement, execute it and read the outcome right here. A successful
// DQL query downloads its result as CSV (column headers first, then every
// returned row); DML/DDL/TCL only show "Status: Success". All validation and
// execution rules live on the server - the checks here just fail fast.
(function () {
  'use strict';

  function el(id) { return document.getElementById(id); }

  // #4: the status / error area on this screen (green = ok, red = failure).
  function status(message, ok) {
    var box = el('statusBanner');
    if (!box) return;
    box.textContent = message || '';
    box.style.display = message ? 'block' : 'none';
    box.classList.toggle('error', !ok);
    box.classList.toggle('info', !!ok);
  }

  // RFC 4180 quoting: wrap cells that carry commas, quotes or line breaks.
  function csvCell(value) {
    var s = value == null ? '' : String(value);
    return /[",\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  }

  function fileStamp() {
    var d = new Date(), pad = function (v) { return String(v).padStart(2, '0'); };
    return d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate())
      + '-' + pad(d.getHours()) + pad(d.getMinutes()) + pad(d.getSeconds());
  }

  // DQL result -> CSV: header row of column names, then all data rows,
  // downloaded straight to the browser.
  function downloadCsv(result) {
    var lines = [(result.columns || []).map(csvCell).join(',')];
    (result.rows || []).forEach(function (row) {
      lines.push(row.map(csvCell).join(','));
    });
    var blob = new Blob(['\ufeff' + lines.join('\r\n') + '\r\n'],
      { type: 'text/csv;charset=utf-8;' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = 'db-query-' + fileStamp() + '.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
  }

  function execute(event) {
    event.preventDefault();
    status('');

    // Flow step 1/2: validate input before anything is sent.
    var category = el('sqlCategory').value;
    var sql = el('sqlInput').value.trim();
    if (!category) {
      status('Validation error: select a SQL category (DQL, DML, DDL or TCL) first.', false);
      return;
    }
    if (!sql) {
      status('Validation error: enter a SQL query to execute.', false);
      return;
    }

    var button = el('executeBtn');
    button.disabled = true;

    Api.post('/api/admin/query', { category: category, sql: sql })
      .then(function (result) {
        if (!result) return; // Api already surfaced the transport problem
        if (result.category === 'DQL') {
          downloadCsv(result);
          status('Status: Success - ' + result.rowCount + ' row(s) returned, CSV downloaded'
            + (result.truncated ? ' (result capped at the configured maximum size)' : '') + '.',
            true);
        } else {
          var affected = (typeof result.rowsAffected === 'number' && result.rowsAffected >= 0)
            ? ' - ' + result.rowsAffected + ' row(s) affected' : '';
          status('Status: Success' + affected + '.', true);
        }
      })
      .catch(function (err) {
        // Execution failed: show the actual database error on this screen.
        status('Error: ' + err.message, false);
      })
      .finally(function () {
        button.disabled = false;
      });
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (window.APP_USER_ROLE !== 'ADMIN') {
      // The server blocks /api/admin/** anyway; fail fast with a clear message.
      status('You need the ADMIN role to use Database Query.', false);
      el('executeBtn').disabled = true;
      return;
    }
    el('dbQueryForm').addEventListener('submit', execute);
    if (window.App && window.App.renderIcons) window.App.renderIcons();
  });
})();
