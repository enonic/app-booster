exports.run = function (params, taskId) {
    log.debug('Running booster node cleaner task with params: ' + JSON.stringify(params));

    if (!params.content ) {
        __.newBean('com.enonic.app.booster.storage.NodeCleanerBean').invalidateContent(params.project, params.content);
        return;
    }

    if (!params.site ) {
        __.newBean('com.enonic.app.booster.storage.NodeCleanerBean').invalidateSite(params.project, params.site);
        return;
    }
    __.newBean('com.enonic.app.booster.storage.NodeCleanerBean').invalidateProjects([].concat(params.project));
};
