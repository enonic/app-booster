const contentLib = require('/lib/xp/content');
const taskLib = require('/lib/xp/task');

const submitTask = function (descriptor, config) {
    return taskLib.submitTask({
        descriptor,
        config
    });
}

exports.post = function (req) {
    const supportedActions = ['invalidate', 'purge-all', 'enforce-all'];
    const params = JSON.parse(req.body);

    if (!params.action || supportedActions.indexOf(params.action.trim()) === -1) {
        return {
            status: 400,
            body: 'Invalid action'
        };
    }
    const action = params.action.trim();
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

    let taskId;
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
