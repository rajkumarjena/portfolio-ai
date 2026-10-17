// register-script.js - validates input client-side then POSTs /api/auth/register
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('registerForm');
    const errorBox = document.getElementById('formError');

    function showError(text) {
        errorBox.textContent = text;
        errorBox.style.display = 'block';
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        errorBox.style.display = 'none';

        const payload = {
            name: document.getElementById('name').value.trim(),
            pan: document.getElementById('pan').value.trim().toUpperCase(),
            email: document.getElementById('email').value.trim(),
            mobileNumber: document.getElementById('mobileNumber').value.trim(),
            password: document.getElementById('password').value
        };
        const confirm = document.getElementById('confirmPassword').value;

        if (payload.password !== confirm) {
            showError('Passwords do not match.');
            return;
        }

        try {
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await response.json().catch(() => ({}));
            if (response.ok) {
                window.location.href = '/login.html?registered=1';
            } else {
                showError(data.message || 'Registration failed (HTTP ' + response.status + ')');
            }
        } catch (error) {
            console.error('Registration error:', error);
            showError('A network error occurred. Please try again.');
        }
    });
});
