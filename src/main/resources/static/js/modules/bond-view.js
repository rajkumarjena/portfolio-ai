const pageSize = 20;

function renderTable(bonds, tableId, paginationId) {
  const tableBody = document.querySelector(`#${tableId} tbody`);
  const pagination = document.getElementById(paginationId);
  let currentPage = 1;

  function displayPage(page) {
    tableBody.innerHTML = '';
    const start = (page - 1) * pageSize;
    const end = start + pageSize;
    const pageData = bonds.slice(start, end);

    pageData.forEach(bond => {
      const row = `<tr>
        <td>${bond.bondName}</td>
        <td>${bond.issuer}</td>
        <td>${bond.investmentDate}</td>
        <td>${bond.maturityDate}</td>
        <td>
          <button onclick="editBond(${bond.id})">Edit</button>
          <button onclick="deleteBond(${bond.id})">Delete</button>
        </td>
      </tr>`;
      tableBody.innerHTML += row;
    });

    pagination.innerHTML = '';
    for (let i = 1; i <= Math.ceil(bonds.length / pageSize); i++) {
      pagination.innerHTML += `<button onclick="displayPage(${i})">${i}</button>`;
    }
  }

  displayPage(currentPage);
}

// Fetch bonds from backend
fetch('/api/bonds')
  .then(res => {
    if (!res.ok) throw new Error("Failed to fetch bonds");
    return res.json();
  })
  .then(bonds => {
    const today = new Date();
    const active = bonds.filter(b => b.maturityDate && new Date(b.maturityDate) >= today);
    const expired = bonds.filter(b => b.maturityDate && new Date(b.maturityDate) < today);

    renderTable(active, 'activeBonds', 'activePagination');
    renderTable(expired, 'expiredBonds', 'expiredPagination');
  })
  .catch(err => alert("Error loading bonds: " + err));

function editBond(id) {
  // Redirect to bond-form with id for editing
  window.location.href = '/bonds/bond-form.html?id=' + id;
}

function deleteBond(id) {
  if (confirm("Are you sure you want to delete this bond?")) {
    fetch('/api/bonds/' + id, { method: 'DELETE' })
      .then(res => {
        if (res.ok) location.reload();
        else alert("Failed to delete bond");
      });
  }
}