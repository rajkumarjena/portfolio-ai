// login-script.js - fills CSRF/next fields and surfaces redirect-message banners
document.addEventListener('DOMContentLoaded', async () => {
    const params = new URLSearchParams(window.location.search);
    const banner = document.getElementById('banner');

    function show(kind, text) {
        banner.className = 'banner ' + kind;
        banner.textContent = text;
        banner.style.display = 'block';
    }

    if (params.get('locked') === '1') {
        show('warn', 'Account temporarily locked after too many failed attempts. '
            + 'Try again in 15 minutes or reset your password.');
    } else if (params.get('status') === 'HOLD') {
        // BONDBP_V10 #4
        show('warn', 'Your account is on hold. Please contact your administrator.');
    } else if (params.get('status') === 'CANCELLED') {
        // BONDBP_V10 #4
        show('error', 'Your account is cancelled. Please contact your administrator.');
    } else if (params.get('status') === 'HOLD') {
        // BONDBP_V10 #4
        show('warn', 'Your account is on hold. Please contact your administrator.');
    } else if (params.get('status') === 'CANCELLED') {
        // BONDBP_V10 #4
        show('error', 'Your account is cancelled. Please contact your administrator.');
    } else if (params.get('status') === 'HOLD') {
        // BONDBP_V10 #4
        show('warn', 'Your account is on hold. Please contact your administrator.');
    } else if (params.get('status') === 'CANCELLED') {
        // BONDBP_V10 #4
        show('error', 'Your account is cancelled. Please contact your administrator.');
    } else if (params.get('error') === '1') {
        show('error', 'Invalid PAN or password.');
    } else if (params.get('logout') === '1') {
        show('info', 'You have been signed out.');
    } else if (params.get('registered') === '1') {
        show('info', 'Registration successful. Please sign in.');
    } else if (params.get('reset') === '1') {
        show('info', 'Password updated. Please sign in with your new password.');
    }

    // Deep link support: /login.html?next=%2Fbonds%2Fbond-form.html
    const next = params.get('next');
    if (next && next.startsWith('/') && !next.startsWith('//')) {
        document.getElementById('nextField').value = next;
    }

    // Native form post needs the raw token in the hidden _csrf field
    try {
        document.getElementById('csrfField').value = await window.getCsrfToken();
    } catch (e) {
        console.error('Could not obtain CSRF token', e);
    }
});
