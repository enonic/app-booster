const subscriptionKey = 'enonic.platform.subscription';
const licenseLib = require('/lib/license');
const authLib = require('/lib/xp/auth');

const allowedRoles = ['role:system.admin', 'role:cms.admin'];

const getProjectOwnerRole = (project) => project ? `role:cms.project.${project}.owner` : null;

exports.hasAllowedRole = (project) => {
    let hasAllowedRole = false;
    allowedRoles.concat(getProjectOwnerRole(project)).forEach(role => {
        if (authLib.hasRole(role)) {
            log.info('Current user has role: ' + role);
            hasAllowedRole = true;
        }
    });
    return hasAllowedRole;
}

const getLicenseDetails = (license) => {
    const params = {
        appKey: subscriptionKey,
    };
    if (license) {
        params.license = license;
    }

    return licenseLib.validateLicense(params);
}

const isLicenseValid = (license) => {
    const licenseDetails = license ? getLicenseDetails(license) : getLicenseDetails();

    return licenseDetails && !licenseDetails.expired;
}

exports.isLicenseValid = isLicenseValid;

exports.installLicense = (license) => {
    if (!isLicenseValid(license)) {
        return false;
    }

    licenseLib.installLicense({
        license: license,
        appKey: subscriptionKey,
    });

    return true;
}
