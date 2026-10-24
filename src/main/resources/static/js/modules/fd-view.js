const pageSize = 20;
let allFds = [];

async function loadBankFilter() {
    try {
        const response = await fetch('/api/fds/banks');
        if (!response.ok) return;
        const banks = await response.json();
        const select = document.getElementById('filterBank');
        banks.forEach(bank => {
            const option = document.createElement('option');
            option.value = bank.paramValue;
            option.textContent = bank.paramValue;
            select.appendChild(option);
        });
    } catch (error) {
        console.error('Error loading bank filter:', error);
    }
}

async function loadAll() {
    try {
        const response = await fetch('/api/fds');
        if (!response.ok) throw new Error('Failed to fetch fixed deposits');
        allFds = await response.json();
        renderTable(allFds);
    } catch (error) {
        alert('Error loading fixed deposits: ' + error);
    }
}

function renderTable(data) {
    const tableBody = document.querySelector('#fdTable tbody');
    const pagination = document.getElementById('pagination');
    tableBody.innerHTML = '';
    pagination.innerHTML = '';

    const pageCount = Math.max(1, Math.ceil(data.length / pageSize));

    function displayPage(page) {
        tableBody.innerHTML = '';
        const start = (page - 1) * pageSize;
        const pageData = data.slice(start, start + pageSize);

        pageData.forEach(fd => {
            const statusClass = fd.status === 'CLOSED' ? 'closed' : 'active';
            const closeBtn = fd.status === 'ACTIVE'
                ? `<button class="close-btn" onclick="closeFd(${fd.id})">Close</button>`
                : '';
            const row = `<tr>
                <td>${fd.id}</td>
                <td>${fd.bankName}</td>
                <td>${fd.depositAmount}</td>
                <td>${fd.depositDate}</td>
                <td>${fd.maturityDate}</td>
                <td>${fd.interestRate}</td>
                <td>${fd.maturityAmount}</td>
                <td><span class="badge ${statusClass}">${fd.status}</span></td>
                <td>
                    <button class="edit-btn" onclick="editFd(${fd.id})">Edit</button>
                    ${closeBtn}
                    <button class="close-btn" onclick="deleteFd(${fd.id})">Delete</button>
                </td>
            </tr>`;
            tableBody.innerHTML += row;
        });

        pagination.innerHTML = '';
        for (let i = 1; i <= pageCount; i++) {
            pagination.innerHTML += `<button onclick="displayPage(${i})">${i}</button>`;
        }
    }

    window.displayPage = displayPage;
    displayPage(1);
    renderSummary(data);
}

function renderSummary(data) {
    const active = data.filter(fd => fd.status !== 'CLOSED');
    const closed = data.filter(fd => fd.status === 'CLOSED');
    const invested = data.reduce((sum, fd) => sum + (fd.depositAmount || 0), 0);
    document.getElementById('summary').innerText =
        `Showing ${data.length} Fixed Deposit(s) | Active: ${active.length} | Closed: ${closed.length} | Total Invested: ${invested.toLocaleString()}`;
}

function applyFilter() {
    const bank = document.getElementById('filterBank').value;
    const status = document.getElementById('filterStatus').value;
    const depositFrom = document.getElementById('filterDepositFrom').value;
    const depositTo = document.getElementById('filterDepositTo').value;
    const maturityFrom = document.getElementById('filterMaturityFrom').value;
    const maturityTo = document.getElementById('filterMaturityTo').value;
    const amountMin = parseFloat(document.getElementById('filterAmountMin').value);
    const amountMax = parseFloat(document.getElementById('filterAmountMax').value);
    const interestMin = parseFloat(document.getElementById('filterInterestMin').value);
    const interestMax = parseFloat(document.getElementById('filterInterestMax').value);

    const filtered = allFds.filter(fd => {
        if (bank && fd.bankName !== bank) return false;
        if (status && fd.status !== status) return false;
        if (depositFrom && fd.depositDate < depositFrom) return false;
        if (depositTo && fd.depositDate > depositTo) return false;
        if (maturityFrom && fd.maturityDate < maturityFrom) return false;
        if (maturityTo && fd.maturityDate > maturityTo) return false;
        if (!isNaN(amountMin) && fd.depositAmount < amountMin) return false;
        if (!isNaN(amountMax) && fd.depositAmount > amountMax) return false;
        if (!isNaN(interestMin) && fd.interestRate < interestMin) return false;
        if (!isNaN(interestMax) && fd.interestRate > interestMax) return false;
        return true;
    });

    renderTable(filtered);
}

function resetFilter() {
    document.querySelectorAll('.filter-section select').forEach(el => el.value = '');
    document.querySelectorAll('.filter-section input').forEach(el => el.value = '');
    renderTable(allFds);
}

function editFd(id) {
    window.location.href = '/fds/fd-form.html?id=' + id;
}

function closeFd(id) {
    // Redirect to the edit screen where the Premature Closure panel lives
    window.location.href = '/fds/fd-form.html?id=' + id + '#closure';
}

function deleteFd(id) {
    if (confirm('Are you sure you want to delete this fixed deposit?')) {
        fetch('/api/fds/' + id, { method: 'DELETE' })
            .then(res => {
                if (res.ok) loadAll();
                else alert('Failed to delete fixed deposit');
            });
    }
}

document.addEventListener('DOMContentLoaded', () => {
    loadBankFilter();
    loadAll();
});
