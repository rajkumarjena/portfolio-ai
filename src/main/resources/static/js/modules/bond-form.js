function navigateAway(url) {
  window.location.href = url;
}

const urlParams = new URLSearchParams(window.location.search);
const bondId = urlParams.get('id');
const viewMode = urlParams.get('view') === '1' && !!bondId;

const form = document.getElementById('bondForm');
const investmentAmountInput = document.getElementById('investmentAmountPerUnit');
const unitsInput = document.getElementById('units');
const investmentDateInput = document.getElementById('investmentDate');

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

let cashFlowData = [];
let couponFrequencyDisplayMap = new Map();

if (bondId && form) {
  fetch('/api/bonds/' + bondId)
    .then(res => res.json())
    .then(bond => {
      Object.keys(bond).forEach(key => {
        const field = form.querySelector(`[name=${key}]`);
        if (field) field.value = bond[key] || '';
      });
      if (bond.cashFlowData && Array.isArray(bond.cashFlowData)) {
        cashFlowData = bond.cashFlowData.map(c => ({...c}));
        renderTable();
      }
      calculateTotal();
      formChanged = false;
    })
    .catch(err => alert('Error loading bond: ' + err));
}

if (form) {
  form.addEventListener('submit', function(e) {
    e.preventDefault();
    if (viewMode) return;
    const formData = new FormData(this);
    const bond = {};
    formData.forEach((value, key) => bond[key] = value);
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

function addMonths(startDate, monthsToAdd) {
  const originalDay = startDate.getDate();
  let year = startDate.getFullYear();
  let month = startDate.getMonth() + monthsToAdd;
  year += Math.floor(month / 12);
  month = month % 12;
  const lastDayOfTargetMonth = new Date(year, month + 1, 0).getDate();
  const day = Math.min(originalDay, lastDayOfTargetMonth);
  return new Date(year, month, day);
}

function addDays(date, days) {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

function fmtDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function daysBetweenInclusiveOrCount(start, end) {
  const s = new Date(start);
  const e = new Date(end);
  return Math.max(1, Math.ceil((e.getTime() - s.getTime()) / (1000 * 60 * 60 * 24)) + 1);
}

function generateCashFlowData(startDateStr, endDateStr) {
  const generatedData = [];
  const faceValuePerUnitInput = Number(document.getElementById('faceValuePerUnit')?.value) || 0;
  const unitInput = Number(document.getElementById('units')?.value) || 0;
  const couponRate = Number(document.querySelector('[name="couponRate"]')?.value) || 0;
  const couponFreqSel = document.getElementById('couponFrequencySelect');
  const freqValue = couponFreqSel?.options[couponFreqSel.selectedIndex]?.textContent || couponFreqSel?.value || '';
  const freqText = (freqValue || '').toLowerCase();

  let currentDate, endDate;
  if (startDateStr.includes('-')) {
    currentDate = new Date(startDateStr + 'T00:00:00');
    endDate = new Date(endDateStr + 'T00:00:00');
  } else {
    const sp = startDateStr.split('.');
    const ep = endDateStr.split('.');
    currentDate = new Date(parseInt(sp[2]), parseInt(sp[1]) - 1, parseInt(sp[0]));
    endDate = new Date(parseInt(ep[2]), parseInt(ep[1]) - 1, parseInt(ep[0]));
  }

  const totalFaceValue = faceValuePerUnitInput * unitInput;
  const annualInterest = (totalFaceValue * couponRate) / 100;
  const interestPerDay = totalFaceValue > 0 && annualInterest > 0 ? annualInterest / 365 : 0;

  if (freqText === 'at maturity' || freqText === 'maturity') {
    const maturityFormatted = fmtDate(endDate);
    generatedData.push({
      year: endDate.getFullYear(),
      schedule: 'Maturity',
      date: maturityFormatted,
      payout: 0,
      amount: totalFaceValue,
      valuePct: 100
    });
    return generatedData;
  }
  if (freqText === 'custom') {
    return [];
  }

  let stepMonths = 1;
  if (freqText.includes('quarter')) stepMonths = 3;
  else if (freqText.includes('half')) stepMonths = 6;
  else if (freqText.includes('year')) stepMonths = 12;
  else if (freqText.includes('month')) stepMonths = 1;
  else stepMonths = 1;

  const dates = [];
  const startD = new Date(currentDate);
  let d = new Date(startD);
  let count = 1;
  while (d <= endDate) {
    const copy = new Date(d);
    dates.push({ date: copy, year: copy.getFullYear(), label: 'Payout #' + count, idx: count });
    d = addMonths(startD, count * stepMonths);
    count++;
    if (count > 2000) break; // safety
  }

  dates.forEach((dt, i) => {
    const dtDate = dt.date;
    const last = i === dates.length - 1;
    let payout = 0;
    if (interestPerDay > 0) {
      const prev = i === 0 ? new Date(startD) : dates[i - 1].date;
      const days = Math.max(0, Math.ceil((dtDate.getTime() - prev.getTime()) / (1000 * 60 * 60 * 24)));
      payout = Math.round(days * interestPerDay * 100) / 100;
    }
    const amount = last ? totalFaceValue : 0;
    const formatted = fmtDate(dtDate);
    generatedData.push({
      year: dt.year,
      schedule: dt.label,
      date: formatted,
      payout: payout,
      amount: amount,
      valuePct: last ? 100 : 0
    });
  });

  // ensure maturity date included
  if (dates.length > 0) {
    const lastRec = generatedData[generatedData.length - 1];
    const maturityFormatted = fmtDate(endDate);
    if (lastRec.date !== maturityFormatted) {
      let payout = 0;
      if (interestPerDay > 0) {
        const prevLast = dates[dates.length - 1].date;
        const days = Math.max(1, Math.ceil((endDate.getTime() - prevLast.getTime()) / (1000 * 60 * 60 * 24)));
        payout = Math.round(days * interestPerDay * 100) / 100;
      }
      generatedData.push({
        year: endDate.getFullYear(),
        schedule: 'Payout #' + (dates.length + 1),
        date: maturityFormatted,
        payout: payout,
        amount: totalFaceValue,
        valuePct: 100
      });
      if (generatedData.length >= 2) {
        generatedData[generatedData.length - 2].amount = 0;
        generatedData[generatedData.length - 2].valuePct = 0;
      }
    } else {
      // maturity matches last
      lastRec.amount = totalFaceValue;
      lastRec.valuePct = 100;
      if (interestPerDay > 0) {
        const prev = dates.length > 1 ? dates[dates.length - 2].date : startD;
        const days = Math.max(1, Math.ceil((endDate.getTime() - prev.getTime()) / (1000 * 60 * 60 * 24)));
        lastRec.payout = Math.round(days * interestPerDay * 100) / 100;
      }
    }
  } else {
    const maturityFormatted = fmtDate(endDate);
    generatedData.push({
      year: endDate.getFullYear(),
      schedule: 'Payout #1',
      date: maturityFormatted,
      payout: interestPerDay > 0 ? Math.round(daysBetweenInclusiveOrCount(startD, endDate) * interestPerDay * 100) / 100 : 0,
      amount: totalFaceValue,
      valuePct: 100
    });
  }

  return generatedData;
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

const couponFreqSelect = document.getElementById('couponFrequencySelect');
if (couponFreqSelect) {
  couponFreqSelect.addEventListener('change', checkAndTrigger);
}
if (investmentAmountInput) investmentAmountInput.addEventListener('change', checkAndTrigger);
if (unitsInput) unitsInput.addEventListener('change', checkAndTrigger);
const faceValueInput = document.getElementById('faceValuePerUnit');
if (faceValueInput) {
  faceValueInput.addEventListener('change', checkAndTrigger);
}
const couponRateInput = document.querySelector('[name="couponRate"]');
if (couponRateInput) couponRateInput.addEventListener('change', checkAndTrigger);

function checkAndTrigger() {
  if (viewMode) return;
  if (!invStartDateInput || !maturityDateInput) return;
  const startDate = invStartDateInput.value;
  const maturityDate = maturityDateInput.value;
  if (startDate && maturityDate) {
    if (new Date(startDate) > new Date(maturityDate)) {
      alert('Maturity date must be after start date');
      return;
    }
    cashFlowData = generateCashFlowData(startDate, maturityDate);
    renderTable();
  }
}

// BONDBP_V3 4-6: keep redemption total and value % inside the face value cap
// whenever the user edits or removes schedule rows.
function recalcRedemption() {
  const faceValuePerUnitInput = Number(document.getElementById('faceValuePerUnit')?.value) || 0;
  const unitInput = Number(document.getElementById('units')?.value) || 0;
  const totalFaceValue = faceValuePerUnitInput * unitInput;
  if (totalFaceValue <= 0 || cashFlowData.length === 0) { renderTable(); return; }

  let totalRed = cashFlowData.reduce((s, r) => s + (Number(r.amount) || 0), 0);
  const lastIdx = cashFlowData.length - 1;
  if (totalRed > totalFaceValue) {
    // BONDBP_V3 4/6: the excess redemption is deducted from the LAST record
    // so the schedule total never exceeds the total face value.
    const excess = totalRed - totalFaceValue;
    const lastAmt = Math.max(0, Math.round(((Number(cashFlowData[lastIdx].amount) || 0) - excess) * 100) / 100);
    cashFlowData[lastIdx].amount = lastAmt;
    totalRed = cashFlowData.reduce((s, r) => s + (Number(r.amount) || 0), 0);
  }
  // BONDBP_V3 4/6: no top-up - a mid-schedule redemption the user entered stays;
  // only enforce that the TOTAL redemption never exceeds the total face value.
  cashFlowData.forEach(r => {
    r.valuePct = Math.round(((Number(r.amount) || 0) / totalFaceValue) * 10000) / 100;
  });
  let pctSum = cashFlowData.reduce((s, r) => s + (Number(r.valuePct) || 0), 0);
  if (pctSum > 100 && lastIdx >= 0) {
    cashFlowData[lastIdx].valuePct = Math.max(0,
      Math.round((Number(cashFlowData[lastIdx].valuePct) - (pctSum - 100)) * 100) / 100);
  }
  renderTable();
}

var renderTable = function () {
  const tbody = document.getElementById('paymentScheduleBody');
  if (!tbody) return;
  tbody.innerHTML = '';

  const addBtnContainer = document.getElementById('addRecordContainer');
  if (addBtnContainer) {
    addBtnContainer.innerHTML = viewMode ? '' :
      '<button type="button" onclick="addCustomRow()">Add Record</button>';
  }

  cashFlowData.forEach((row, index) => {
    const tr = document.createElement('tr');
    tr.dataset.index = index;
    const actionCell = viewMode ? 'View'
      : '<button type="button" class="edit-btn" onclick="toggleEditRow(this)">Edit</button> '
      + '<button type="button" class="delete-btn" onclick="deleteRow(' + index + ')">Delete</button>';

    tr.innerHTML = '<td>' + row.year + '</td>'
      + '<td>' + escCell(row.schedule) + '</td>'
      + '<td>' + escCell(row.date) + '</td>'
      + '<td>' + (row.payout || 0) + '</td>'
      + '<td>' + (row.amount || 0) + '</td>'
      + '<td>' + (row.valuePct || 0) + '</td>'
      + '<td>' + actionCell + '</td>';
    tbody.appendChild(tr);
  });
};

function escCell(v) {
  return String(v == null ? '' : v)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function deleteRow(index) {
  if (index >= 0 && index < cashFlowData.length) {
    cashFlowData.splice(index, 1);
    recalcRedemption();
  }
}

function addCustomRow() {
  cashFlowData.push({
    year: new Date().getFullYear(),
    schedule: 'Custom Payout',
    date: fmtDate(new Date()),
    payout: 0,
    amount: 0,
    valuePct: 0
  });
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
    btn.innerText = 'Save';
    btn.classList.add('saving');
  } else {
    for (let i = 0; i < cells.length - 1; i++) {
      cells[i].removeAttribute('contenteditable');
    }
    btn.innerText = 'Edit';
    btn.classList.remove('saving');

    // BONDBP_V3 7: the edited row lands in cashFlowData immediately, so
    // hitting Save Bond later persists it even without a per-row save.
    if (cashFlowData[dataIndex]) {
      cashFlowData[dataIndex] = {
        id: cashFlowData[dataIndex].id || null,
        year: parseInt(cells[0].innerText) || 0,
        schedule: cells[1].innerText.trim(),
        date: cells[2].innerText.trim(),
        payout: parseFloat(cells[3].innerText) || 0,
        amount: parseFloat(cells[4].innerText) || 0,
        valuePct: parseFloat(cells[5].innerText) || 0
      };
      recalcRedemption();
    }
  }
}

async function loadIssuers() {
  try {
    const response = await fetch('/api/bonds/issuer');
    if (!response.ok) throw new Error('Failed to fetch issuers');
    const issuers = await response.json();
    const select = document.getElementById('issuerSelect');
    if (!select) return;
    select.innerHTML = '';
    issuers.forEach(issuer => {
      const option = document.createElement('option');
      option.value = issuer.id;
      option.textContent = issuer.paramValue;
      select.appendChild(option);
    });
  } catch (error) {
    console.error('Error loading issuers:', error);
  }
}

async function loadCouponFrequency() {
  try {
    const response = await fetch('/api/bonds/coupon-frequency');
    if (!response.ok) throw new Error('Failed to fetch coupon frequency');
    const freqs = await response.json();
    const select = document.getElementById('couponFrequencySelect');
    if (!select) return;
    select.innerHTML = '';
    freqs.forEach(f => {
      const option = document.createElement('option');
      option.value = f.id;
      option.textContent = f.paramValue;
      select.appendChild(option);
      couponFrequencyDisplayMap.set(String(f.id), f.paramValue);
      couponFrequencyDisplayMap.set((f.paramValue || '').toLowerCase(), f.paramValue);
    });
    // The schedule may have been generated before options arrived (defaulting
    // to monthly); regenerate now that the real frequency text is known.
    checkAndTrigger();
  } catch (error) {
    console.error('Error loading coupon frequency:', error);
  }
}

async function loadCreditRating() {
  try {
    const response = await fetch('/api/bonds/credit/rating');
    if (!response.ok) throw new Error('Failed to fetch Credit Rating');
    const ratings = await response.json();
    const select = document.getElementById('creditRatingSelect');
    if (!select) return;
    select.innerHTML = '';
    ratings.forEach(r => {
      const option = document.createElement('option');
      option.value = r.id;
      option.textContent = r.paramValue;
      select.appendChild(option);
    });
  } catch (error) {
    console.error('Error loading credit rating:', error);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  applyViewMode();
  loadIssuers();
  loadCouponFrequency();
  loadCreditRating();
  renderTable();
  checkAndTrigger();
});

window.addCustomRow = addCustomRow;
window.deleteRow = deleteRow;
window.toggleEditRow = toggleEditRow;
