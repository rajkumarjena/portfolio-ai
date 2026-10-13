// csrf.js - shared by every page (include BEFORE the page's own script).
//
// Spring Security CSRF is enabled with a readable XSRF-TOKEN cookie. This helper:
//   1. ensures the cookie exists (calls /api/auth/csrf once if needed),
//   2. patches window.fetch to attach X-XSRF-TOKEN on every unsafe request,
//   3. retries once with a refreshed token if the server answers 403,
//   4. exposes window.getCsrfToken() for forms that post natively (login form).
(function () {
    'use strict';

    function readCookie(name) {
        const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[1]) : null;
    }

    let tokenPromise = null;

    async function fetchTokenFromServer() {
        const res = await fetch('/api/auth/csrf', { method: 'GET' });
        if (!res.ok) throw new Error('CSRF endpoint failed: ' + res.status);
        const data = await res.json();
        return data.token;
    }

    // Returns the current raw CSRF token (cookie first, server as fallback).
    function getCsrfToken() {
        if (!tokenPromise) {
            const cookie = readCookie('XSRF-TOKEN');
            tokenPromise = cookie ? Promise.resolve(cookie) : fetchTokenFromServer();
            // Allow a refresh after rotation (login/logout) or failure
            tokenPromise.catch(function () { tokenPromise = null; });
        }
        return tokenPromise;
    }

    function invalidateToken() {
        tokenPromise = null;
    }

    const nativeFetch = window.fetch.bind(window);

    window.fetch = async function (input, init) {
        init = init || {};
        const method = ((init.method) || (typeof input === 'object' && input && input.method) || 'GET')
            .toString().toUpperCase();
        const safe = method === 'GET' || method === 'HEAD' || method === 'OPTIONS';

        if (safe) {
            return nativeFetch(input, init);
        }

        const attempt = async (isRetry) => {
            const token = await getCsrfToken();
            const headers = Object.assign({}, init.headers);
            headers['X-XSRF-TOKEN'] = token;
            const res = await nativeFetch(input, Object.assign({}, init, { headers: headers }));
            if (res.status === 403 && !isRetry) {
                // Token rotated or missing - refresh once and retry the request
                invalidateToken();
                return attempt(true);
            }
            return res;
        };
        return attempt(false);
    };

    window.getCsrfToken = getCsrfToken;
    window.invalidateCsrfToken = invalidateToken;
})();
