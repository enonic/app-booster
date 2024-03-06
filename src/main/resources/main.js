const cron = require('/lib/cron');
const clusterLib = require('/lib/xp/cluster');

cron.schedule({
    name: 'invalidate-scheduled',
    delay: 1000,
    fixedDelay: 10000,
    callback: function () {
        if (clusterLib.isMaster()) {
            __.newBean('com.enonic.app.booster.storage.NodeCleanerBean').invalidateScheduled();
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
