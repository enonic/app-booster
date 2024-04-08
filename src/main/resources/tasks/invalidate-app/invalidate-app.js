exports.run = function (params, taskId) {
    log.debug('Running booster node cleaner by app task with params: ' + JSON.stringify(params));
    let nodeCleanerBean = __.newBean('com.enonic.app.booster.storage.NodeCleanerBean');

    nodeCleanerBean.invalidateWithApp(params.app);
};
