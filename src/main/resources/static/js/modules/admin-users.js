// admin-users-script.js - lists users and lets an ADMIN change any other
// user's role and (BONDBP_V10 #4) client account status
let currentPan = null;

function feedback(kind, text) {
    const el = document.getElementById('feedback');
    el.textContent = text;
    el.className = kind;
}

async function loadMe() {
    try {
        const res = await fetch('/api/auth/me');
        if (res.ok) {
            const me = await res.json();
            currentPan = me.pan;
            if (me.role !== 'ADMIN') {
                // Server blocks these calls anyway; fail fast for a clearer message
                feedback('fail', 'You need the ADMIN role to manage client roles.');
            }
        }
    } catch (e) {
        console.error('me error', e);
    }
}

async function loadUsers() {
    const tbody = document.querySelector('#userTable tbody');
    tbody.innerHTML = '<tr><td colspan="8">Loading...</td></tr>';

    try {
        const res = await fetch('/api/admin/users');
        if (!res.ok) {
            tbody.innerHTML = '<tr><td colspan="8">Failed to load clients (HTTP '
                + res.status + ')</td></tr>';
            return;
        }
        const users = await res.json();
        tbody.innerHTML = '';

        users.forEach(user => {
            const tr = document.createElement('tr');
            const isSelf = currentPan && user.pan === currentPan;
            const emails = (user.emails || [])
                .map(e => '<span class="chip">' + escapeHtml(e) + '</span>').join('') || '-';
            // BONDBP_V10 #4: account status chip (ACTIVE/HOLD/CANCELLED).
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: client account status chip (ACTIVE/HOLD/CANCELLED).
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: account status chip + the status <select> bound
            // to its current value (default ACTIVE for bare user accounts).
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            const statusOptions = ['ACTIVE', 'HOLD', 'CANCELLED']
                .map(s => '<option value="' + s + '"' + (s === status ? ' selected' : '') + '>'
                    + s + '</option>').join('');
            // BONDBP_V10 #4: account status chip + editable status select.
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: client account status (ACTIVE/HOLD/CANCELLED).
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: client account status chip (ACTIVE/HOLD/CANCELLED).
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: account status chip (ACTIVE/HOLD/CANCELLED).
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: operative account status (ACTIVE/HOLD/CANCELLED)
            // shown beside the lock badge - they are independent flags.
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: the client's account status (ACTIVE/HOLD/CANCELLED)
            // sits beside the lock badge - they are independent flags.
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: account status chip (ACTIVE/HOLD/CANCELLED).
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: operative account status (ACTIVE/HOLD/CANCELLED).
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            const statusOptions = ['ACTIVE', 'HOLD', 'CANCELLED']
                .map(s => '<option value="' + s + '"' + (s === status ? ' selected' : '') + '>'
                    + s + '</option>').join('');
            // BONDBP_V10 #4: operational status chip alongside the lock badge.
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: account status (ACTIVE/HOLD/CANCELLED) chip.
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';
            // BONDBP_V10 #4: account status chip (ACTIVE/HOLD/CANCELLED).
            const status = user.accountStatus || 'ACTIVE';
            const statusChip = '<span class="chip status-' + escapeHtml(status.toLowerCase()) + '">'
                + escapeHtml(status) + '</span>';

            const roleOptions = ['USER', 'SUPERVISOR', 'ADMIN']
                .map(r => '<option value="' + r + '"' + (r === user.role ? ' selected' : '') + '>'
                    + r + '</option>').join('');

            tr.innerHTML =
                '<td style="text-align:left;">' + escapeHtml(user.name) + '</td>'
                + '<td>' + escapeHtml(user.pan) + '</td>'
                + '<td style="text-align:left;">' + emails + '</td>'
                + '<td><span class="chip role-chip role-' + escapeHtml(user.role) + '">'
                + escapeHtml(user.role) + '</span></td>'
                + '<td>' + statusChip
                + (user.locked ? ' <span class="locked-badge">LOCKED</span>' : '') + '</td>'
                + '<td>' + user.failedAttempts + '</td>'
                + '<td><select id="role-' + user.id + '">' + roleOptions + '</select></td>'
                + '<td class="status-actions">'
                + '<select id="status-' + user.id + '">'
                + ['ACTIVE', 'HOLD', 'CANCELLED']
                    .map(s => '<option value="' + s + '"' + (s === status ? ' selected' : '') + '>'
                        + s + '</option>').join('')
                + '</select>'
                + '<button id="save-' + user.id + '"'
                + (isSelf ? ' disabled title="You cannot change your own role"' : '')
                + '>Update Role</button>'
                + '<button id="statusUpdate-' + user.id + '"'
                + (isSelf ? ' disabled title="You cannot change your own account status"' : '')
                + '>Update Status</button></td>';

            tbody.appendChild(tr);

            const button = document.getElementById('save-' + user.id);
            button.addEventListener('click', () => updateRole(user, isSelf));
            const statusButton = document.getElementById('statusUpdate-' + user.id);
            statusButton.addEventListener('click', () => updateStatus(user, isSelf));
        });
    } catch (e) {
        console.error('loadUsers error', e);
        tbody.innerHTML = '<tr><td colspan="8">Network error while loading clients</td></tr>';
    }
}

