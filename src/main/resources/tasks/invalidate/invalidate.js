exports.run = function (params, taskId) {
    log.debug('Running booster node cleaner task with params: ' + JSON.stringify(params));
    __.newBean('com.enonic.app.booster.storage.NodeCleanerBean').invalidateRepos([].concat(params.repos));
};
