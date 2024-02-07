exports.run = function (params, taskId) {
    log.debug('Running booster node invalidate all task with params: ' + JSON.stringify(params));
    __.newBean('com.enonic.app.booster.storage.NodeCleanerBean').invalidateAll();
};
