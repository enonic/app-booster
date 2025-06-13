const portal = require('/lib/xp/portal');
const contentLib = require('/lib/xp/content');
const mustache = require('/lib/mustache');
const helper = require("/lib/helper");
const nodeLib = require('/lib/xp/node');

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

const getCommonlyCachedPaths = (project, numResults) => {
    const boosterRepo = nodeLib.connect({
        repoId: app.name,
        branch: 'master'
    });

    const allCachedPaths = boosterRepo.query({
        start: 0,
        count: 0,
        query: {
            'term': {
                'field': 'project',
                'value': project
            }
        },
        aggregations: {
            paths: {
                terms: {
                    field: 'path',
                    order: '_count DESC',
                    size: numResults
                }
            }
        }
    });

    return allCachedPaths.total ? allCachedPaths.aggregations.paths.buckets : [];
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
        commonlyCachedPaths: getCommonlyCachedPaths(project, 20),
        pathStatsServiceUrl: portal.serviceUrl({ service: 'pathstats' }),
        error,
        hint
    };

    return {
        contentType: 'text/html',
        body: mustache.render(view, params)
    };
}

exports.get = (req) => renderWidgetView(req);
