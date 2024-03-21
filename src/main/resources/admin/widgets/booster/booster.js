const portal = require('/lib/xp/portal');
const contentLib = require('/lib/xp/content');
const contextLib = require('/lib/xp/context');
const authLib = require('/lib/xp/auth');
const mustache = require('/lib/mustache');
const licenseManager = require("/lib/license-manager");

const forceArray = (data) => (Array.isArray(data) ? data : new Array(data));

const allowedRoles = ['role:system.admin', 'role:cms.admin'];

const hasAllowedRole = () => {
    let hasAllowedRole = false;
    allowedRoles.concat(getProjectOwnerRole()).forEach(role => {
        if (authLib.hasRole(role)) {
            log.info('Current user has role: ' + role);
            hasAllowedRole = true;
        }
    });
    return hasAllowedRole;
}

const getProjectOwnerRole = () => {
    const context = contextLib.get();
    if (!context || !context.repository) {
        return '';
    }
    const project = context.repository.replace('com.enonic.cms.', '');
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
    let contentPath = '';
    let isSiteSelected = false;
    let isContentSelected = false;
    let errorMessage;
    let size;

    const contentId = req.params.contentId || '';
    const project = req.params.repository.replace('com.enonic.cms.', '') || '';

    if (hasAllowedRole()) {
        if (contentId) {
            const content = contentLib.get({
                key: contentId
            });
            contentPath = content._name;
            isSiteSelected = content.type === 'portal:site';
            isContentSelected = !isSiteSelected;
        }

        let nodeCleanerBean = __.newBean('com.enonic.app.booster.storage.NodeCleanerBean');
        if (contentId) {
            if (isSiteSelected) {
                size = nodeCleanerBean.getSiteCacheSize(project, contentId);
            } else {
                size = nodeCleanerBean.getContentCacheSize(project, contentId);
            }
        } else {
            size = nodeCleanerBean.getProjectCacheSize(project);
        }

        if (!isAppEnabledOnSite(contentId)) {
            errorMessage = 'Booster app is not added to the site';
        }
    } else {
        errorMessage = 'You do not have permission to access this application';
    }


    const view = resolve('booster.html');
    const params = {
        contentId,
        contentPath,
        project,
        isContentSelected,
        isSiteSelected,
        size,
        isButtonDisabled: size === 0,
        isProjectSelected: !isContentSelected && !isSiteSelected,
        isEnabled: !errorMessage,
        assetsUri: portal.assetUrl({ path: ''}),
        serviceUrl: portal.serviceUrl({ service: 'booster' }),
        isLicenseValid: licenseManager.isLicenseValid(),
        licenseUploadUrl: portal.serviceUrl({ service: 'license-upload' }),
        errorMessage
    };

    return {
        contentType: 'text/html',
        body: mustache.render(view, params)
    };
}

exports.get = (req) => renderWidgetView(req);
