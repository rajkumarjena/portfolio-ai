// admin-db-backup.js - BACKUP.md: the admin-only Database Backup and Refresh
// screen. Three operations:
//   1. Backup  - POST start (server drains 8s + snapshots), then download it;
//   2. Refresh - upload a local portfolio.db and replace the server's file;
//   3. Log     - download springboot.log (the server clears it to 0 bytes).
// The red warning banner everywhere is handled centrally by app.js polling
// /api/maintenance/status - this module only drives the buttons and feedback.
(function () {
    'use strict';

    var running = false;
    var statusTimer = null;

    function el(id) { return document.getElementById(id); }

    function feedback(kind, text) {
        var box = el('feedback');
        box.textContent = text || '';
        box.className = kind || '';
    }

    function live(text) {
        el('liveStatus').textContent = text || '';
    }

    function setDisabled(disabled) {
        ['btnBackup', 'btnRefresh', 'btnLog', 'refreshFile'].forEach(function (id) {
            var node = el(id);
            if (node) node.disabled = disabled;
        });
        running = disabled;
    }

    // While a process runs, show the server's live phase text once a second.
    function startStatusWatch() {
        stopStatusWatch();
        statusTimer = setInterval(function () {
            fetch('/api/maintenance/status', { cache: 'no-store' })
                .then(function (r) { return r.ok ? r.json() : null; })
                .then(function (s) { if (s && s.active) live(s.message); })
                .catch(function () { });
        }, 1000);
    }

    function stopStatusWatch() {
        if (statusTimer) {
            clearInterval(statusTimer);
            statusTimer = null;
        }
    }

    // Waits until the server reports the hold is released (banner gone).
    function waitForIdle(maxSeconds) {
        var deadline = Date.now() + maxSeconds * 1000;
        return new Promise(function (resolve) {
            (function check() {
                fetch('/api/maintenance/status', { cache: 'no-store' })
                    .then(function (r) { return r.ok ? r.json() : null; })
                    .then(function (s) {
                        if (s && !s.active) return resolve(true);
                        if (Date.now() > deadline) return resolve(false);
                        setTimeout(check, 500);
                    })
                    .catch(function () {
                        if (Date.now() > deadline) return resolve(false);
                        setTimeout(check, 500);
                    });
            })();
        });
    }

    function errorOf(res, fallback) {
        return res.json()
            .catch(function () { return {}; })
            .then(function (data) {
                return data && data.message
                    ? data.message
                    : fallback + ' (HTTP ' + res.status + ')';
            });
    }

    // ------------------------------------------------------------- backup --
    async function runBackup() {
        if (running) return;
        setDisabled(true);
        startStatusWatch();
        feedback('', 'Holding new requests and creating a database snapshot - about 10 seconds...');
        live('Preparing the database backup...');

        try {
            var res = await fetch('/api/admin/maintenance/backup', { method: 'POST' });
            if (!res.ok) {
                feedback('fail', await errorOf(res, 'Backup failed'));
                return;
            }
            var data = await res.json();
            live('Snapshot ready - downloading ' + data.fileName + '...');

            var a = document.createElement('a');
            a.href = data.downloadUrl;
            a.download = '';
            document.body.appendChild(a);
            a.click();
            a.remove();

            var idle = await waitForIdle(60);
            feedback('ok', idle
                ? 'Backup complete: ' + data.fileName + ' was downloaded. '
                    + 'The warning banner disappears everywhere automatically.'
                : 'The download started; the server is still finishing the backup.');
        } catch (e) {
            feedback('fail', 'Network error while taking the backup: ' + e.message);
        } finally {
            stopStatusWatch();
            setDisabled(false);
            live('');
            if (window.App && window.App.pollMaintenance) window.App.pollMaintenance();
        }
    }

    // ------------------------------------------------------------ refresh --
    async function runRefresh() {
        if (running) return;
        var input = el('refreshFile');
        var file = input.files && input.files[0];
        if (!file) {
            feedback('fail', 'Choose a portfolio.db file from this computer first.');
            return;
        }
        var confirmed = window.confirm(
            'Replace the SERVER database with "' + file.name + '"?\n\n'
            + 'All current server data will be replaced by the contents of the uploaded file.');
        if (!confirmed) return;

        setDisabled(true);
        startStatusWatch();
        feedback('', 'Uploading ' + file.name + ' - the application is paused while the database is replaced...');
        live('Refreshing the database...');

        try {
            var form = new FormData();
            form.append('file', file);
            var res = await fetch('/api/admin/maintenance/refresh', { method: 'POST', body: form });
            if (!res.ok) {
                feedback('fail', await errorOf(res, 'Refresh failed'));
            } else {
                var data = await res.json();
                feedback('ok', 'Database refreshed from "' + data.fileName + '". '
                    + 'Reload this page to work with the new data.');
            }
        } catch (e) {
            feedback('fail', 'Network error while refreshing: ' + e.message);
        } finally {
            stopStatusWatch();
            setDisabled(false);
            live('');
            if (window.App && window.App.pollMaintenance) window.App.pollMaintenance();
        }
    }

    // ----------------------------------------------------------- log file --
    async function runLog() {
        if (running) return;
        setDisabled(true);
        feedback('', 'Downloading the application log...');

        try {
            var res = await fetch('/api/admin/maintenance/log');
            if (!res.ok) {
                feedback('fail', await errorOf(res, 'Log download failed'));
                return;
            }
            var blob = await res.blob();
            var disposition = res.headers.get('Content-Disposition') || '';
            var match = disposition.match(/filename="?([^";]+)"?/);
            var fileName = match ? match[1] : 'springboot.log';

            var url = URL.createObjectURL(blob);
            var a = document.createElement('a');
            a.href = url;
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(function () { URL.revokeObjectURL(url); }, 5000);

            feedback('ok', 'Downloaded ' + fileName + ' - the log file on the server '
                + 'is now cleared (0 bytes).');
        } catch (e) {
            feedback('fail', 'Network error while downloading the log: ' + e.message);
        } finally {
            setDisabled(false);
        }
    }

    // ------------------------------------------------------------- boot ----
    document.addEventListener('DOMContentLoaded', async function () {
        if (window.APP_USER_ROLE !== 'ADMIN') {
            // The server blocks these calls anyway; fail fast with a clear message.
            feedback('fail', 'You need the ADMIN role to use Database Backup and Refresh.');
            setDisabled(true);
            return;
        }

        el('btnBackup').addEventListener('click', runBackup);
        el('btnRefresh').addEventListener('click', runRefresh);
        el('btnLog').addEventListener('click', runLog);

        // If a process is already running when the page opens, reflect it.
        try {
            var res = await fetch('/api/maintenance/status', { cache: 'no-store' });
            if (res.ok) {
                var state = await res.json();
                if (state.active) {
                    setDisabled(true);
                    startStatusWatch();
                    feedback('fail', 'A database process is already running - '
                        + 'wait for it to finish before starting another.');
                }
            }
        } catch (e) { /* status unavailable - buttons stay usable */ }
    });
})();
