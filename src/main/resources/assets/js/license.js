(() => {

    if (!document.currentScript) {
        throw 'Cannot upload a licence - legacy browsers are not supported';
    }

    const serviceUrl = document.currentScript.getAttribute('data-service-url');
    if (!serviceUrl) {
        throw 'Unable to find license upload service';
    }

    const form = document.getElementById("licenseForm");
    form.addEventListener("onsubmit", event => event.preventDefault());

    const uploadInput = document.getElementById("booster-license-upload");

    uploadInput.addEventListener("change", () => {
        const formData = new FormData();
        formData.append("license", uploadInput.files[0]);
        const errorDiv = document.getElementById('license-invalid-message');
        errorDiv.classList.remove('visible');

        fetch(serviceUrl, {
            body: formData,
            "Content-type": "multipart/form-data",
            method: "POST",
        })
        .then(response => response.json())
        .then(data => {
            if (data.licenseValid) {
                const widgetContainer = document.getElementById("widget-booster-container");
                widgetContainer.classList.remove('license-invalid');
                widgetContainer.classList.add('license-valid');
                const invalidLicenseDiv = document.getElementById('widget-booster-license-invalid');
                invalidLicenseDiv.remove();
            } else if (errorDiv) {
                errorDiv.classList.add('visible');
                setTimeout(() => errorDiv.classList.remove('visible'), 5000);
            }
        });
    });
})();
