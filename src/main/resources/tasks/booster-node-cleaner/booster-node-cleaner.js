exports.run = function (params, taskId) {
    log.debug('Running booster node cleaner task with params: ' + JSON.stringify(params));
    __.newBean('com.enonic.app.booster.NodeCleanerBean').cleanUpRepos([].concat(params.repos));
};
