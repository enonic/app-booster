exports.run = function (params, taskId) {
    log.debug('Running booster background job: ' + JSON.stringify(params));
    __.newBean('com.enonic.app.booster.storage.ScheduledTasksBean').execute();
};
