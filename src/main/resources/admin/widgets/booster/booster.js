const portal = require('/lib/xp/portal');
const contentLib = require('/lib/xp/content');
const mustache = require('/lib/mustache');

const forceArray = (data) => (Array.isArray(data) ? data : new Array(data));

function handleGet(req) {
    return renderWidgetView(req);
}

function isAppEnabledOnSite(contentId) {
    if (!contentId) {
        return false;
    }
    const site = contentLib.getSite({ key: contentId });
    if (!site || !site.data || !site.data.siteConfig) {
        return false;
    }
    log.info(JSON.stringify(forceArray(site.data.siteConfig), null, 4));
    let siteConfig;
    forceArray(site.data.siteConfig).forEach(config => {
        if (config.applicationKey == app.name) {
            siteConfig = config
        }
    });
    log.info(JSON.stringify(siteConfig, null, 4));
    return !!siteConfig;
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
        isEnabled: isAppEnabledOnSite(contentId),
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