async function updateRole(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own role.');
        return;
    }
    const newRole = document.getElementById('role-' + user.id).value;
    if (newRole === user.role) {
        feedback('', '');
        return;
    }

    try {
        const res = await fetch('/api/admin/users/' + user.id + '/role', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ role: newRole })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Updated ' + user.name + ' (' + user.pan + ') to ' + newRole + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateRole error', e);
        feedback('fail', 'Network error while updating the role.');
    }
}

// BONDBP_V10 #4: set a client account's status (ACTIVE / HOLD / CANCELLED).
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED cannot be undone. Cancel ' + user.name + ' (' + user.pan + ')?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: set a client account's status (ACTIVE / HOLD / CANCELLED).
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED cannot be undone. Cancel ' + user.name + ' (' + user.pan + ')?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: flip a client account between ACTIVE / HOLD / CANCELLED.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED is permanent. Set ' + user.name + ' (' + user.pan
            + ') to CANCELLED?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Updated ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: flip a client account between ACTIVE / HOLD / CANCELLED.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED cannot be undone. Set ' + user.name + ' (' + user.pan + ') to CANCELLED?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: switch a client account between ACTIVE / HOLD / CANCELLED.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED is permanent. Set ' + user.name + ' (' + user.pan
            + ') to CANCELLED?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: switch a client account between ACTIVE / HOLD / CANCELLED.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED cannot be undone. Set ' + user.name + ' (' + user.pan
            + ') to CANCELLED?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: switch a client account between ACTIVE / HOLD / CANCELLED.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED cannot be undone. Set ' + user.name + ' (' + user.pan
            + ') to CANCELLED?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: flip a client account between ACTIVE / HOLD / CANCELLED.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED is permanent. Set ' + user.name + ' (' + user.pan
            + ') to CANCELLED?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: flip a client account between ACTIVE / HOLD / CANCELLED.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED is permanent. Set ' + user.name + ' (' + user.pan
            + ') to CANCELLED?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: flip a client account between ACTIVE / HOLD / CANCELLED.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('acct-' + user.id).value;
    if (status === (user.accountStatus || 'ACTIVE')) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED cannot be undone. Set ' + user.name + ' (' + user.pan
            + ') to CANCELLED?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: ACTIVE / HOLD / CANCELLED for a client account. HOLD blocks
// login; CANCELLED is permanent.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCELLED cannot be undone. Set ' + user.name + ' (' + user.pan
            + ') to CANCELLED?')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: switch a client account between ACTIVE / HOLD / CANCELLED.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('acct-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCEL the account for ' + user.name + ' (' + user.pan
            + ')? This cannot be undone.')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Account status of ' + user.name + ' (' + user.pan + ') set to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: switch a client account between ACTIVE / HOLD / CANCELLED.
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('acct-' + user.id).value;
    const current = user.accountStatus || 'ACTIVE';
    if (status === current) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCEL the account for ' + user.name + ' (' + user.pan
            + ')? This cannot be undone.')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Account status of ' + user.name + ' (' + user.pan + ') set to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

// BONDBP_V10 #4: flip a client's account status (ACTIVE/HOLD/CANCELLED).
async function updateStatus(user, isSelf) {
    if (isSelf) {
        feedback('fail', 'You cannot change your own account status.');
        return;
    }
    const status = document.getElementById('status-' + user.id).value;
    if (status === (user.accountStatus || 'ACTIVE')) {
        feedback('', '');
        return;
    }
    if (status === 'CANCELLED'
        && !confirm('CANCEL the account for ' + user.name + '? This cannot be undone.')) {
        return;
    }
    try {
        const res = await fetch('/api/admin/users/' + user.id + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            feedback('ok', 'Set ' + user.name + ' (' + user.pan + ') account status to ' + status + '.');
            loadUsers();
        } else {
            feedback('fail', data.message || 'Update failed (HTTP ' + res.status + ')');
        }
    } catch (e) {
        console.error('updateStatus error', e);
        feedback('fail', 'Network error while updating the account status.');
    }
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;');
}

document.addEventListener('DOMContentLoaded', async () => {
    await loadMe();
    await loadUsers();
});
