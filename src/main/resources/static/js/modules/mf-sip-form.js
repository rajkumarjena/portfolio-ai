// mf-sip-form-script.js - SIP / investment entry (MF.md 1-6, 11, 12)
const urlParams = new URLSearchParams(window.location.search);
const sipId = urlParams.get('id');

const form = document.getElementById('sipForm');
let schemes = [];          // registered scheme master (for AMC/category autofill)
let banks = [];            // client bank accounts (MF.md 3)
let current = null;        // header being edited

// ---------------------------------------------------------------- helpers
function esc(s) {
    return String(s == null ? '' : s)
        .replace(/&/g, '\u0026amp;')
        .replace(/</g, '\u0026lt;')
        .replace(/>/g, '\u0026gt;')
        .replace(/"/g, '\u0026quot;')
        .replace(/'/g, '\u0026#39;');
}

async function getJson(url, init) {
    const res = await fetch(url, init);
    const text = await res.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch (e) { body = text; }
    if (!res.ok) {
        const msg = (body && body.message) ? body.message : text;
        throw new Error(msg || ('HTTP ' + res.status));
    }
    return body;
}

// ------------------------------------------------------- scheme auto-fill
function loadSchemes() {
    return getJson('/api/mf/schemes').then(list => {
        schemes = list || [];
        const dl = document.getElementById('schemeList');
        dl.innerHTML = schemes.map(s => `<option value="${esc(s.schemeName)}">`).join('');
        const amcs = [...new Set(schemes.map(s => s.amcName).filter(Boolean))];
        const cats = [...new Set(schemes.map(s => s.category).filter(Boolean))];
        document.getElementById('amcList').innerHTML =
            amcs.map(a => `<option value="${esc(a)}">`).join('');
        document.getElementById('categoryList').innerHTML =
            cats.map(c => `<option value="${esc(c)}">`).join('');
    });
}

function autofillScheme() {
    const name = document.getElementById('schemeName').value.trim();
    const matches = schemes.filter(s => s.schemeName.toLowerCase() === name.toLowerCase());
    if (matches.length >= 1) {
        const amc = document.getElementById('amcName');
        const cat = document.getElementById('category');
        if (!amc.value) amc.value = matches[0].amcName || '';
        if (!cat.value) cat.value = matches[0].category || '';
        if (matches.length > 1 && !amc.value) {
            // same scheme name under several AMCs - user picks the AMC himself
            console.log('Multiple AMCs share this scheme name; select the AMC manually');
        }
    }
}

// ------------------------------------------------------------ bank dropdown
function loadBanks() {
    return getJson('/api/mf/banks').then(list => {
        banks = list || [];
        const sel = document.getElementById('bankSelect');
        const desired = (current && current.bankAccountNumber) || sel.value;
        sel.innerHTML = '';
        if (!banks.length) {
            sel.innerHTML = '<option value="">(add a bank account on the client screen)</option>';
            return;
        }
        banks.forEach(b => {
            const opt = document.createElement('option');
            opt.value = b.accountNumber;
            opt.textContent = `${b.bankName} - ${b.accountNumber}`;
            sel.appendChild(opt);
        });
        if (desired) sel.value = desired;
        syncBankName();
    });
}

function syncBankName() {
    const acct = document.getElementById('bankSelect').value;
    const b = banks.find(x => String(x.accountNumber) === String(acct));
    document.getElementById('bankName').value = b ? b.bankName : '';
}

// ---------------------------------------------------------- schedule preview
function toggleSipFields() {
    const sip = document.getElementById('registrationType').value === 'SIP';
    document.getElementById('sipFields').style.display = sip ? '' : 'none';
    updatePreview();
}

function nextDates(startStr, type, day, count) {
    if (!startStr) return [];
    const out = [];
    const start = new Date(startStr + 'T00:00:00');
    for (let i = 0; i < count; i++) {
        let d = new Date(start);
        const n = i;
        if (type === 'WEEKLY') d.setDate(start.getDate() + 7 * n);
        else if (type === 'MONTHLY') d.setMonth(start.getMonth() + n);
        else if (type === 'QUARTERLY') d.setMonth(start.getMonth() + 3 * n);
        else if (type === 'HALF_YEARLY') d.setMonth(start.getMonth() + 6 * n);
        else if (type === 'YEARLY') d.setFullYear(start.getFullYear() + n);
        // keep the requested SIP day of month when it exists
        if (type !== 'WEEKLY' && day >= 1 && day <= 31) {
            const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
            d.setDate(Math.min(day, last));
        }
        out.push(d);
    }
    return out;
}

function updatePreview() {
    const type = document.getElementById('registrationType').value;
    if (type !== 'SIP') {
        document.getElementById('datePreview').innerHTML = '<li class="muted">One time purchase - no schedule</li>';
        document.getElementById('unitsPreview').value = '-';
        return;
    }
    const start = document.getElementById('sipStartDate').value;
    const sipType = document.getElementById('sipType').value;
    const day = parseInt(document.getElementById('sipDate').value, 10) || 1;
    const amount = parseFloat(document.getElementById('sipAmount').value) || 0;

    const dates = nextDates(start, sipType, day, 6);
    document.getElementById('datePreview').innerHTML = dates.length
        ? dates.map(d => `<li>${d.toISOString().slice(0, 10)}</li>`).join('')
        : '<li class="muted">Choose a start date to preview</li>';

    const nav = current && current.nav > 0 ? current.nav : 0;
    document.getElementById('unitsPreview').value = (amount > 0 && nav > 0)
        ? (amount / nav).toFixed(4) + ' units @ NAV ' + nav
        : (amount > 0 ? '₹' + amount.toFixed(2) + ' per instalment' : '-');
}

// ------------------------------------------------------------- bank wiring
document.getElementById('bankSelect').addEventListener('change', syncBankName);
document.getElementById('schemeName').addEventListener('change', autofillScheme);
['registrationType', 'sipType', 'sipDate', 'sipStartDate', 'sipAmount'].forEach(id => {
    document.getElementById(id).addEventListener('input', updatePreview);
    document.getElementById(id).addEventListener('change', updatePreview);
});
document.getElementById('registrationType').addEventListener('change', toggleSipFields);

// ------------------------------------------------------------------ submit
form.addEventListener('submit', function (e) {
    e.preventDefault();
    const fd = new FormData(form);

    const regType = fd.get('registrationType');
    const payload = {
        scheme: {
            schemeName: (fd.get('schemeName') || '').trim(),
            amcName: (fd.get('amcName') || '').trim(),
            category: (fd.get('category') || '').trim()
        },
        folioNo: (fd.get('folioNo') || '').trim(),
        registrationType: regType,
        sipType: regType === 'SIP' ? fd.get('sipType') : null,
        sipAmount: regType === 'SIP' ? parseFloat(fd.get('sipAmount')) || 0 : 0,
        sipDate: regType === 'SIP' ? parseInt(fd.get('sipDate'), 10) || 0 : 0,
        sipStartDate: regType === 'SIP' ? fd.get('sipStartDate') : null,
        sipEndDate: regType === 'SIP' ? fd.get('sipEndDate') : null,
        bankName: fd.get('bankName'),
        bankAccountNumber: fd.get('bankAccountNumber'),
        status: fd.get('status'),
        notes: fd.get('notes')
    };
    if (current) payload.nav = current.nav || 0;
    if (!payload.scheme.schemeName) { alert('Scheme name is required'); return; }

    getJson(sipId ? '/api/mf/sips/' + sipId : '/api/mf/sips', {
        method: sipId ? 'PUT' : 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(payload)
    }).then(() => {
        alert('Mutual fund saved successfully!');
        window.location.href = '/mutualfunds/mf-view.html';
    }).catch(err => alert('Error saving mutual fund: ' + err.message));
});

// ------------------------------------------------------------- edit mode
if (sipId) {
    getJson('/api/mf/sips/' + sipId)
        .then(h => {
            current = h;
            document.getElementById('schemeName').value = h.scheme ? h.scheme.schemeName : '';
            document.getElementById('amcName').value = h.scheme ? (h.scheme.amcName || '') : '';
            document.getElementById('category').value = h.scheme ? (h.scheme.category || '') : '';
            document.getElementById('folioNo').value = h.folioNo || '';
            document.getElementById('registrationType').value = h.registrationType || 'SIP';
            document.getElementById('sipType').value = h.sipType || 'MONTHLY';
            document.getElementById('sipAmount').value = h.sipAmount || '';
            document.getElementById('sipDate').value = h.sipDate || 1;
            document.getElementById('sipStartDate').value = h.sipStartDate || '';
            document.getElementById('sipEndDate').value = h.sipEndDate || '';
            document.getElementById('status').value = h.status || 'ACTIVE';
            document.querySelector('[name=notes]').value = h.notes || '';
            document.getElementById('saveBtn').textContent = 'Update Mutual Fund';

            const banner = document.getElementById('statusBanner');
            banner.style.display = 'flex';
            banner.className = 'status-banner banner-' +
                (h.status === 'ACTIVE' ? 'GREEN' : h.status === 'PAUSED' ? 'AMBER' : 'RED');
            banner.innerHTML = `Status: ${esc(h.status)}` +
                `<span class="sub">units ${h.units || 0} · invested ₹${(h.investedValue || 0).toFixed(2)} · ` +
                `value ₹${(h.currentValue || 0).toFixed(2)}</span>`;

            toggleSipFields();
            updatePreview();
            return loadBanks();
        })
        .catch(err => alert('Error loading mutual fund: ' + err.message));
}

// --------------------------------------------------------------- bootstrap
Promise.all([loadSchemes(), loadBanks()]).then(() => {
    if (!sipId) {
        const today = new Date();
        document.getElementById('sipStartDate').value = today.toISOString().slice(0, 10);
    }
    toggleSipFields();
    updatePreview();
}).catch(err => console.error(err));
