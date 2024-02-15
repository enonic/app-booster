(() => {
    const sendRequest = (url, action, data) => {
        const req = new XMLHttpRequest();
        req.open("POST", url, true);

        req.setRequestHeader("Content-Type", "application/json;charset=UTF-8");

        req.onload = () => {
            if (req.status >= 200 && req.status < 300) {
                onSuccess && onSuccess(req.responseText && JSON.parse(req.responseText));
            } else {
                onError && onError(req.status, req.statusText);
            }
        };

        req.onerror = () => {
            onError && onError(req.status, req.statusText);
        };

        const params = {
            action,
            data: data
        }

        req.send(JSON.stringify(params));
    };

    const onSuccess = (responseText) => {
        const responseContainer = document.getElementById('widget-booster-action-response');
        responseContainer.classList.remove('failure');
        responseContainer.classList.add('success');
    }

    const onError = (status, statusText) => {
        const responseContainer = document.getElementById('widget-booster-action-response');
        responseContainer.classList.remove('success');
        responseContainer.classList.add('failure');
    }

    if (!document.currentScript) {
        throw 'Legacy browsers are not supported';
    }

    const serviceUrl = document.currentScript.getAttribute('data-service-url');
    if (!serviceUrl) {
        throw 'Unable to find Booster service';
    }

    const project = document.currentScript.getAttribute('data-project');
    const contentId = document.currentScript.getAttribute('data-content-id');

    const submitButton = document.getElementById('widget-booster-container-action');
    if (submitButton) {
        submitButton.addEventListener('click', () => {
            sendRequest(serviceUrl, 'invalidate', { project, contentId });
        });
    }
})()
