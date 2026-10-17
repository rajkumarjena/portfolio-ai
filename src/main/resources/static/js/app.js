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
    });

    // Expose for inline handlers in the layout / templates.
    window.App = {
        logout: logout,
        renderIcons: renderIcons
    };
})();
