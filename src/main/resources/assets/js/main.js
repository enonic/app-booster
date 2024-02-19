(() => {

    if (!document.currentScript) {
        throw 'Legacy browsers are not supported';
    }

    let confirmationCallback;

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

    const hideConfirmation = () => confirmationContainer.classList.remove('visible');

    const showConfirmation = (text) => {
        confirmationText.innerHTML = text || 'Are you sure?'
        confirmationContainer.classList.add('visible');
        const cancelIcon = document.querySelector('#widget-booster-confirmation-dialog .cancel-button-top');
        const onCancelConfirmation = (e) => {
            if (modalDialogWrapper.contains(e.target) && e.target.id !== 'cancel-button-top') {
                return;
            }
            hideConfirmation();
            cancelIcon.removeEventListener('click', onCancelConfirmation);
            confirmationContainer.removeEventListener('click', onCancelConfirmation);
        }
        cancelIcon.addEventListener('click', onCancelConfirmation);
        confirmationContainer.addEventListener('click', onCancelConfirmation);
    }

    const getConfirmationQuestion = () => {
        const basePart = 'Purge cache for';
        let customPart;
        if (contentPath) {
            if (isSiteSelected) {
                customPart = `all content in site "<b>${contentPath}</b>"?`;
            } else {
                customPart = `content "<b>${contentPath}</b>"?`;
            }
        } else {
            customPart = `all content in project "<b>${project}</b>"?`;
        }

        return `${basePart} ${customPart}`;
    };

    const serviceUrl = document.currentScript.getAttribute('data-service-url');
    if (!serviceUrl) {
        throw 'Unable to find Booster service';
    }

    const project = document.currentScript.getAttribute('data-project');
    const contentId = document.currentScript.getAttribute('data-content-id');
    const contentPath = document.currentScript.getAttribute('data-content-path');
    const isSiteSelected = document.currentScript.getAttribute('data-site-selected') === 'true';

    const confirmationContainer = document.getElementById('widget-booster-confirmation-dialog');
    const modalDialogWrapper = document.getElementById('widget-booster-modal-dialog-wrapper');
    const confirmationText = document.getElementById('widget-booster-confirmation-text');

    const submitButton = document.getElementById('widget-booster-container-action');
    if (submitButton) {
        submitButton.addEventListener('click', () => {
            confirmationCallback = () => sendRequest(serviceUrl, actions.INVALIDATE, {project, contentId})
            showConfirmation(getConfirmationQuestion());
        });
    }

    const confirmYesButton = document.getElementById('widget-booster-confirmation-button-yes');
    if (confirmYesButton) {
        confirmYesButton.addEventListener('click', () => {
            hideConfirmation();
            if (confirmationCallback) {
                confirmationCallback();
            }
        });
    }

    const confirmNoButton = document.getElementById('widget-booster-confirmation-button-no');

    if (confirmNoButton) {
        confirmNoButton.addEventListener('click', () => hideConfirmation());
    }

})()
