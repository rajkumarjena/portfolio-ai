function navigateAway(url) {
    window.location.href = url;
}

const urlParams = new URLSearchParams(window.location.search);
const bondId = urlParams.get('id');
// BONDBP A.5: ?view=1 opens an existing bond read-only - every field, the
// schedule and the save action are all disabled, nothing here edits data.
const viewMode = urlParams.get('view') === '1' && !!bondId;

const form = document.getElementById('bondForm');
const investmentAmountInput = document.getElementById('investmentAmountPerUnit');
const unitsInput = document.getElementById('units');
const investmentDateInput = document.getElementById('investmentDate');

// Automatically set the Investment Date to today using local timezone boundaries
if (!bondId && investmentDateInput) {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, '0');
    const day = String(today.getDate()).padStart(2, '0');

    investmentDateInput.value = `${year}-${month}-${day}`;
}

let formChanged = false;
if (form) {
    form.addEventListener('input', () => { formChanged = true; });
}

// Put the whole screen into view-only mode: title, fields and save action.
function applyViewMode() {
    if (!viewMode) return;
    const heading = document.querySelector('#content h1');
    if (heading) heading.textContent = 'Bond Details (View Only)';
    if (form) {
        form.querySelectorAll('input, select, textarea').forEach(el => { el.disabled = true; });
        const submit = form.querySelector('button[type=submit]');
        if (submit) submit.style.display = 'none';
    }
}

// Function to calculate the total based on Investment Amount and Units
function calculateTotal() {
    const amount = parseFloat(investmentAmountInput?.value) || 0;
    const units = parseFloat(unitsInput?.value) || 0;
    const totalElement = document.getElementById('investmentTotal');
    if (totalElement) {
        totalElement.value = amount * units;
    }
}

if (investmentAmountInput) investmentAmountInput.addEventListener('input', calculateTotal);
if (unitsInput) unitsInput.addEventListener('input', calculateTotal);

// Global data array holding schedule instances
let cashFlowData = [];

// If editing, fetch bond data and populate form
if (bondId && form) {
  fetch('/api/bonds/' + bondId)
    .then(res => res.json())
    .then(bond => {
      Object.keys(bond).forEach(key => {
        const field = form.querySelector(`[name=${key}]`);
        if (field) field.value = bond[key] || '';
      });

      // 🛠️ FIX 1: Read the saved cash flow data from the database object when editing
      if (bond.cashFlowData && Array.isArray(bond.cashFlowData)) {
          cashFlowData = bond.cashFlowData;
          renderTable();
      }

      calculateTotal();
      formChanged = false;
    })
    .catch(err => alert("Error loading bond: " + err));
}

if (form) {
  form.addEventListener('submit', function(e) {
    e.preventDefault();
    if (viewMode) return; // read-only screen - never persists
    const formData = new FormData(this);
    const bond = {};
    formData.forEach((value, key) => bond[key] = value);

    // 🛠️ FIX 2: Attach the data model list array to match the Spring Boot nested parameter structure
    bond.cashFlowData = cashFlowData;

    const method = bondId ? 'PUT' : 'POST';
    const url = bondId ? '/api/bonds/' + bondId : '/api/bonds';

    fetch(url, {
      method: method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(bond)
    })
    .then(res => {
        if (res.ok) {
            alert('Bond saved successfully!');
            window.location.href = '/bonds/bond-view.html';
        } else {
            alert('Failed to save bond.');
        }
    })
    .catch(err => alert('Error: ' + err));
  });
}

