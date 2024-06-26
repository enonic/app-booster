const portalLib = require("/lib/xp/portal");
const ioLib = require("/lib/xp/io");
const helper = require("/lib/helper");

exports.post = function (req) {
    const licenseStream = portalLib.getMultipartStream("license");
    const license = ioLib.readText(licenseStream);
    const licenseInstalled = helper.installLicense(license);

    if (licenseInstalled) {
        return {
            status: 200,
            contentType: "application/json",
            body: {
                licenseValid: true,
            },
        };
    } else {
        return {
            status: 500,
        };
    }
};
