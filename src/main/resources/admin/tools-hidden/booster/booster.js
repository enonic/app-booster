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
                project: req.params.projects,
                domain: req.params.domain,
                path: req.params.path,
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
        checkboxesHtml += '<li><label><input type="checkbox" name="projects" value="' + projects[i].id + '">' + projects[i].displayName + '</label></li>';
    }
    checkboxesHtml += '</ul>'

    return '<form action="" method="post">' +
           '<fieldset>' +
           '<legend>Projects</legend>' +
           checkboxesHtml +
           '<hr>' +
           '<legend>Domain</legend>' +
           '<input type="text" name="domain" value="">' +
           '<legend>Path prefix</legend>' +
           '<input type="text" name="path" value="">' +
           '</fieldset>' +
           '<input type="submit" value="Purge Cache">' +
           '</form>';
}
