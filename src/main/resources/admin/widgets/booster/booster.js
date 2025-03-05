const portal = require('/lib/xp/portal');
const contentLib = require('/lib/xp/content');
const mustache = require('/lib/mustache');
const helper = require("/lib/helper");

const forceArray = (data) => (Array.isArray(data) ? data : new Array(data));

const isAppEnabledOnSite = (contentId) => {
    if (!contentId) {
        return true;
    }
    const site = contentLib.getSite({ key: contentId });
    if (!site) {
        return true;
    }

    if (!site.data || !site.data.siteConfig) {
        return false;
    }

    let siteConfig;
    forceArray(site.data.siteConfig).forEach(config => {
        if (config.applicationKey === app.name) {
            siteConfig = config
        }
    });

    return !!siteConfig;
}

const renderWidgetView = (req) => {
    let error, hint;
    let size;

    const contentId = req.params.contentId || '';
    const project = req.params.repository.replace('com.enonic.cms.', '') || '';

    if (!project) {
        error = 'Project not found';
    } else if (!helper.hasAllowedRole(project)) {
        error = 'You do not have a permission to access this application';
    }

    if (!error) {
        const nodeCleanerBean = __.newBean('com.enonic.app.booster.script.NodeCleanerBean');
        size = nodeCleanerBean.getProjectCacheSize(project);

        if (contentId && !isAppEnabledOnSite(contentId)) {
            hint = 'Booster app is not added to this site';
        }
    }

    const view = resolve('booster.html');
    const params = {
        project,
        size,
        isButtonDisabled: size === 0,
        isEnabled: !error,
        assetsUri: portal.assetUrl({ path: ''}),
        serviceUrl: portal.serviceUrl({ service: 'booster' }),
        isLicenseValid: helper.isLicenseValid(),
        licenseUploadUrl: portal.serviceUrl({ service: 'license-upload' }),
        error,
        hint
    };

    return {
        contentType: 'text/html',
        body: mustache.render(view, params)
    };
}

exports.get = (req) => renderWidgetView(req);
