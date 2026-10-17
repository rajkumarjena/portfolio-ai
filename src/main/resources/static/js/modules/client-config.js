function navigateAway(url) {
    window.location.href = url;
}

const form = document.getElementById('clientForm');
const panInput = document.getElementById('clientPan');
const nameInput = document.getElementById('clientName');

// null = create mode; a Client object = append mode (PAN already exists)
let existingClient = null;

const banner = document.getElementById('modeBanner');
const existingPanel = document.getElementById('existingPanel');
const submitBtn = document.getElementById('submitBtn');

// ------------------------------------------------------------------
// PAN lookup: PAN is the business identifier of a client
// ------------------------------------------------------------------
async function lookupPan() {
    const pan = panInput.value.trim().toUpperCase();
    if (!pan) {
        enterCreateMode();
        return;
    }

    try {
        const res = await fetch('/api/clients/pan/' + encodeURIComponent(pan));
        if (res.status === 404) {
            enterCreateMode();
            return;
        }
        if (!res.ok) throw new Error('Lookup failed');

        const client = await res.json();
        enterAppendMode(client);
    } catch (err) {
        console.error('PAN lookup error:', err);
    }
}

function enterCreateMode() {
    existingClient = null;
    nameInput.readOnly = false;
    panInput.readOnly = false;
    banner.style.display = 'block';
    banner.className = 'status-banner new-client';
    banner.textContent = 'New client - PAN not registered yet.';
    existingPanel.style.display = 'none';
    submitBtn.textContent = 'Save Client';
}

function enterAppendMode(client) {
    existingClient = client;

    // Name and PAN are immutable - lock them (CLIENT.md rule)
    nameInput.value = client.name;
    nameInput.readOnly = true;
    panInput.value = client.pan;
    panInput.readOnly = true;

    banner.style.display = 'block';
    banner.className = 'status-banner';
    banner.textContent = `Existing client found: ${client.name} (${client.pan}). ` +
        'Name and PAN are locked. You may add new mobile numbers, emails and bank accounts.';

    renderExisting(client);
    existingPanel.style.display = 'block';
    submitBtn.textContent = 'Add to Client';
}

function renderExisting(client) {
    const mobiles = document.getElementById('existingMobiles');
    const emails = document.getElementById('existingEmails');
    const banks = document.getElementById('existingBanks');

    mobiles.innerHTML = client.mobileNumbers && client.mobileNumbers.length
        ? client.mobileNumbers.map(m => `<li>${m.mobileNumber}</li>`).join('')
        : '<li>None yet</li>';
    emails.innerHTML = client.emails && client.emails.length
        ? client.emails.map(e => `<li>${e.email}</li>`).join('')
        : '<li>None yet</li>';
    banks.innerHTML = client.bankAccounts && client.bankAccounts.length
        ? client.bankAccounts.map(b => `<li>${b.bankName} - ${b.accountNumber} (${b.ifsc})</li>`).join('')
        : '<li>None yet</li>';
}

panInput.addEventListener('blur', lookupPan);

// Deep link: app-client-config.html?pan=XXXXX
const urlParams = new URLSearchParams(window.location.search);
const initialPan = urlParams.get('pan');
if (initialPan) {
    panInput.value = initialPan;
    lookupPan();
} else {
    enterCreateMode();
}

// ------------------------------------------------------------------
// Submit: POST when creating, PUT (append-only) when PAN exists
// ------------------------------------------------------------------
form.addEventListener('submit', async (event) => {
    event.preventDefault();

    const mobile = document.getElementById('mobileNumber').value.trim();
    const email = document.getElementById('email').value.trim();
    const bankName = document.getElementById('bankName').value.trim();
    const accountNumber = document.getElementById('accountNumber').value.trim();
    const ifsc = document.getElementById('ifsc').value.trim();
    const bankAddress = document.getElementById('bankAddress').value.trim();

    const hasBank = bankName || accountNumber || ifsc || bankAddress;
    if (hasBank && (!bankName || !accountNumber || !ifsc || !bankAddress)) {
        alert('Please fill all bank fields (Bank Name, Account Number, IFSC, Bank Address) or none.');
        return;
    }

    let payload;
    let url;
    let method;

    if (existingClient) {
        // Append mode: only NEW items are sent; Name/PAN stay untouched server-side
        if (!mobile && !email && !hasBank) {
            alert('Enter at least one new mobile number, email or bank account to add.');
            return;
        }
        payload = {
            name: existingClient.name,
            pan: existingClient.pan,
            mobileNumbers: mobile ? [{ mobileNumber: mobile }] : [],
            emails: email ? [{ email: email }] : [],
            bankAccounts: hasBank ? [{ bankName, accountNumber, ifsc, bankAddress }] : []
        };
        url = '/api/clients/' + existingClient.id;
        method = 'PUT';
    } else {
        // Create mode: Name, PAN, mobile and email are required
        if (!mobile) { alert('Mobile number is required for a new client.'); return; }
        if (!email) { alert('Email is required for a new client.'); return; }
        if (!hasBank) { alert('Bank details are required for a new client.'); return; }

        payload = {
            name: nameInput.value.trim(),
            pan: panInput.value.trim().toUpperCase(),
            mobileNumbers: [{ mobileNumber: mobile }],
            emails: [{ email: email }],
            bankAccounts: [{ bankName, accountNumber, ifsc, bankAddress }]
        };
        url = '/api/clients';
        method = 'POST';
    }

    try {
        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            const saved = await response.json();
            alert(existingClient
                ? 'Details added to client successfully!'
                : 'Client saved successfully!');
            window.location.href = '/app-client-view.html';
        } else if (response.status === 409) {
            alert('A client with this PAN already exists. Reload the page to switch to add mode.');
        } else if (response.status === 400) {
            const err = await response.json().catch(() => ({}));
            alert('Validation error: ' + (err.message || 'please check the entered values'));
        } else {
            alert('Failed to save client (HTTP ' + response.status + ')');
        }
    } catch (error) {
        console.error('Error saving client:', error);
        alert('A network error occurred. Please try again.');
    }
});
