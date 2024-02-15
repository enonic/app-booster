const portal = require('/lib/xp/portal');
const mustache = require('/lib/mustache');

function handleGet(req) {
    return renderWidgetView(req);
}

function renderWidgetView(req) {
    const contentId = req.params.contentId;
    const repository = req.params.repository;

    const view = resolve('booster.html');
    const params = {
        contentId: contentId || '',
        project: repository || '',
        assetsUri: portal.assetUrl({
            path: ''
        }),
        serviceUrl: portal.serviceUrl({service: 'booster'})
    };

    return {
        contentType: 'text/html',
        body: mustache.render(view, params)
    };
}

exports.get = handleGet;
