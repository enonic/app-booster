const portal = require('/lib/xp/portal');
const contentLib = require('/lib/xp/content');
const mustache = require('/lib/mustache');

const forceArray = (data) => (Array.isArray(data) ? data : new Array(data));

const isAppEnabledOnSite = (contentId) => {
    if (!contentId) {
        return true;
    }
    const site = contentLib.getSite({ key: contentId });
    if (!site || !site.data || !site.data.siteConfig) {
        return false;
    }

    let siteConfig;
    forceArray(site.data.siteConfig).forEach(config => {
        if (config.applicationKey == app.name) {
            siteConfig = config
        }
    });

    return !!siteConfig;
}

const renderWidgetView = (req) => {
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

exports.get = (req) => renderWidgetView(req);
