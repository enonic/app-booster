(() => {

    if (!document.currentScript) {
        throw 'Legacy browsers are not supported';
    }

    let confirmationCallback;

    const actions = {
        INVALIDATE: 'invalidate',
        STATUS: 'status'
    };

    const taskStates = {
        FINISHED: 'FINISHED',
        FAILED: 'FAILED'
    };

    const sendRequest = (url, action, data, responseCallback) => {
        const req = new XMLHttpRequest();
        req.open("POST", url, true);

        req.setRequestHeader("Content-Type", "application/json;charset=UTF-8");

        req.onload = () => responseCallback(req, action);
        req.onerror = () => responseCallback(req, action);

        const params = {
            action,
            data
        }

        req.send(JSON.stringify(params));
    };

    const isSuccessfulRequest = (request) => {
        const statusOk = request.status >= 200 && request.status < 300;
        return !(!statusOk || !request.response);
    }

    const isTaskCompleted = (request) => {
        if (!isSuccessfulRequest(request)) {
            return false;
        }
        const response = request.response && JSON.parse(request.response);
        return !!taskStates[response.state];
    };

    const showNotification = (message) => {
        responseContainer.classList.remove('success', 'failure');
        responseContainer.classList.add('visible');
        responseContainer.innerText = message;
    }

    const showSuccessNotification = (message) => {
        showNotification(message);
        responseContainer.classList.add('success');
    }

    const showFailureNotification = (message) => {
        showNotification(message);
        responseContainer.classList.add('failure');
    }

    const waitForTaskCompletion = async (taskId) => {
        const timeout = 10000;
        return new Promise((resolve, reject) => {
            const startTime = Date.now();
            const pollId = setInterval(() => {
                if (Date.now() - startTime > timeout) {
                    clearInterval(pollId);
                    reject(new Error('Task completion timeout'));
                }
                sendRequest(serviceUrl, actions.STATUS, {taskId}, (req) => {
                    if (isTaskCompleted(req)) {
                        clearInterval(pollId);
                        const response = req.response && JSON.parse(req.response);
                        resolve(response.state);
                    }
                });
            }, 1000);
        });
    }

    const handleResponse = async (request, action) => {
        const response = request.response && JSON.parse(request.response);
        const success = isSuccessfulRequest(request) && !!response.taskId
        if (success) {
            try {
                const taskState = await waitForTaskCompletion(response.taskId);

                if (taskState === taskStates.FINISHED) {
                    showSuccessNotification('Cache successfully purged');
                } else if (taskState === taskStates.FAILED) {
                    showFailureNotification('Failed to purge cache');
                }
            } catch (e) {
                showFailureNotification('Failed to fetch task status');
            }

        } else {
            if (action === actions.INVALIDATE) {
                showFailureNotification(`Failed to purge: ${request.statusText} (${request.status})`);
            }
        }

        setTimeout(() => {
            submitButton.removeAttribute('disabled');
            hideNotification();
            window.dispatchEvent(new CustomEvent('ReloadActiveWidgetEvent'));
        }, 3000);
    }

    const hideNotification = () => {
        responseContainer.innerText = '';
        responseContainer.classList.remove('visible', 'success', 'failure');
    }

    const hideConfirmation = () => confirmationContainer.classList.remove('visible');

    const showConfirmation = (text) => {
        confirmationText.innerHTML = text || 'Are you sure?';
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
        return `Purge cache for all content in project "<b>${project}</b>"?`;
    };

    const initEventListeners = () => {
        submitButton && submitButton.addEventListener('click', () => {
            confirmationCallback = () => {
                showNotification('Initiated cache purge...');
                submitButton.setAttribute('disabled', 'disabled');
                sendRequest(serviceUrl, actions.INVALIDATE, { project }, (req, action) => handleResponse(req, action));
            }
            showConfirmation(getConfirmationQuestion());
        });

        confirmYesButton && confirmYesButton.addEventListener('click', () => {
            hideConfirmation();
            if (confirmationCallback) {
                confirmationCallback();
            }
        });

        confirmNoButton && confirmNoButton.addEventListener('click', () => hideConfirmation());
    }

    const serviceUrl = document.currentScript.getAttribute('data-service-url');
    if (!serviceUrl) {
        throw 'Unable to find Booster service';
    }

    const project = document.currentScript.getAttribute('data-project');

    const confirmationContainer = document.getElementById('widget-booster-confirmation-dialog');
    const modalDialogWrapper = document.getElementById('widget-booster-modal-dialog-wrapper');
    const confirmationText = document.getElementById('widget-booster-confirmation-text');

    const submitButton = document.getElementById('widget-booster-container-action');
    const confirmYesButton = document.getElementById('widget-booster-confirmation-button-yes');
    const confirmNoButton = document.getElementById('widget-booster-confirmation-button-no');
    const responseContainer = document.getElementById('widget-booster-action-response');

    initEventListeners();

})()
