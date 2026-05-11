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
        WAITING: 'WAITING',
        RUNNING: 'RUNNING',
        FINISHED: 'FINISHED',
        FAILED: 'FAILED'
    };

    const taskStatusPolling = {
        interval: 1000,
        timeout: 300000
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

    const getTaskState = (request) => {
        if (!isSuccessfulRequest(request)) {
            return null;
        }
        const response = request.response && JSON.parse(request.response);
        return response && response.state;
    };

    const isTaskCompleted = (taskState) => !!taskStates[taskState] && ![taskStates.WAITING, taskStates.RUNNING].includes(taskState);

    const isTaskPending = (taskState) => [taskStates.WAITING, taskStates.RUNNING].includes(taskState);

    const isTaskCompletionTimeoutError = (error) => error && error.message === 'Task completion timeout';

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
        return new Promise((resolve, reject) => {
            const startTime = Date.now();
            const poll = () => {
                if (Date.now() - startTime > taskStatusPolling.timeout) {
                    reject(new Error('Task completion timeout'));
                    return;
                }

                sendRequest(serviceUrl, actions.STATUS, {taskId}, (req) => {
                    if (!isSuccessfulRequest(req)) {
                        reject(new Error('Task status request failed'));
                        return;
                    }

                    const taskState = getTaskState(req);
                    if (isTaskCompleted(taskState)) {
                        resolve(taskState);
                        return;
                    }

                    if (!isTaskPending(taskState)) {
                        reject(new Error('Unexpected task state'));
                        return;
                    }

                    setTimeout(poll, taskStatusPolling.interval);
                });
            };

            poll();
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
                if (isTaskCompletionTimeoutError(e)) {
                    showNotification('Cache purge is still running. The widget will refresh shortly.');
                } else {
                    showFailureNotification('Failed to fetch task status');
                }
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

        pathDetails.forEach(detailElement => {
            detailElement.addEventListener('click', () => {
                const detailTarget = detailElement.querySelector('.detail-target');
                // Only load details once
                if (detailElement.getAttribute('data-loaded') === 'true') {
                    return;
                }
    
                fetch(detailElement.getAttribute('data-detailurl'))
                    .then(response => response.text())
                    .then(html => {
                        detailTarget.innerHTML = html;
                        detailElement.setAttribute('data-loaded', 'true');
                    })
                    .catch(error => {
                        console.error('Error fetching details:', error);
                    });
            });
        });
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
    const pathDetails = document.querySelectorAll('#widget-booster-commonlycachedpaths details');

    initEventListeners();

})()
