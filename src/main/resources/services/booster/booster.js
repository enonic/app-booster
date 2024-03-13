const contentLib = require('/lib/xp/content');
const taskLib = require('/lib/xp/task');
const licenseManager = require("/lib/license-manager");

const submitTask = function (descriptor, config) {
    return taskLib.submitTask({
        descriptor,
        config
    });
}

const getTaskStatus = function (taskId) {
    const taskDetails = taskLib.get(taskId);
    if (!taskDetails) {
        return {
            status: 404,
            body: 'Task not found'
        };
    }
    return {
        contentType: 'application/json',
        body: taskDetails,
        status: 200
    };
}

exports.post = function (req) {
    const supportedActions = ['invalidate', 'purge-all', 'enforce-all', 'status'];
    const params = JSON.parse(req.body);

    if (!licenseManager.isLicenseValid()) {
        return {
            status: 500,
            body: 'Invalid license'
        };
    }

    if (!params.action || supportedActions.indexOf(params.action.trim()) === -1) {
        return {
            status: 400,
            body: 'Invalid action'
        };
    }
    const action = params.action.trim();
    let taskId = params.data.taskId;

    if (action === 'status' && taskId) {
        return getTaskStatus(taskId);
    }

    const contentId = params.data.contentId;
    const project = params.data.project;
    const config = { project };

    if (contentId) {
        const content = contentLib.get({
            key: contentId
        });
        if (content.type === 'portal:site') {
            config.site = contentId;
        } else {
            config.content = contentId;
        }
    }

    if (action === 'invalidate') {
        taskId = submitTask(action, config);
    }

    return {
        contentType: 'application/json',
        body: {
            taskId
        },
        status: 200
    };
};
