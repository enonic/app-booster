(() => {

    const actions = {
        INVALIDATE: 'invalidate'
    };

    const sendRequest = (url, action, data) => {
        const req = new XMLHttpRequest();
        req.open("POST", url, true);

        req.setRequestHeader("Content-Type", "application/json;charset=UTF-8");

        req.onload = () => showResponse(action, req);
        req.onerror = () => showResponse(action, req);

        const params = {
            action,
            data
        }

        req.send(JSON.stringify(params));
    };

    const isSuccessfulRequest = (request) => {
        const statusOk = request.status >= 200 && request.status < 300;
        if (statusOk) {
            const response = request.response && JSON.parse(request.response);
            if (!response.taskId) {
                return false;
            }
        }
        return statusOk;
    }

    const showResponse = (action, request) => {
        const responseContainer = document.getElementById('widget-booster-action-response');
        const success = isSuccessfulRequest(request);
        responseContainer.classList.toggle('success', success);
        responseContainer.classList.toggle('failure', !success);
        if (action === actions.INVALIDATE) {
            const errorMessage = !success && `${request.statusText} (${request.status})`;

            responseContainer.innerText = success ? 'Cache successfully invalidated' : `Failed to invalidate: ${errorMessage}`;
            setTimeout(() => hideResponse(), 3000);
        }
    }

    const hideResponse = () => {
        const responseContainer = document.getElementById('widget-booster-action-response');
        responseContainer.innerText = '';
        responseContainer.classList.remove('failure');
        responseContainer.classList.remove('success');
    }

    const onSuccess = (action, request) => {
        showResponse(action, request);
    }

    const onError = (action, request) => {
        showResponse(action, request);
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
            sendRequest(serviceUrl, actions.INVALIDATE, { project, contentId });
        });
    }
})()
