var taskLib = require('/lib/xp/task');
const projectLib = require('/lib/xp/project');

exports.get = function (req) {

    return {
        body: drawForm(),
        contentType: 'text/html;charset=utf-8'
    }
}

exports.post = function (req) {
    let taskId;
    if (req.params) {
        taskId  = taskLib.submitTask({
            descriptor: 'invalidate',
            config: {
                repos: req.params.repos
            }
        });
    }
    return {
        body : (taskId ? `<p>Task submitted ${taskId}</p>` : '<p>No task submitted</p>') + drawForm(),
        contentType: 'text/html;charset=utf-8'
    }
}

function drawForm() {
    const projects = projectLib.list();

    let checkboxesHtml = '<ul>';
    for (let i = 0; i < projects.length; i++) {
        checkboxesHtml += '<li><label><input type="checkbox" name="repos" value="' + 'com.enonic.cms.'+ projects[i].id + '">' + projects[i].displayName + '</label></li>';
    }
    checkboxesHtml += '</ul>'

    return '<form action="" method="post">' +
           '<fieldset>' +
           '<legend>repos</legend>' +
           checkboxesHtml +
           '</fieldset>' +
           '<input type="submit" value="Purge Cache">' +
           '</form>';
}
