const portal = require('/lib/xp/portal');
const contentLib = require('/lib/xp/content');
const authLib = require('/lib/xp/auth');
const mustache = require('/lib/mustache');
const licenseManager = require("/lib/license-manager");

const forceArray = (data) => (Array.isArray(data) ? data : new Array(data));

const allowedRoles = ['role:system.admin', 'role:cms.admin'];

const hasAllowedRole = (project) => {
    let hasAllowedRole = false;
    allowedRoles.concat(getProjectOwnerRole(project)).forEach(role => {
        if (authLib.hasRole(role)) {
            log.info('Current user has role: ' + role);
            hasAllowedRole = true;
        }
    });
    return hasAllowedRole;
}

const getProjectOwnerRole = (project) => {
    if (!project) {
        return null;
    }
    return `role:cms.project.${project}.owner`;
}

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
    } else if (!hasAllowedRole(project)) {
        error = 'You do not have permission to access this application';
    }

    if (!error) {
        const nodeCleanerBean = __.newBean('com.enonic.app.booster.storage.NodeCleanerBean');
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
        isLicenseValid: licenseManager.isLicenseValid(),
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
