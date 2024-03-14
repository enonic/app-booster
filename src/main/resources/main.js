const clusterLib = require('/lib/xp/cluster');
const cron = require('/lib/cron');

cron.schedule({
    name: 'booster-invalidate-scheduled',
    delay: 1000,
    fixedDelay: 10000,
    callback: function () {
        if (clusterLib.isMaster()) {
            __.newBean('com.enonic.app.booster.storage.NodeCleanerBean').invalidateScheduled();
        }
    }
});

cron.schedule({
    name: 'booster-delete-excess-nodes',
    cron: '* * * * *',
    callback: function () {
        if (clusterLib.isMaster()) {
            __.newBean('com.enonic.app.booster.storage.NodeCleanerBean').scavenge();
        }
    }
});


__.disposer(function () {
    log.debug('Unscheduling invalidate-scheduled');
    cron.unschedule({
        name: 'booster-invalidate-scheduled'
    });
    cron.unschedule({
        name: 'booster-delete-excess-nodes'
    });
});
