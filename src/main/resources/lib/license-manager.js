const subscriptionKey = "enonic.platform.subscription";
const licenseLib = require("/lib/license");

const getLicenseDetails = function (license) {
    const params = {
        appKey: subscriptionKey,
    };
    if (license) {
        params.license = license;
    }

    return licenseLib.validateLicense(params);
}

const isLicenseValid = function (license) {
    const licenseDetails = license ? getLicenseDetails(license) : getLicenseDetails();

    return licenseDetails && !licenseDetails.expired;
}

exports.isLicenseValid = isLicenseValid;

exports.installLicense = function (license) {
    if (!isLicenseValid(license)) {
        return false;
    }

    licenseLib.installLicense({
        license: license,
        appKey: subscriptionKey,
    });

    return true;
}
