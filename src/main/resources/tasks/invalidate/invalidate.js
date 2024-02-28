exports.run = function (params, taskId) {
    log.debug('Running booster node cleaner task with params: ' + JSON.stringify(params));
    let nodeCleanerBean = __.newBean('com.enonic.app.booster.storage.NodeCleanerBean');

    if (params.content) {
        nodeCleanerBean.invalidateContent(params.project, params.content);
        return;
    }

    if (params.site) {
        nodeCleanerBean.invalidateSite(params.project, params.site);
        return;
    }

    if (params.path) {
        nodeCleanerBean.invalidatePathPrefix(params.domain, params.path);
        return;
    }

    if (params.domain) {
        nodeCleanerBean.invalidateDomain(params.domain);
        return;
    }

    if (params.project) {
        nodeCleanerBean.invalidateProjects([].concat(params.project));
        return;
    }

    nodeCleanerBean.invalidateAll();
};
