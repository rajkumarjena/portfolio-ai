// credential-vault.js - CRED_V1 Credential Vault screen.
//
// One page, three panels: Create Vault, Unlock Vault and the credential listing.
// The listing only ever holds masked passwords - a real password arrives only
// from /reveal or /copy, after the Master Password was verified for that single
// request, and it is wiped from the page again after 30 seconds.
document.addEventListener('DOMContentLoaded', () => {
    const MASK = '********';
    const REVEAL_MILLIS = 30000;
    const STATUS_POLL_MILLIS = 25000;

    const PANELS = {
        setup: 'vaultSetupPanel',
        lock: 'vaultLockPanel',
        main: 'vaultMainPanel'
    };

    const banner = document.getElementById('vaultBanner');
    const rowsBody = document.getElementById('vaultRows');
    const emptyNote = document.getElementById('vaultEmpty');

    let credentials = [];
    let currentState = null;
    let editingId = null;
    let verifyAction = null;
    let hideTimer = null;
    let hideAt = 0;

    // ------------------------------------------------------------- helpers
    function el(id) {
        return document.getElementById(id);
    }

    function showBanner(message, tone) {
        banner.className = 'status-banner' + (tone ? ' ' + tone : '');
        banner.textContent = message;
        banner.style.display = 'block';
    }

    function hideBanner() {
        banner.style.display = 'none';
    }

    function openModal(id) {
        el(id).classList.add('open');
    }

    function closeModal(id) {
        el(id).classList.remove('open');
    }

    function closeAllModals() {
        hideRevealed();
        document.querySelectorAll('.modal-overlay.open')
            .forEach(overlay => overlay.classList.remove('open'));
    }

    function showPanel(name) {
        Object.values(PANELS).forEach(id => { el(id).hidden = true; });
        el(PANELS[name]).hidden = false;
    }

    // --------------------------------------------------------------- state
    async function refreshStatus(quiet) {
        const status = await Api.guard(Api.get('/api/vault/status'), 'Could not read the vault state');
        if (!status) return;

        el('vaultIdleHint').textContent = 'Auto-locks after ' + status.idleLockMinutes
            + ' minutes of inactivity.';

        const state = !status.initialized ? 'setup' : (!status.unlocked ? 'lock' : 'main');
        if (state === currentState) return;

        const wasUnlocked = currentState === 'main';
        currentState = state;
        showPanel(state);

        if (state === 'main') {
            hideBanner();
            loadCredentials();
        } else if (state === 'lock' && wasUnlocked && !quiet) {
            showBanner('The vault locked itself after ' + status.idleLockMinutes
                + ' minutes of inactivity. Enter the Master Password to continue.', 'warn');
        }
    }

    async function loadCredentials() {
        const list = await Api.guard(Api.get('/api/vault/credentials'), 'Could not load the credentials');
        if (!list) return;
        credentials = list;
        render();
    }

    // ------------------------------------------------------------- listing
    function cell(label, text, className) {
        const td = document.createElement('td');
        td.dataset.label = label;
        td.textContent = text == null || text === '' ? '\u2014' : text;
        if (className) td.className = className;
        return td;
    }

    function websiteCell(website) {
        const td = document.createElement('td');
        td.dataset.label = 'Website';
        if (!website) {
            td.textContent = '\u2014';
            return td;
        }
        const link = document.createElement('a');
        // Only ever a web link - a stored value cannot inject another scheme.
        link.href = /^https?:\/\//i.test(website) ? website : 'https://' + website;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.textContent = website;
        td.appendChild(link);
        return td;
    }

    function actionButton(icon, label, tone, handler) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-sm' + (tone ? ' ' + tone : '');
        button.innerHTML = '<i data-lucide="' + icon + '"></i>' + label;
        button.title = label;
        button.addEventListener('click', handler);
        return button;
    }

    function actionsCell(credential) {
        const td = document.createElement('td');
        td.dataset.label = 'Actions';

        const group = document.createElement('div');
        group.className = 'vault-actions-cell';
        group.appendChild(actionButton('eye', 'View', null, () => openDetails(credential)));
        group.appendChild(actionButton('pencil', 'Edit', null, () => openForm(credential)));
        group.appendChild(actionButton('key-round', 'Reveal', null, () => askMasterPassword('reveal', credential)));
        group.appendChild(actionButton('copy', 'Copy', null, () => askMasterPassword('copy', credential)));
        group.appendChild(actionButton('trash-2', 'Delete', 'danger', () => removeCredential(credential)));

        td.appendChild(group);
        return td;
    }

    function passwordCell(credential) {
        const td = document.createElement('td');
        td.dataset.label = 'Password';
        const span = document.createElement('span');
        span.className = 'vault-pwd';
        span.id = 'vault-pwd-' + credential.id;
        span.textContent = MASK;
        td.appendChild(span);
        return td;
    }

    function render() {
        rowsBody.innerHTML = '';
        emptyNote.hidden = credentials.length > 0;

        credentials.forEach(credential => {
            const tr = document.createElement('tr');
            tr.appendChild(cell('PAN', credential.clientPan, 'mono'));
            tr.appendChild(cell('App Name', credential.appName));
            tr.appendChild(cell('Username', credential.username));
            tr.appendChild(passwordCell(credential));
            tr.appendChild(websiteCell(credential.website));
            tr.appendChild(actionsCell(credential));
            rowsBody.appendChild(tr);
        });

        if (window.App && typeof App.renderIcons === 'function') App.renderIcons();
    }

    // --------------------------------------------------------- create / edit
    function openForm(credential) {
        editingId = credential ? credential.id : null;
        el('vaultFormTitle').textContent = credential ? 'Edit Credential' : 'Add Credential';

        // The credential always belongs to the signed-in client, so the PAN is
        // their own and stays read-only on the form.
        el('vaultClientPan').value = window.APP_USER_PAN || (credential ? credential.clientPan : '');
        el('vaultAppName').value = credential ? credential.appName : '';
        el('vaultUsername').value = credential ? credential.username : '';
        el('vaultPassword').value = '';
        el('vaultPassword').required = !credential;
        el('vaultPasswordHint').textContent = credential
            ? 'Leave blank to keep the stored password.'
            : 'Stored encrypted with a fresh IV - never in plaintext.';
        el('vaultWebsite').value = credential && credential.website ? credential.website : '';
        el('vaultNotes').value = credential && credential.notes ? credential.notes : '';

        openModal('vaultFormModal');
        setTimeout(() => el('vaultAppName').focus(), 50);
    }

    async function saveCredential(event) {
        event.preventDefault();

        const payload = {
            clientPan: el('vaultClientPan').value,
            appName: el('vaultAppName').value,
            username: el('vaultUsername').value,
            password: el('vaultPassword').value,
            website: el('vaultWebsite').value,
            notes: el('vaultNotes').value
        };

        const url = editingId
            ? '/api/vault/credentials/' + editingId
            : '/api/vault/credentials';
        const saved = await Api.guard(editingId ? Api.put(url, payload) : Api.post(url, payload),
            'Could not save the credential');

        if (!saved) {
            refreshStatus(true);
            return;
        }
        closeModal('vaultFormModal');
        showBanner(editingId ? 'Credential updated.' : 'Credential saved.', 'info');
        editingId = null;
        loadCredentials();
    }

    async function removeCredential(credential) {
        const confirmed = window.confirm(
            'Are you sure you want to delete this credential?\n\n'
            + credential.appName + ' (' + credential.username + ')');
        if (!confirmed) return;

        const list = await Api.guard(Api.del('/api/vault/credentials/' + credential.id),
            'Could not delete the credential');
        if (!list) {
            refreshStatus(true);
            return;
        }
        credentials = list;
        render();
        showBanner('Credential deleted.', 'info');
    }

    // --------------------------------------------------------- reveal / copy
    function askMasterPassword(kind, credential) {
        verifyAction = { kind: kind, id: credential.id };
        el('vaultVerifyTitle').textContent = kind === 'copy' ? 'Copy Password' : 'Verify Master Password';
        el('vaultVerifyHint').textContent = 'Enter the Master Password to '
            + (kind === 'copy' ? 'copy' : 'reveal') + ' the password of '
            + credential.appName + '.';
        el('vaultVerifyPassword').value = '';
        openModal('vaultVerifyModal');
        setTimeout(() => el('vaultVerifyPassword').focus(), 50);
    }

    async function verifyMasterPassword(event) {
        event.preventDefault();
        if (!verifyAction) return;

        const action = verifyAction;
        const result = await Api.guard(
            Api.post('/api/vault/credentials/' + action.id + '/' + action.kind,
                { masterPassword: el('vaultVerifyPassword').value }),
            'Could not verify the Master Password');

        if (!result) return; // a wrong password is already in the toast

        verifyAction = null;
        el('vaultVerifyPassword').value = '';
        closeModal('vaultVerifyModal');

        if (action.kind === 'copy') {
            await copyToClipboard(result.password);
        } else {
            showRevealed(result.password);
        }
    }

    async function copyToClipboard(value) {
        try {
            if (navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(value);
            } else {
                const area = document.createElement('textarea');
                area.value = value;
                area.style.position = 'fixed';
                area.style.opacity = '0';
                document.body.appendChild(area);
                area.select();
                document.execCommand('copy');
                area.remove();
            }
            showBanner('Password copied successfully.', 'info');
        } catch (error) {
            Api.showError('Could not copy the password - the browser blocked clipboard access');
        }
    }

    function showRevealed(password) {
        el('vaultRevealedValue').textContent = password;
        openModal('vaultPasswordModal');
        startCountdown();
    }

    function startCountdown() {
        stopCountdown();
        hideAt = Date.now() + REVEAL_MILLIS;
        const label = el('vaultRevealCountdown');

        const tick = () => {
            const left = Math.max(0, Math.round((hideAt - Date.now()) / 1000));
            label.textContent = 'Hides automatically in ' + left + 's';
            if (left <= 0) hideRevealed();
        };
        tick();
        hideTimer = setInterval(tick, 1000);
    }

    function stopCountdown() {
        if (hideTimer) {
            clearInterval(hideTimer);
            hideTimer = null;
        }
    }

    function hideRevealed() {
        stopCountdown();
        const value = el('vaultRevealedValue');
        if (value) value.textContent = '';
        closeModal('vaultPasswordModal');
    }

    // ------------------------------------------------------------- details
    function openDetails(credential) {
        const body = el('vaultViewBody');
        body.innerHTML = '';
        const fields = [
            ['PAN', credential.clientPan],
            ['Application', credential.appName],
            ['Username', credential.username],
            ['Password', MASK + '  (use Reveal)'],
            ['Website', credential.website],
            ['Notes', credential.notes],
            ['Created', credential.createdAt],
            ['Updated', credential.updatedAt]
        ];
        fields.forEach(([label, value]) => {
            const dt = document.createElement('dt');
            dt.textContent = label;
            const dd = document.createElement('dd');
            dd.textContent = value == null || value === '' ? '\u2014' : value;
            body.appendChild(dt);
            body.appendChild(dd);
        });
        openModal('vaultViewModal');
    }

    // ---------------------------------------------------------- setup/unlock
    async function createVault(event) {
        event.preventDefault();
        const master = el('vaultMaster').value;
        const confirm = el('vaultConfirm').value;

        if (master !== confirm) {
            showBanner('The two Master Passwords do not match.', 'error');
            return;
        }

        const status = await Api.guard(
            Api.post('/api/vault/setup', { masterPassword: master, confirmPassword: confirm }),
            'Could not create the vault');
        if (!status) return;

        el('vaultSetupForm').reset();
        showBanner('Vault created and unlocked. Add your first credential.', 'info');
        currentState = null;
        await refreshStatus(true);
    }

    async function unlockVault(event) {
        event.preventDefault();
        const status = await Api.guard(
            Api.post('/api/vault/unlock', { masterPassword: el('vaultUnlockPassword').value }),
            'Could not unlock the vault');
        if (!status) return;

        el('vaultUnlockForm').reset();
        currentState = null;
        await refreshStatus(true);
    }

    async function lockVault() {
        const status = await Api.guard(Api.post('/api/vault/lock', {}), 'Could not lock the vault');
        if (!status) return;
        currentState = null;
        hideBanner();
        await refreshStatus(true);
        showBanner('Vault locked - the data key was removed from memory.', 'info');
    }

    // ---------------------------------------------------------------- wiring
    el('vaultSetupForm').addEventListener('submit', createVault);
    el('vaultUnlockForm').addEventListener('submit', unlockVault);
    el('vaultForm').addEventListener('submit', saveCredential);
    el('vaultVerifyForm').addEventListener('submit', verifyMasterPassword);
    el('vaultAddBtn').addEventListener('click', () => openForm(null));
    el('vaultLockBtn').addEventListener('click', lockVault);
    el('vaultHideNow').addEventListener('click', hideRevealed);

    document.querySelectorAll('[data-close]').forEach(button => {
        button.addEventListener('click', closeAllModals);
    });

    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', event => {
            if (event.target === overlay) closeAllModals();
        });
    });

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') closeAllModals();
    });

    // CRED_V1 session management: notice the 10 minute auto-lock even when the
    // page is simply left open. The poll does not count as activity.
    setInterval(() => refreshStatus(false), STATUS_POLL_MILLIS);

    refreshStatus(true);
});
