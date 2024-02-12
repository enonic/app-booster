exports.run = function (params, taskId) {
    log.debug('Running booster node purge all task with params: ' + JSON.stringify(params));
    __.newBean('com.enonic.app.booster.storage.NodeCleanerBean').purgeAll();
};
