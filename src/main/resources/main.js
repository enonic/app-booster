const schedulerLib = require('/lib/xp/scheduler');
const clusterLib = require('/lib/xp/cluster');


if (clusterLib.isMaster()) {
    let job = schedulerLib.get({name: 'booster-background-job'});
    if (job) {
        schedulerLib.modify({
            name: 'booster-background-job',
            editor: (edit) => {
                edit.enabled = true;
                return edit;
            }
        });
    } else {
        const job = schedulerLib.create({
            name: 'booster-background-job',
            descriptor: 'booster-background-task',
            enabled: true,
            schedule: {type: 'CRON', value: '0/10 * * * *', timeZone: 'UTC'},
        });
    }
}

__.disposer(function () {
    if (clusterLib.isMaster()) {
        schedulerLib.modify({
            name: 'booster-background-job',
            editor: (edit) => {
                edit.enabled = false;
                return edit;
            }
        });
    }
});
