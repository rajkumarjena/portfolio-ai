function navigateAway(url) {
    window.location.href = url;
}

const urlParams = new URLSearchParams(window.location.search);
const fdId = urlParams.get('id');

const form = document.getElementById('fdForm');
const depositAmountInput = document.getElementById('depositAmount');
const interestRateInput = document.getElementById('interestRate');
const maturityAmountInput = document.getElementById('maturityAmount');
const depositDateInput = document.getElementById('depositDate');
const maturityDateInput = document.getElementById('maturityDate');

// Loaded from backend when editing
let currentFd = null;

// Automatically set the Deposit Date to today for a new entry
if (!fdId && depositDateInput) {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, '0');
    const day = String(today.getDate()).padStart(2, '0');
    depositDateInput.value = `${year}-${month}-${day}`;
}

// Populate bank dropdown from database-configurable AppParamConfig (investmentType=fd)
async function loadBanks() {
    try {
        const response = await fetch('/api/fds/banks');
        if (!response.ok) throw new Error('Failed to fetch bank names');
        const banks = await response.json();

        const select = document.getElementById('bankSelect');
        // Preserve the bank already chosen (edit mode may have populated the
        // form before these async options arrived)
        const desiredBank = (currentFd && currentFd.bankName) || select.value;
        select.innerHTML = '';
        banks.forEach(bank => {
            const option = document.createElement('option');
            option.value = bank.paramValue;
            option.textContent = bank.paramValue;
            select.appendChild(option);
        });
        if (desiredBank) select.value = desiredBank;
    } catch (error) {
        console.error('Error loading bank names:', error);
    }
}

/**
 * FD_V1 #2: Indian banking fixed-deposit calculation. Interest compounds
 * QUARTERLY, so the maturity amount is P * (1 + R/400)^(4 * years), with the
 * tenure derived from the deposit and maturity DATES (the redundant 'tenure'
 * field is gone). Mirrors FdCalculator on the server, which recomputes and
 * stores the authoritative value on save.
 */
function calculateEstimate() {
    const amount = parseFloat(depositAmountInput?.value) || 0;
    const rate = parseFloat(interestRateInput?.value) || 0;
    const depositDate = depositDateInput?.value ? new Date(depositDateInput.value) : null;
    const maturityDate = maturityDateInput?.value ? new Date(maturityDateInput.value) : null;

    let interest = 0;
    let maturity = amount;
    if (amount > 0 && depositDate && maturityDate && maturityDate >= depositDate) {
        // Actual days / 365, same convention as the backend
        const days = Math.round((maturityDate - depositDate) / 86400000);
        const years = days / 365;
        maturity = Math.round(amount * Math.pow(1 + rate / 400, 4 * years) * 100) / 100;
        interest = Math.round((maturity - amount) * 100) / 100;
    }

    document.getElementById('interestEstimate').value = interest ? interest.toFixed(2) : '';
    document.getElementById('maturityEstimate').value = maturity > amount ? maturity.toFixed(2) : '';
    // Mature Amount is populated automatically (#2) and read-only in the form
    if (maturityAmountInput && !maturityAmountInput.disabled) {
        maturityAmountInput.value = maturity > amount ? maturity.toFixed(2) : '';
    }
}

if (depositAmountInput) depositAmountInput.addEventListener('input', calculateEstimate);
if (interestRateInput) interestRateInput.addEventListener('input', calculateEstimate);
if (depositDateInput) depositDateInput.addEventListener('change', calculateEstimate);
if (maturityDateInput) maturityDateInput.addEventListener('change', calculateEstimate);

// If editing, fetch FD data and populate form
if (fdId && form) {
    fetch('/api/fds/' + fdId)
        .then(res => {
            if (!res.ok) throw new Error('Failed to load fixed deposit');
            return res.json();
        })
        .then(fd => {
            currentFd = fd;
            Object.keys(fd).forEach(key => {
                const field = form.querySelector(`[name=${key}]`);
                if (field && fd[key] !== null) field.value = fd[key];
            });
            calculateEstimate();

            document.getElementById('saveBtn').textContent = 'Update Fixed Deposit';

            // Show status banner + closure panel when editing
            const banner = document.getElementById('statusBanner');
            banner.style.display = 'block';
            if (fd.status === 'CLOSED') {
                banner.textContent = `Status: CLOSED on ${fd.closureDate} for ${fd.closureAmount}` +
                    (fd.closureReason ? ` (${fd.closureReason})` : '');
                document.getElementById('closureCard').style.display = 'none';
                form.querySelectorAll('input, select, textarea, button[type=submit]')
                    .forEach(el => el.disabled = true);
            } else {
                banner.textContent = 'Status: ACTIVE';
                document.getElementById('closureCard').style.display = 'block';
                // Prefill closure fields
                const today = new Date();
                document.getElementById('closureDate').value =
                    `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
                document.getElementById('closureAmount').value = fd.maturityAmount;
            }
        })
        .catch(err => alert('Error loading fixed deposit: ' + err));
}

if (form) {
    form.addEventListener('submit', function (e) {
        e.preventDefault();
        const formData = new FormData(this);
        const fd = {};
        formData.forEach((value, key) => {
            if (['depositAmount', 'interestRate', 'maturityAmount'].includes(key)) fd[key] = parseFloat(value);
            else fd[key] = value;
        });

        if (new Date(fd.maturityDate) < new Date(fd.depositDate)) {
            alert('Mature date must be on or after the deposit date');
            return;
        }

        if (currentFd) {
            fd.status = currentFd.status;
            fd.closureDate = currentFd.closureDate;
            fd.closureAmount = currentFd.closureAmount;
            fd.closureReason = currentFd.closureReason;
        }

        const method = fdId ? 'PUT' : 'POST';
        const url = fdId ? '/api/fds/' + fdId : '/api/fds';

        fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(fd)
        })
            .then(res => {
                if (res.ok) {
                    alert('Fixed deposit saved successfully!');
                    window.location.href = '/fds/fd-view.html';
                } else {
                    return res.text().then(msg => { throw new Error(msg); });
                }
            })
            .catch(err => alert('Error saving fixed deposit: ' + err));
    });
}

// Premature closure: mark FD closed and persist via PUT
document.getElementById('closeFdBtn').addEventListener('click', () => {
    if (!currentFd) return;

    const closureDate = document.getElementById('closureDate').value;
    const closureAmount = parseFloat(document.getElementById('closureAmount').value);
    const closureReason = document.getElementById('closureReason').value;

    if (!closureDate) { alert('Please enter the closure date'); return; }
    if (isNaN(closureAmount)) { alert('Please enter the closure amount'); return; }

    if (!confirm(`Close this fixed deposit prematurely on ${closureDate}?`)) return;

    const fd = Object.fromEntries(new FormData(form));
    fd.id = currentFd.id;
    fd.depositAmount = parseFloat(fd.depositAmount);
    fd.interestRate = parseFloat(fd.interestRate);
    fd.maturityAmount = parseFloat(fd.maturityAmount);
    fd.status = 'CLOSED';
    fd.closureDate = closureDate;
    fd.closureAmount = closureAmount;
    fd.closureReason = closureReason;

    fetch('/api/fds/' + currentFd.id, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(fd)
    })
        .then(res => {
            if (res.ok) {
                alert('Fixed deposit closed successfully!');
                window.location.href = '/fds/fd-view.html';
            } else {
                res.text().then(msg => alert('Failed to close fixed deposit: ' + msg));
            }
        })
        .catch(err => alert('Error closing fixed deposit: ' + err));
});

document.addEventListener('DOMContentLoaded', () => {
    loadBanks();
});
