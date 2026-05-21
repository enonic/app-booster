const portalLib = require('/lib/xp/portal');
const contentLib = require('/lib/xp/content');
const contextLib = require('/lib/xp/context');
const nodeLib = require('/lib/xp/node');
const taskLib = require('/lib/xp/task');
const ioLib = require('/lib/xp/io');
const auditLogLib = require('/lib/xp/auditlog');
const mustache = require('/lib/mustache');
const staticLib = require('/lib/enonic/static');
const Router = require('/lib/router');
const helper = require("/lib/helper");

const STATIC_BASE = '/_static';
const PURGE_PATH = '/purge';
const LICENSE_PATH = '/license';
const PATHSTATS_PATH = '/pathstats';
const STATE_PATH = '/state';

const SUPPORTED_ACTIONS = ['invalidate', 'purge-all', 'enforce-all', 'status'];

const forceArray = (data) => (Array.isArray(data) ? data : new Array(data));

const isAppEnabledOnSite = (contentId, repository) => {
    if (!contentId) {
        return true;
    }
    return contextLib.run({
        repository: repository,
        branch: 'draft'
    }, () => {
        const site = contentLib.getSite({key: contentId});
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
    });
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

const logManualCachePurge = (config) => {
    auditLogLib.log({
        type: 'booster.manualCachePurge',
        message: 'Manual cache purge requested',
        data: {
            project: config.project || null,
            content: config.content || null,
            site: config.site || null,
            domain: config.domain || null,
            path: config.path || null
        }
    });
}

const getTaskStatus = (taskId) => {
    const taskDetails = taskLib.get(taskId);
    if (!taskDetails) {
        return {
            status: 404,
            body: 'Task not found'
        };
    }
    return {
        contentType: 'application/json',
        body: taskDetails,
        status: 200
    };
}

const computeProjectState = (contentId, repository) => {
    const project = repository ? repository.replace('com.enonic.cms.', '') : '';

    if (!project) {
        return {error: 'Project not found'};
    }
    if (!helper.hasAllowedRole(project)) {
        return {error: 'You do not have a permission to access this application'};
    }

    const nodeCleanerBean = __.newBean('com.enonic.app.booster.script.NodeCleanerBean');
    const size = nodeCleanerBean.getProjectCacheSize(project);
    const commonlyCachedPaths = getCommonlyCachedPaths(project, 20);
    const hint = (contentId && !isAppEnabledOnSite(contentId, repository))
        ? 'Booster app is not added to this site'
        : undefined;

    return {project, size, commonlyCachedPaths, hint};
}

const renderWidgetView = (req) => {
    const contentId = req.params.contentId || '';
    const repository = req.params.repository || '';
    const projectState = computeProjectState(contentId, repository);

    const stateQuery = '?repository=' + encodeURIComponent(repository) + '&contentId=' + encodeURIComponent(contentId);

    const state = {
        project: projectState.project || '',
        size: projectState.size || 0,
        isEnabled: !projectState.error,
        isLicenseValid: helper.isLicenseValid(),
        serviceUrl: req.contextPath + PURGE_PATH,
        licenseUploadUrl: req.contextPath + LICENSE_PATH,
        pathStatsUrl: req.contextPath + PATHSTATS_PATH,
        refreshUrl: req.contextPath + STATE_PATH + stateQuery,
        commonlyCachedPaths: projectState.commonlyCachedPaths || [],
        error: projectState.error,
        hint: projectState.hint
    };

    const view = resolve('booster.html');
    const params = {
        assetsUri: req.contextPath + STATIC_BASE,
        state: JSON.stringify(state).replace(/</g, '\\u003c')
    };

    return {
        contentType: 'text/html',
        body: mustache.render(view, params)
    };
}

const handlePurge = (req) => {
    const params = JSON.parse(req.body);

    if (!helper.isLicenseValid()) {
        return {status: 500, body: 'Invalid license'};
    }

    if (!params.action || SUPPORTED_ACTIONS.indexOf(params.action.trim()) === -1) {
        return {status: 400, body: 'Invalid action'};
    }

    const action = params.action.trim();
    let taskId = params.data.taskId;

    if (action === 'status' && taskId) {
        return getTaskStatus(taskId);
    }

    const contentId = params.data.contentId;
    const project = params.data.project;
    const config = {project};

    if (!helper.hasAllowedRole(project)) {
        return {status: 403, body: 'Forbidden'};
    }

    if (contentId) {
        const content = contentLib.get({key: contentId});
        if (content.type === 'portal:site') {
            config.site = contentId;
        } else {
            config.content = contentId;
        }
    }

    if (action === 'invalidate') {
        logManualCachePurge(config);
        taskId = taskLib.submitTask({descriptor: action, config});
    }

    return {
        contentType: 'application/json',
        body: {taskId},
        status: 200
    };
}

const handleLicenseUpload = (req) => {
    const licenseStream = portalLib.getMultipartStream("license");
    const license = ioLib.readText(licenseStream);
    const licenseInstalled = helper.installLicense(license);

    if (!licenseInstalled) {
        return {status: 400};
    }
    return {
        status: 200,
        contentType: "application/json",
        body: {licenseValid: true}
    };
}

const parseQueryString = (queryString) => {
    if (!queryString) {
        return null;
    }

    return queryString.split('&').reduce((acc, pair) => {
        const parts = pair.split('=');
        const key = parts[0];
        const value = parts[1];

        if (!acc[key]) {
            acc[key] = {};
        }

        acc[key][value] = true;
        return acc;
    }, {});
}

const getUniqueParams = (urlBuckets) => {
    const queryStrings = urlBuckets.map(url => url.key ? url.key.split('?')[1] : '');
    const parsedQueryStrings = queryStrings.map(parseQueryString).filter((parsed) => parsed !== null);

    let allParams = {};
    parsedQueryStrings.forEach((parsed) => {
        for (let param in parsed) {
            if (parsed.hasOwnProperty(param)) {
                allParams[param] = true;
            }
        }
    });

    let result = [];
    for (let param in allParams) {
        if (allParams.hasOwnProperty(param)) {
            let uniqueValues = {};
            parsedQueryStrings.forEach((parsed) => {
                if (parsed[param]) {
                    for (let value in parsed[param]) {
                        if (parsed[param].hasOwnProperty(value)) {
                            uniqueValues[value] = true;
                        }
                    }
                }
            });
            const uniqueValuesArray = Object.keys(uniqueValues);

            result.push({
                paramName: param,
                uniqueValuesCount: uniqueValuesArray.length
            });
            result.sort((a, b) => b.uniqueValuesCount - a.uniqueValuesCount);
        }
    }

    return result;
}

const handlePathStats = (req) => {
    const path = req.params.path;

    const boosterRepo = nodeLib.connect({
        repoId: app.name,
        branch: 'master'
    });

    const pathUrls = boosterRepo.query({
        start: 0,
        count: 0,
        query: {
            'term': {
                'field': 'path',
                'value': path
            }
        },
        aggregations: {
            urls: {
                terms: {
                    field: 'url',
                    size: 10000
                }
            }
        }
    });

    const uniqueParams = getUniqueParams(pathUrls.aggregations.urls.buckets);

    return {
        contentType: 'application/json',
        body: {uniqueParams}
    };
}

const router = Router();

router.get(STATIC_BASE + '/{path:.*}', (req) => staticLib.requestHandler(req, {
    index: false,
    root: '/assets',
    relativePath: (r) => r.pathParams.path
}));

router.get(STATE_PATH, (req) => {
    const projectState = computeProjectState(
        req.params.contentId || '',
        req.params.repository || ''
    );
    if (projectState.error) {
        return {status: 403, body: projectState.error};
    }
    return {
        contentType: 'application/json',
        body: {
            size: projectState.size,
            commonlyCachedPaths: projectState.commonlyCachedPaths,
            hint: projectState.hint
        }
    };
});
router.post(PURGE_PATH, handlePurge);
router.post(LICENSE_PATH, handleLicenseUpload);
router.get(PATHSTATS_PATH, handlePathStats);

router.get('{path:.*}', renderWidgetView);

exports.GET = (req) => router.dispatch(req);
exports.POST = (req) => router.dispatch(req);
