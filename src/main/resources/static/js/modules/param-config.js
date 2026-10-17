// app-param-config-script.js
document.addEventListener('DOMContentLoaded', () => {
    const bondForm = document.getElementById('bondForm');

    // 1. Listen for the form submission triggered by the "Save Param" button
    bondForm.addEventListener('submit', async (event) => {
        // Prevent the default browser page reload behavior
        event.preventDefault();

        // 2. Automatically grab all values from the form inputs
        const formData = new FormData(bondForm);

        // 3. Convert the form data into a standard JavaScript object
        const formPayload = Object.fromEntries(formData);

        try {
            // 4. Call the POST method for the API endpoint "/app/config"
            const response = await fetch('/app/config', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formPayload) // Send data as a JSON string
            });

            // 5. Handle the response from your server
            if (response.ok) {
                alert('Parameter saved successfully!');
                bondForm.reset(); // Optional: clears the form after a successful save
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
