// forgot-password-script.js - always shows the generic success message so the
// server response cannot be used to probe which PANs/emails exist.
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('forgotForm');
    const result = document.getElementById('result');

    function show(kind, text) {
        result.className = 'result ' + kind;
        result.textContent = text;
        result.style.display = 'block';
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        result.style.display = 'none';

        const payload = {
            pan: document.getElementById('pan').value.trim().toUpperCase(),
            email: document.getElementById('email').value.trim()
        };

        try {
            const response = await fetch('/api/auth/forgot-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await response.json().catch(() => ({}));
            if (response.ok) {
                show('info', data.message || 'If that PAN and email match an account, '
                    + 'a password reset link has been sent.');
                form.reset();
            } else {
                show('error', data.message || 'Something went wrong. Please try again.');
            }
        } catch (error) {
            console.error('Forgot password error:', error);
            show('error', 'A network error occurred. Please try again.');
        }
    });
});
