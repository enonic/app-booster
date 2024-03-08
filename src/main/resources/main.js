const contextLib = require('/lib/xp/context');
const clusterLib = require('/lib/xp/cluster');
const projectLib = require('/lib/xp/project');
const cron = require('/lib/cron');

cron.schedule({
    name: 'invalidate-scheduled',
    delay: 1000,
    fixedDelay: 10000,
    callback: function () {
        if (clusterLib.isMaster()) {
            const bean = __.newBean('com.enonic.app.booster.storage.NodeCleanerBean');
            const allProjects = contextLib.run({principals: ['role:system.admin']}, () => projectLib.list()).map(project => project.id);
            const projects = bean.findScheduledForInvalidation(allProjects);
            bean.invalidateProjects(projects);
        }
    }
});

cron.schedule({
    name: 'delete-excess-nodes',
    cron: '* * * * *',
    callback: function () {
        if (clusterLib.isMaster()) {
            __.newBean('com.enonic.app.booster.storage.NodeCleanerBean').deleteExcessNodes(app.config.cacheSize);
        }
    }
});


__.disposer(function () {
    log.debug('Unscheduling invalidate-scheduled');
    cron.unschedule({
        name: 'invalidate-scheduled'
    });
    cron.unschedule({
        name: 'delete-excess-nodes'
    });
});
