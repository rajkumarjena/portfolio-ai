// param-config.js - "Add Application Parameter" screen.
//
// ADMIN_V6 #1/#2: the Investment Type and Parameter Name drop-downs are filled
// from the backend's cached config (GET /app/config serves AppParamController's
// cachedConfig list - no database query per page load). Parameter Name only
// offers the param_name values already used for the selected investment type.
document.addEventListener('DOMContentLoaded', () => {
    const bondForm = document.getElementById('bondForm');
    const typeSelect = document.getElementById('investmentTypeSelect');
    const nameSelect = document.getElementById('paramNameSelect');

    // Distinct, trimmed, sorted values - keeps the drop-downs stable no matter
    // how many rows share the same type / name.
    function distinctValues(rows, key, typeFilter) {
        const values = rows
            .filter(r => r && r[key] != null && r[key] !== ''
                && (!typeFilter || (r.investmentType || '').trim() === typeFilter))
            .map(r => String(r[key]).trim());
        return [...new Set(values)].sort((a, b) => a.localeCompare(b));
    }

    // ADMIN_V6 #1.b: Parameter Name options depend on the selected type.
    function fillParamNames(config, selectedType) {
        nameSelect.innerHTML = '';
        const placeholder = document.createElement('option');
        placeholder.value = '';
        if (selectedType) {
            placeholder.textContent = '-- Select Parameter Name --';
            distinctValues(config, 'paramName', selectedType).forEach(name => {
                const option = document.createElement('option');
                option.value = name;
                option.textContent = name;
                nameSelect.appendChild(option);
            });
        } else {
            placeholder.textContent = '-- Select Investment Type first --';
        }
        nameSelect.prepend(placeholder);
    }

    // ADMIN_V6 #1.a: Investment Type options come from investment_type values.
    function fillInvestmentTypes(config) {
        typeSelect.innerHTML = '';
        const placeholder = document.createElement('option');
        placeholder.value = '';
        placeholder.textContent = '-- Select Investment Type --';
        typeSelect.appendChild(placeholder);
        distinctValues(config, 'investmentType').forEach(type => {
            const option = document.createElement('option');
            option.value = type;
            option.textContent = type.toUpperCase();
            typeSelect.appendChild(option);
        });
    }

    // One fetch of the cached config drives both drop-downs (rule #2).
    let cachedConfig = [];
    Api.guard(Api.get('/app/config'), 'Could not load configuration').then(config => {
        if (!config) return;
        cachedConfig = config;
        fillInvestmentTypes(cachedConfig);
        fillParamNames(cachedConfig, typeSelect.value || null);
    });

    typeSelect.addEventListener('change', () => {
        fillParamNames(cachedConfig, typeSelect.value || null);
    });

    bondForm.addEventListener('submit', async (event) => {
        event.preventDefault();

        const formData = new FormData(bondForm);
        const formPayload = Object.fromEntries(formData);

        try {
            const response = await fetch('/app/config', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formPayload)
            });

            if (response.ok) {
                alert('Parameter saved successfully!');
                bondForm.reset();
                fillParamNames(cachedConfig, null);
            } else {
                const errorResponse = await response.json().catch(() => ({}));
                alert('Failed to save parameter: ' + (errorResponse.message || 'Server error'));
            }
        } catch (error) {
            console.error('Error submitting form data:', error);
            alert('A network error occurred. Please try again.');
        }
    });
});

/**
 * Fix for your "Home" button navigation issue
 */
function navigateAway(path) {
    window.location.href = path;
}
