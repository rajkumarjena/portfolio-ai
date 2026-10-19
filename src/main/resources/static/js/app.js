// app.js - core, site-wide behaviour loaded once in the master layout
// (CONSISTENCY.md 3): mobile sidebar toggle, Lucide icon rendering, logout,
// active-link highlighting and a session-expiry safety net.
(function () {
    'use strict';

    // Render any <i data-lucide="..."> placeholders (layout + page content).
    function renderIcons() {
        if (window.lucide && typeof window.lucide.createIcons === 'function') {
            window.lucide.createIcons();
        }
    }

    // Highlight the sidebar link for the page currently open.
    function markActiveLink() {
        var here = window.location.pathname || '/';
        document.querySelectorAll('.sidebar-nav .nav-link').forEach(function (link) {
            var target = link.getAttribute('href');
            link.classList.toggle('active', target === here);
        });
    }

    // BACKUP.md 4/5: the site-wide maintenance warning. Every page (signed-in
    // and signed-out screens alike) polls the public status endpoint, so the
    // banner appears the moment an admin starts a backup/refresh and vanishes
    // by itself when the process is over.
    var maintenanceTimer = null;

    function applyMaintenance(state) {
        var banner = document.getElementById('maintBanner');
        if (!banner) return;
        var visible = !!(state && state.active);
        banner.classList.toggle('visible', visible);
        var text = document.getElementById('maintBannerText');
        if (text && state && state.message) {
            text.textContent = state.message;
        }
        // Keep the sticky topbar/sidebar below the banner while it shows.
        document.documentElement.style.setProperty('--maint-banner-h',
            visible ? (banner.offsetHeight || 0) + 'px' : '0px');
    }

    function pollMaintenance() {
        return fetch('/api/maintenance/status', { cache: 'no-store' })
            .then(function (res) { return res.ok ? res.json() : null; })
            .then(function (state) { if (state) applyMaintenance(state); return state; })
            .catch(function () { /* transient failure - keep the last state */ return null; });
    }

    function startMaintenancePolling() {
        if (maintenanceTimer) return;
        pollMaintenance();
        maintenanceTimer = setInterval(pollMaintenance, 3000);
        window.addEventListener('resize', function () {
            var banner = document.getElementById('maintBanner');
            if (banner && banner.classList.contains('visible')) {
                document.documentElement.style.setProperty('--maint-banner-h',
                    (banner.offsetHeight || 0) + 'px');
            }
        });
    }

    async function logout() {
        try {
            await fetch('/logout', { method: 'POST' });
        } finally {
            window.invalidateCsrfToken && window.invalidateCsrfToken();
            window.location.href = '/login.html?logout=1';
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        var toggle = document.getElementById('menuToggle');
        var sidebar = document.getElementById('sidebar');
        if (toggle && sidebar) {
            toggle.addEventListener('click', function () {
                sidebar.classList.toggle('open');
            });
        }

        var topbarTitle = document.querySelector('.topbar-title');
        if (topbarTitle) {
            topbarTitle.textContent = document.title.replace(/\s*\|\s*Portfolio\s*$/, '');
        }

        markActiveLink();
        renderIcons();
        startMaintenancePolling();
    });

    // Expose for inline handlers in the layout / templates.
    window.App = {
        logout: logout,
        renderIcons: renderIcons,
        pollMaintenance: pollMaintenance,
        applyMaintenance: applyMaintenance
    };
})();
