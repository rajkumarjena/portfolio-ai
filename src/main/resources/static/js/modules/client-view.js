let allClients = [];
let openDetailsPan = null; // PAN of the row whose details panel is expanded

async function loadClients() {
    try {
        const response = await fetch('/api/clients');
        if (!response.ok) throw new Error('Failed to fetch clients');
        allClients = await response.json();
        renderTable(allClients);
    } catch (error) {
        alert('Error loading clients: ' + error);
    }
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;');
}

function renderTable(data) {
    const tbody = document.querySelector('#clientTable tbody');
    tbody.innerHTML = '';

    if (!data.length) {
        tbody.innerHTML = '<tr><td colspan="7">No clients found. Use "Add Client" to create one.</td></tr>';
        return;
    }

    data.forEach(client => {
        const mobiles = (client.mobileNumbers || [])
            .map(m => `<span class="chip">${escapeHtml(m.mobileNumber)}</span>`).join('') || '<span class="chip">None</span>';
        const emails = (client.emails || [])
            .map(e => `<span class="chip">${escapeHtml(e.email)}</span>`).join('') || '<span class="chip">None</span>';
        const banks = (client.bankAccounts || [])
            .map(b => `<span class="chip">${escapeHtml(b.bankName)} - ${escapeHtml(b.accountNumber)}</span>`).join('') || '<span class="chip">None</span>';
        // BONDBP_V5 #4: family members are other registered clients - shown as name (PAN)
        const members = (client.familyMembers || [])
            .filter(fm => fm.member)
            .map(fm => `<span class="chip" title="Family member">${escapeHtml(fm.member.name)} (${escapeHtml(fm.member.pan)})</span>`).join('') || '<span class="chip">None</span>';

        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${escapeHtml(client.name)}<span class="immutable">(immutable)</span></td>
            <td>${escapeHtml(client.pan)}<span class="immutable">(immutable)</span></td>
            <td>${mobiles}</td>
            <td>${emails}</td>
            <td>${banks}</td>
            <td>${members}</td>
            <td>
                <button class="edit-btn" onclick="toggleDetails('${escapeHtml(client.pan)}')">
                    ${openDetailsPan === client.pan ? 'Close' : 'Details / Add'}
                </button>
                <button class="edit-btn" onclick="openConfig('${escapeHtml(client.pan)}')">Open in Config</button>
            </td>
        `;
        tbody.appendChild(row);

        if (openDetailsPan === client.pan) {
            tbody.appendChild(buildDetailsRow(client));
        }
    });
}

// Inline panel for adding new mobiles / emails / bank accounts (no delete, no name/PAN edit)
function buildDetailsRow(client) {
    const tr = document.createElement('tr');
    tr.className = 'details-row';
    const td = document.createElement('td');
    td.colSpan = 7;
    td.innerHTML = `
        <p style="margin:4px 0 10px; font-weight:600;">
            ${escapeHtml(client.name)} &mdash; ${escapeHtml(client.pan)}
            <span class="immutable">Name and PAN cannot be changed or deleted.</span>
        </p>
        <div class="add-panel">
            <div class="add-group">
                <h4>Add Mobile Number</h4>
                <input type="tel" id="addMobile-${client.id}" maxlength="10" placeholder="10-digit mobile">
                <button onclick="addItem(${client.id}, 'mobile')">Add Mobile</button>
            </div>
            <div class="add-group">
                <h4>Add Email Address</h4>
                <input type="email" id="addEmail-${client.id}" placeholder="email@example.com">
                <button onclick="addItem(${client.id}, 'email')">Add Email</button>
            </div>
            <div class="add-group">
                <h4>Add Bank Account</h4>
                <input type="text" id="addBankName-${client.id}" placeholder="Bank Name">
                <input type="text" id="addAccountNumber-${client.id}" placeholder="Account Number">
                <input type="text" id="addIfsc-${client.id}" placeholder="IFSC">
                <textarea id="addBankAddress-${client.id}" placeholder="Bank Address"></textarea>
                <button onclick="addItem(${client.id}, 'bank')">Add Bank Account</button>
            </div>
            <div class="add-group fam-group">
                <h4>Add Family Member</h4>
                <p class="fam-hint">A family member must be an existing registered client.
                    Type at least 3 characters of a name or PAN to search.</p>
                <div class="fam-associated">
                    <span class="fam-label">Associated:</span>${associatedMembers(client)}
                </div>
                <input type="text" id="famSearch-${client.id}" placeholder="Search by name or PAN..." autocomplete="off">
                <div class="fam-results" id="famResults-${client.id}"></div>
                <div class="fam-display" id="famDisplay-${client.id}">
                    <label><span>Name</span><input type="text" id="famName-${client.id}" readonly></label>
                    <label><span>PAN</span><input type="text" id="famPan-${client.id}" readonly></label>
                    <label><span>Emails</span><input type="text" id="famEmails-${client.id}" readonly></label>
                    <label><span>Mobiles</span><input type="text" id="famMobiles-${client.id}" readonly></label>
                </div>
                <div class="fam-actions">
                    <button id="famAddBtn-${client.id}" onclick="famAdd(${client.id})" disabled>Add</button>
                    <button id="famCancelBtn-${client.id}" onclick="famCancel(${client.id})" disabled>Cancel</button>
                    <button id="famDeleteBtn-${client.id}" onclick="famDelete(${client.id})" disabled>Delete</button>
                </div>
            </div>
        </div>
    `;
    tr.appendChild(td);
    return tr;
}

// Associated family members as clickable chips - selecting one loads its
// read-only details so the user can de-associate it with the Delete button.
function associatedMembers(client) {
    const links = (client.familyMembers || []).filter(fm => fm.member);
    if (!links.length) return '<span class="fam-hint">none yet</span>';
    return links.map(fm =>
        `<button type="button" class="chip fam-chip" title="Select to de-associate"`
        + ` data-fam-client="${client.id}" data-fam-member="${fm.member.id}">`
        + `${escapeHtml(fm.member.name)} (${escapeHtml(fm.member.pan)})</button>`).join('');
}

function toggleDetails(pan) {
    openDetailsPan = openDetailsPan === pan ? null : pan;
    renderTable(currentFiltered());
}

function openConfig(pan) {
    window.location.href = '/app-client-config.html?pan=' + encodeURIComponent(pan);
}

async function addItem(clientId, type) {
    const client = allClients.find(c => c.id === clientId);
    if (!client) return;

    const payload = {
        name: client.name,   // sent for completeness - immutable server-side
        pan: client.pan,
        mobileNumbers: [],
        emails: [],
        bankAccounts: []
    };

    if (type === 'mobile') {
        const value = document.getElementById(`addMobile-${clientId}`).value.trim();
        if (!/^[0-9]{10}$/.test(value)) { alert('Enter a valid 10-digit mobile number'); return; }
        payload.mobileNumbers.push({ mobileNumber: value });
    } else if (type === 'email') {
        const value = document.getElementById(`addEmail-${clientId}`).value.trim();
        if (!value) { alert('Enter an email address'); return; }
        payload.emails.push({ email: value });
    } else {
        const bankName = document.getElementById(`addBankName-${clientId}`).value.trim();
        const accountNumber = document.getElementById(`addAccountNumber-${clientId}`).value.trim();
        const ifsc = document.getElementById(`addIfsc-${clientId}`).value.trim();
        const bankAddress = document.getElementById(`addBankAddress-${clientId}`).value.trim();
        if (!bankName || !accountNumber || !ifsc || !bankAddress) {
            alert('Please fill all bank fields: Bank Name, Account Number, IFSC and Bank Address');
            return;
        }
        payload.bankAccounts.push({ bankName, accountNumber, ifsc, bankAddress });
    }

    try {
        const response = await fetch('/api/clients/' + clientId, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (response.ok) {
            await loadClients(); // keep the details panel open for the same PAN
        } else {
            // Server-side validation messages (family member rules, etc.) are shown as-is
            let detail = '';
            try {
                const body = await response.json();
                detail = body && body.message ? ': ' + body.message : '';
            } catch (e) { /* no JSON body */ }
            alert('Failed to add details (HTTP ' + response.status + ')' + detail);
        }
    } catch (error) {
        alert('Network error: ' + error);
    }
}

function applySearch() {
    renderTable(currentFiltered());
}

function currentFiltered() {
    const term = document.getElementById('searchInput').value.trim().toLowerCase();
    if (!term) return allClients;
    return allClients.filter(c =>
        (c.name || '').toLowerCase().includes(term)
        || (c.pan || '').toLowerCase().includes(term)
        || (c.bankAccounts || []).some(b =>
            (b.bankName || '').toLowerCase().includes(term)
            || (b.accountNumber || '').toLowerCase().includes(term))            || (c.familyMembers || []).some(fm =>
                ((fm.member && fm.member.name) || '').toLowerCase().includes(term)
                || ((fm.member && fm.member.pan) || '').toLowerCase().includes(term))
    );
}

// ---------------------------------------------------------------------------
// BONDBP_V5 #4: "Add Family Member" - the member is always an EXISTING
// registered client picked from a search drop-down:
//   4.b  the backend is hit only from 3 characters (name or PAN),
//   4.c  the selected client's details are shown in read-only fields,
//   4.d  Add confirms the association, Cancel clears the selection and lets
//        the user search again, Delete de-associates an earlier association.
// ---------------------------------------------------------------------------
const MIN_FAM_SEARCH = 3;
const famSearchResults = {}; // clientId -> last matches returned by /api/clients/search

// Search input - delegated because the panel is re-rendered on every loadClients
document.addEventListener('input', (e) => {
    const el = e.target;
    if (!el.id || !el.id.startsWith('famSearch-')) return;
    const clientId = Number(el.id.slice('famSearch-'.length));
    clearTimeout(el._famTimer);
    const q = el.value.trim();
    const results = document.getElementById('famResults-' + clientId);
    if (!results) return;
    if (q.length < MIN_FAM_SEARCH) {
        famSearchResults[clientId] = [];
        results.innerHTML = q.length
            ? '<p class="fam-hint">Keep typing - minimum 3 characters.</p>'
            : '';
        return;
    }
    el._famTimer = setTimeout(() => runFamSearch(el, clientId, q), 300);
});

async function runFamSearch(el, clientId, q) {
    const results = document.getElementById('famResults-' + clientId);
    if (!results || !el.isConnected || el.value.trim() !== q) return; // stale request
    try {
        const response = await fetch(`/api/clients/search?q=${encodeURIComponent(q)}&forClientId=${clientId}`);
        if (!response.ok) {
            let message = 'Search failed (HTTP ' + response.status + ')';
            try {
                const body = await response.json();
                if (body && body.message) message = body.message;
            } catch (ignored) { /* no JSON body */ }
            throw new Error(message);
        }
        const matches = await response.json();
        if (el.value.trim() !== q) return; // user kept typing meanwhile
        famSearchResults[clientId] = matches;
        results.innerHTML = matches.length
            ? matches.map((m, idx) =>
                `<button type="button" class="fam-result" data-fam-client="${clientId}" data-fam-result="${idx}">`
                + `${escapeHtml(m.name)} &mdash; ${escapeHtml(m.pan)}`
                + ((m.emails || []).length ? ` &middot; ${escapeHtml(m.emails[0])}` : '')
                + `</button>`).join('')
            : '<p class="fam-hint">No matching registered client found.</p>';
    } catch (error) {
        results.innerHTML = `<p class="fam-error">${escapeHtml(error.message)}</p>`;
    }
}

// Delegated clicks: pick a search result (new association) or an associated
// member chip (to de-associate)
document.addEventListener('click', (e) => {
    const target = e.target instanceof Element ? e.target : null;
    if (!target) return;
    const result = target.closest('[data-fam-result]');
    if (result) {
        const clientId = Number(result.dataset.famClient);
        const match = (famSearchResults[clientId] || [])[Number(result.dataset.famResult)];
        if (match) fillFamDisplay(clientId, match, 'new');
        return;
    }
    const chip = target.closest('[data-fam-member]');
    if (chip) {
        const clientId = Number(chip.dataset.famClient);
        const memberId = Number(chip.dataset.famMember);
        const client = allClients.find(c => c.id === clientId);
        const link = client && (client.familyMembers || [])
            .find(fm => fm.member && fm.member.id === memberId);
        if (link) fillFamDisplay(clientId, link.member, 'associated');
    }
});

// Search results carry plain strings; the member view inside client JSON
// carries {email: ...} objects - normalise either shape to strings.
function famStrings(value) {
    return (value || []).map(v => (typeof v === 'string' ? v : v && (v.email || v.mobileNumber)))
        .filter(Boolean);
}

function fillFamDisplay(clientId, member, mode) {
    document.getElementById('famName-' + clientId).value = member.name || '';
    document.getElementById('famPan-' + clientId).value = member.pan || '';
    document.getElementById('famEmails-' + clientId).value = famStrings(member.emails).join(', ');
    document.getElementById('famMobiles-' + clientId).value = famStrings(member.mobileNumbers).join(', ');
    const display = document.getElementById('famDisplay-' + clientId);
    display.dataset.memberId = member.id;
    display.dataset.mode = mode;
    document.getElementById('famAddBtn-' + clientId).disabled = mode !== 'new';
    document.getElementById('famDeleteBtn-' + clientId).disabled = mode !== 'associated';
    document.getElementById('famCancelBtn-' + clientId).disabled = false;
    document.getElementById('famResults-' + clientId).innerHTML = '';
}

function famCancel(clientId) {
    ['famName', 'famPan', 'famEmails', 'famMobiles'].forEach(id => {
        const field = document.getElementById(id + '-' + clientId);
        if (field) field.value = '';
    });
    const display = document.getElementById('famDisplay-' + clientId);
    if (display) { delete display.dataset.memberId; delete display.dataset.mode; }
    const search = document.getElementById('famSearch-' + clientId);
    if (search) search.value = '';
    const results = document.getElementById('famResults-' + clientId);
    if (results) results.innerHTML = '';
    famSearchResults[clientId] = [];
    ['famAddBtn', 'famCancelBtn', 'famDeleteBtn'].forEach(id => {
        const btn = document.getElementById(id + '-' + clientId);
        if (btn) btn.disabled = true;
    });
}

async function famAdd(clientId) {
    const display = document.getElementById('famDisplay-' + clientId);
    const memberId = display && display.dataset.memberId;
    if (!memberId) { alert('Search and select a registered client first'); return; }
    try {
        const response = await fetch(`/api/clients/${clientId}/family-members`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ memberId: Number(memberId) })
        });
        if (response.ok) {
            famSearchResults[clientId] = [];
            await loadClients(); // panel stays open (openDetailsPan), fresh state
        } else {
            let detail = '';
            try {
                const body = await response.json();
                detail = body && body.message ? ': ' + body.message : '';
            } catch (ignored) { /* no JSON body */ }
            alert('Failed to add family member (HTTP ' + response.status + ')' + detail);
        }
    } catch (error) {
        alert('Network error: ' + error);
    }
}

async function famDelete(clientId) {
    const display = document.getElementById('famDisplay-' + clientId);
    const memberId = display && display.dataset.memberId;
    if (!memberId) return;
    try {
        const response = await fetch(`/api/clients/${clientId}/family-members/${memberId}`, {
            method: 'DELETE'
        });
        if (response.ok) {
            famSearchResults[clientId] = [];
            await loadClients();
        } else {
            let detail = '';
            try {
                const body = await response.json();
                detail = body && body.message ? ': ' + body.message : '';
            } catch (ignored) { /* no JSON body */ }
            alert('Failed to remove family member (HTTP ' + response.status + ')' + detail);
        }
    } catch (error) {
        alert('Network error: ' + error);
    }
}

document.addEventListener('DOMContentLoaded', loadClients);
