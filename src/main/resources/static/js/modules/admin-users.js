// admin-users-script.js - lists users and lets an ADMIN change any other user's role
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
                feedback('fail', 'You need the ADMIN role to manage user roles.');
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
            tbody.innerHTML = '<tr><td colspan="8">Failed to load users (HTTP '
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

            const roleOptions = ['USER', 'SUPERVISOR', 'ADMIN']
                .map(r => '<option value="' + r + '"' + (r === user.role ? ' selected' : '') + '>'
                    + r + '</option>').join('');

            tr.innerHTML =
                '<td style="text-align:left;">' + escapeHtml(user.name) + '</td>'
                + '<td>' + escapeHtml(user.pan) + '</td>'
                + '<td style="text-align:left;">' + emails + '</td>'
                + '<td><span class="chip role-chip role-' + escapeHtml(user.role) + '">'
                + escapeHtml(user.role) + '</span></td>'
                + '<td>' + (user.locked ? '<span class="locked-badge">LOCKED</span>' : 'Active') + '</td>'
                + '<td>' + user.failedAttempts + '</td>'
                + '<td><select id="role-' + user.id + '">' + roleOptions + '</select></td>'
                + '<td><button id="save-' + user.id + '"'
                + (isSelf ? ' disabled title="You cannot change your own role"' : '')
                + '>Update Role</button></td>';

            tbody.appendChild(tr);

            const button = document.getElementById('save-' + user.id);
            button.addEventListener('click', () => updateRole(user, isSelf));
        });
    } catch (e) {
        console.error('loadUsers error', e);
        tbody.innerHTML = '<tr><td colspan="8">Network error while loading users</td></tr>';
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
