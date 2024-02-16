const portal = require('/lib/xp/portal');
const contentLib = require('/lib/xp/content');
const mustache = require('/lib/mustache');

function handleGet(req) {
    return renderWidgetView(req);
}

function renderWidgetView(req) {
    const contentId = req.params.contentId || '';
    const project = req.params.repository.replace('com.enonic.cms.', '') || '';
    let contentPath = '';
    let isSiteSelected = false;
    let isContentSelected = false;

    if (contentId) {
        const content = contentLib.get({
            key: contentId
        });
        contentPath = content._path;
        isSiteSelected = content.type === 'portal:site';
        isContentSelected = !isSiteSelected;
    }

    const view = resolve('booster.html');
    const params = {
        contentId,
        contentPath,
        project,
        isContentSelected,
        isSiteSelected,
        isProjectSelected: !isContentSelected && !isSiteSelected,
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
