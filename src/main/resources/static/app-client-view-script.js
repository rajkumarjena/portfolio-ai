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
        tbody.innerHTML = '<tr><td colspan="6">No clients found. Use "Add Client" to create one.</td></tr>';
        return;
    }

    data.forEach(client => {
        const mobiles = (client.mobileNumbers || [])
            .map(m => `<span class="chip">${escapeHtml(m.mobileNumber)}</span>`).join('') || '<span class="chip">None</span>';
        const emails = (client.emails || [])
            .map(e => `<span class="chip">${escapeHtml(e.email)}</span>`).join('') || '<span class="chip">None</span>';
        const banks = (client.bankAccounts || [])
            .map(b => `<span class="chip">${escapeHtml(b.bankName)} - ${escapeHtml(b.accountNumber)}</span>`).join('') || '<span class="chip">None</span>';

        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${escapeHtml(client.name)}<span class="immutable">(immutable)</span></td>
            <td>${escapeHtml(client.pan)}<span class="immutable">(immutable)</span></td>
            <td>${mobiles}</td>
            <td>${emails}</td>
            <td>${banks}</td>
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
    td.colSpan = 6;
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
        </div>
    `;
    tr.appendChild(td);
    return tr;
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
            alert('Failed to add details (HTTP ' + response.status + ')');
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
            || (b.accountNumber || '').toLowerCase().includes(term))
    );
}

document.addEventListener('DOMContentLoaded', loadClients);
