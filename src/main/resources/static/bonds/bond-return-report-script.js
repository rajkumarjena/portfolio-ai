async function fetchData(url) {
      const response = await fetch(url);
      if (!response.ok) {
        alert("Failed to fetch data");
        return [];
      }
      return await response.json();
    }

    function renderTable(filteredData) {
      const tbody = document.querySelector("#reportTable tbody");
      tbody.innerHTML = "";
      let totalInterest = 0, totalRedemption = 0, totalCredit = 0;

      filteredData.forEach(row => {
      console.log (row);
        const tr = document.createElement("tr");
        tr.innerHTML = `
          <td>${row.id}</td>
          <td>${row.date}</td>
          <td>${row.payout}</td>
          <td>${row.schedule}</td>
          <td>${row.amount}</td>
          <td>${row.year}</td>
          <td>${row.bondName}</td>
        `;
        tbody.appendChild(tr);

        totalInterest += row.payout;
        totalRedemption += row.amount;
        totalCredit += row.payout + row.amount;
      });

      document.getElementById("summary").innerText =
        `Total Interest: ${totalInterest} | Total Redemption: ${totalRedemption} | Total Credit: ${totalCredit}`;
    }

    async function applyFilter() {
      const type = document.getElementById("filterType").value;
      let apiUrl = "/api/cashflows"; // default: all

      if (type === "date") {
        const d = document.getElementById("filterDate").value;
        apiUrl = `/api/cashflows/date/${d}`;
      } else if (type === "month") {
        const m = document.getElementById("filterMonth").value;
        apiUrl = `/api/cashflows/month/${m}`;
      } else if (type === "year") {
        const y = document.getElementById("filterYear").value;
        apiUrl = `/api/cashflows/year/${y}`;
      } else if (type === "range") {
        const start = document.getElementById("filterStart").value;
        const end = document.getElementById("filterEnd").value;
        apiUrl = `/api/cashflows/range?startDate=${start}&endDate=${end}`;
      }

      const filtered = await fetchData(apiUrl);
      renderTable(filtered);
    }

    // Initial load: fetch all
    (async () => {
      const allData = await fetchData("/api/cashflows");
      renderTable(allData);
    })();

    // Show/hide filter inputs based on selection
    document.getElementById("filterType").addEventListener("change", function() {
      document.getElementById("filterDate").style.display = this.value === "date" ? "inline" : "none";
      document.getElementById("filterMonth").style.display = this.value === "month" ? "inline" : "none";
      document.getElementById("filterYear").style.display = this.value === "year" ? "inline" : "none";
      document.getElementById("filterStart").style.display = this.value === "range" ? "inline" : "none";
      document.getElementById("filterEnd").style.display = this.value === "range" ? "inline" : "none";
    });