exports.run = function (params, taskId) {
    log.debug('Running booster node capped task with params: ' + JSON.stringify(params));
    __.newBean('com.enonic.app.booster.NodeCleanerBean').deleteExcessNodes(params.cacheSize);
};