function generateCashFlowData(startDateStr, endDateStr) {
     const generatedData = [];

     const faceValuePerUnitInput = Number(
            document.getElementById("faceValuePerUnit").value
        );
     const unitInput = Number(
            document.getElementById("units").value
        );

    let currentDate, endDate;
    if (startDateStr.includes('-')) {
        currentDate = new Date(startDateStr);
        endDate = new Date(endDateStr);
    } else {
        const startDateParts = startDateStr.split(".");
        const endDateParts = endDateStr.split(".");
        currentDate = new Date(parseInt(startDateParts[2]), parseInt(startDateParts[1]) - 1, parseInt(startDateParts[0]));
        endDate = new Date(parseInt(endDateParts[2]), parseInt(endDateParts[1]) - 1, parseInt(endDateParts[0]));
    }

    let scheduleCount = 1;
    const startD = new Date(currentDate);
    while (currentDate <= endDate) {
        const year = currentDate.getFullYear();
        const month = String(currentDate.getMonth() + 1).padStart(2, "0");
        const day = String(currentDate.getDate()).padStart(2, "0");
        const formattedDate = `${year}-${month}-${day}`;
        generatedData.push({
            year: year,
            schedule: `Payout #${scheduleCount}`,
            date: formattedDate,
            payout: 0,
            amount: 0,
            valuePct: 0
        });

        currentDate = addMonths(startD, scheduleCount);
        scheduleCount++;
    }

    // 🛠️ Ensure final maturity date is always included
    const lastRecord = generatedData[generatedData.length - 1];
    const maturityFormatted = `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2, "0")}-${String(endDate.getDate()).padStart(2, "0")}`;
    if (lastRecord.date !== maturityFormatted) {
        generatedData.push({
            year: endDate.getFullYear(),
            schedule: `Payout #${scheduleCount}`,
            date: maturityFormatted,
            payout: 0,
            amount: faceValuePerUnitInput * unitInput,
            valuePct: 100
        });
    } else {
        // If maturity date matches last record, update it with redemption
         lastRecord.amount = faceValuePerUnitInput * unitInput;
         lastRecord.valuePct = 100;
    }

    return generatedData;
}

function addMonths(startDate, monthsToAdd) {
    originalDay = startDate.getDate();

    let year = startDate.getFullYear();
    let month = startDate.getMonth() + monthsToAdd;

    year += Math.floor(month/12);
    month = month % 12;

    const lastDayOfTargetMonth = new Date(year, month + 1, 0).getDate();

    const day = Math.min(originalDay, lastDayOfTargetMonth);

    return new Date(year, month, day);
}



const invStartDateInput = document.getElementById('interestPaymentStartDate');
const maturityDateInput = document.getElementById('maturityDate');

if (invStartDateInput) {
    invStartDateInput.addEventListener('input', checkAndTrigger);
    invStartDateInput.addEventListener('change', checkAndTrigger);
}
if (maturityDateInput) {
    maturityDateInput.addEventListener('input', checkAndTrigger);
    maturityDateInput.addEventListener('change', checkAndTrigger);
}

function checkAndTrigger() {
    // View-only mode never regenerates the loaded schedule
    if (viewMode) return;

    if (!invStartDateInput || !maturityDateInput) {
        console.log("Inputs not found");
        return;
    }

    const startDate = invStartDateInput.value;
    const maturityDate = maturityDateInput.value;

    console.log("checkAndTrigger called with:", startDate, maturityDate);

    if (startDate && maturityDate) {
        if (new Date(startDate) > new Date(maturityDate)) {
            alert("Maturity date must be after start date");
            return;
        }

        cashFlowData = generateCashFlowData(startDate, maturityDate);
        console.log("Generated cashFlowData:", cashFlowData);
        renderTable();
    }
}

var renderTable = function() {
    const tbody = document.getElementById('paymentScheduleBody');
    if (!tbody) return;
    tbody.innerHTML = "";

    // Add button above the table (never in view-only mode)
    const addBtnContainer = document.getElementById('addRecordContainer');
    if (addBtnContainer) {
        addBtnContainer.innerHTML = viewMode ? '' : `
            <button type="button" onclick="addCustomRow()">Add Record</button>
        `;
    }

    cashFlowData.forEach((row, index) => {
        const tr = document.createElement('tr');
        tr.dataset.index = index;

        const actionCell = viewMode ? 'View'
            : `<button type="button" class="edit-btn" onclick="toggleEditRow(this)">Edit</button>
                <button type="button" class="delete-btn" onclick="deleteRow(${index})">Delete</button>`;

        tr.innerHTML = `
            <td>${row.year}</td>
            <td>${row.schedule}</td>
            <td>${row.date}</td>
            <td>${row.payout}</td>
            <td>${row.amount}</td>
            <td>${row.valuePct}</td>
            <td>${actionCell}</td>
        `;
        tbody.appendChild(tr);
    });
}

