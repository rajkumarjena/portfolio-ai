// api.js - the single HTTP utility every page uses to talk to the Spring Boot
// REST APIs (CONSISTENCY.md 3). It layers uniform behaviour on top of the
// CSRF-patched fetch from csrf.js:
//   * JSON in / JSON out with one error shape,
//   * a global loading indicator so every screen shows the same feedback,
//   * one shared error surface (Api.showError) plus a redirect to the login
//     screen when the session has expired (401).
(function () {
    'use strict';

    // ------------------------------------------------------ loading overlay
    var overlay = null;
    var pending = 0;

    function ensureOverlay() {
        if (overlay) return overlay;
        overlay = document.createElement('div');
        overlay.id = 'apiLoading';
        overlay.style.cssText = 'position:fixed;top:0;left:0;height:3px;width:0;'
            + 'background:#0056b3;z-index:999;transition:width .25s ease;pointer-events:none;';
        document.body.appendChild(overlay);
        return overlay;
    }

    function loading(on) {
        pending = Math.max(0, pending + (on ? 1 : -1));
        var el = ensureOverlay();
        el.style.width = pending === 0 ? '0' : '65%';
        if (pending === 0) {
            setTimeout(function () { if (pending === 0) el.style.width = '0'; }, 250);
        }
    }

    // ---------------------------------------------------------- error toast
    var toast = null;

    function showError(message) {
        if (!toast) {
            toast = document.createElement('div');
            toast.style.cssText = 'position:fixed;right:16px;bottom:16px;max-width:420px;'
                + 'background:#fbe3e5;border-left:5px solid #dc3545;color:#8f1d26;'
                + 'padding:12px 16px;border-radius:8px;font-weight:600;z-index:1000;'
                + 'box-shadow:0 4px 14px rgba(20,32,48,.18);cursor:pointer;';
            toast.addEventListener('click', function () { toast.style.display = 'none'; });
            document.body.appendChild(toast);
        }
        toast.textContent = message;
        toast.style.display = 'block';
        clearTimeout(toast._timer);
        toast._timer = setTimeout(function () { toast.style.display = 'none'; }, 6000);
    }

    // --------------------------------------------------------------- core
    async function request(method, url, body) {
        loading(true);
        try {
            var init = { method: method, headers: {} };
            if (body !== undefined) {
                init.headers['Content-Type'] = 'application/json';
                init.body = JSON.stringify(body);
            }
            var res = await window.fetch(url, init);

            if (res.status === 401) {
                window.location.href = '/login.html?next='
                    + encodeURIComponent(window.location.pathname + window.location.search);
                throw new Error('Session expired - please sign in again');
            }

            var data = null;
            var text = await res.text();
            if (text) {
                try { data = JSON.parse(text); } catch (e) { data = text; }
            }

            if (!res.ok) {
                var message = (data && data.message)
                    || (typeof data === 'string' && data)
                    || ('Request failed (HTTP ' + res.status + ')');
                throw new Error(message);
            }
            return data;
        } finally {
            loading(false);
        }
    }

    /** Wraps a call so failures land in the shared toast; returns null on error. */
    function guard(promise, onMessage) {
        return promise
            .then(function (data) { return data; })
            .catch(function (err) {
                showError(onMessage ? onMessage + ': ' + err.message : err.message);
                return null;
            });
    }

    window.Api = {
        get: function (url) { return request('GET', url); },
        post: function (url, body) { return request('POST', url, body); },
        put: function (url, body) { return request('PUT', url, body); },
        del: function (url) { return request('DELETE', url); },
        guard: guard,
        showError: showError
    };
})();
