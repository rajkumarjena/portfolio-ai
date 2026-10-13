// reset-password-script.js - reads ?token=..., validates it, then sets a new password
document.addEventListener('DOMContentLoaded', async () => {
    const form = document.getElementById('resetForm');
    const result = document.getElementById('result');
    const token = new URLSearchParams(window.location.search).get('token') || '';

    function show(kind, text) {
        result.className = 'result ' + kind;
        result.textContent = text;
        result.style.display = 'block';
    }

    if (!token) {
        show('error', 'This reset link is incomplete. Request a new one from the '
            + 'forgot-password page.');
        form.style.display = 'none';
        return;
    }

    // Reject dead/expired links before asking the user for input
    try {
        const check = await fetch('/api/auth/validate-token?token=' + encodeURIComponent(token));
        if (!check.ok) {
            show('error', 'This reset link is invalid or has expired. Request a new one.');
            form.style.display = 'none';
            return;
        }
    } catch (e) {
        console.error('Token validation error', e);
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        result.style.display = 'none';

        const password = document.getElementById('password').value;
        const confirm = document.getElementById('confirmPassword').value;
        if (password !== confirm) {
            show('error', 'Passwords do not match.');
            return;
        }

        try {
            const response = await fetch('/api/auth/reset-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token: token, password: password })
            });
            const data = await response.json().catch(() => ({}));
            if (response.ok) {
                window.location.href = '/login.html?reset=1';
            } else {
                show('error', data.message || 'Could not reset the password (HTTP '
                    + response.status + ')');
            }
        } catch (error) {
            console.error('Reset password error:', error);
            show('error', 'A network error occurred. Please try again.');
        }
    });
});