// Delete function
function deleteRow(index) {
    if (index >= 0 && index < cashFlowData.length) {
        cashFlowData.splice(index, 1);
        renderTable();
    }
}

// Add custom row function
function addCustomRow() {
    const newRow = {
        year: new Date().getFullYear(),
        schedule: `Custom Payout`,
        date: `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, "0")}-${String(new Date().getDate()).padStart(2, "0")}`,
        payout: 0,
        amount: 0,
        valuePct: 0
    };
    cashFlowData.push(newRow);
    renderTable();
}

function toggleEditRow(btn) {
    const row = btn.closest('tr');
    const cells = row.getElementsByTagName('td');
    const isEditing = btn.classList.contains('saving');
    const dataIndex = row.dataset.index;

    if (!isEditing) {
        for (let i = 0; i < cells.length - 1; i++) {
            cells[i].setAttribute('contenteditable', 'true');
        }
        btn.innerText = "Save";
        btn.classList.add('saving');
    } else {
        for (let i = 0; i < cells.length - 1; i++) {
            cells[i].removeAttribute('contenteditable');
        }
        btn.innerText = "Edit";
        btn.classList.remove('saving');

        if (cashFlowData[dataIndex]) {
            cashFlowData[dataIndex] = {
                // If row already has an id property (from editing), preserve it
                id: cashFlowData[dataIndex].id || null,
                year: parseInt(cells[0].innerText) || 0,
                schedule: cells[1].innerText.trim(),
                date: cells[2].innerText.trim(),
                payout: parseFloat(cells[3].innerText) || 0,
                amount: parseFloat(cells[4].innerText) || 0,
                valuePct: parseFloat(cells[5].innerText) || 0
            };
            console.log("Updated JavaScript array element:", cashFlowData[dataIndex]);
        }
    }
}

async function loadIssuers() {
  try {
    const response = await fetch("/api/bonds/issuer"); // call your API
    if (!response.ok) {
      throw new Error("Failed to fetch issuers");
    }
    const issuers = await response.json();

    const select = document.getElementById("issuerSelect");
    select.innerHTML = ""; // clear existing options

    issuers.forEach(issuer => {
      const option = document.createElement("option");
      option.value = issuer.id;        // backend ID
      option.textContent = issuer.paramValue; // display name
      select.appendChild(option);
    });
  } catch (error) {
    console.error("Error loading issuers:", error);
  }
}

async function loadCouponFrequency() {
  try {
    const response = await fetch("/api/bonds/coupon-frequency"); // call your API
    if (!response.ok) {
      throw new Error("Failed to fetch coupon frequency");
    }
    const issuers = await response.json();

    const select = document.getElementById("couponFrequencySelect");
    select.innerHTML = ""; // clear existing options

    issuers.forEach(issuer => {
      const option = document.createElement("option");
      option.value = issuer.id;        // backend ID
      option.textContent = issuer.paramValue; // display name
      select.appendChild(option);
    });
  } catch (error) {
    console.error("Error loading coupon frequency:", error);
  }
}

async function loadCreditRating() {
  try {
    const response = await fetch("/api/bonds/credit/rating"); // call your API
    if (!response.ok) {
      throw new Error("Failed to fetch Credit Rating");
    }
    const issuers = await response.json();

    const select = document.getElementById("creditRatingSelect");
    select.innerHTML = ""; // clear existing options

    issuers.forEach(issuer => {
      const option = document.createElement("option");
      option.value = issuer.id;        // backend ID
      option.textContent = issuer.paramValue; // display name
      select.appendChild(option);
    });
  } catch (error) {
    console.error("Error loading issuers:", error);
  }
}

document.addEventListener("DOMContentLoaded", () => {
  applyViewMode();
  loadIssuers();
  loadCouponFrequency();
  loadCreditRating();
  renderTable();
  checkAndTrigger();
});

