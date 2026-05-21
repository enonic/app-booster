import {LicenseUpload} from './LicenseUpload';
import {PurgePanel} from './PurgePanel';
import {CachedPathsTable, type CachedPath} from './CachedPathsTable';

export type AppState = {
    project: string;
    size: number;
    isEnabled: boolean;
    isLicenseValid: boolean;
    serviceUrl: string;
    licenseUploadUrl: string;
    pathStatsUrl: string;
    commonlyCachedPaths: CachedPath[];
    error?: string;
    hint?: string;
};

export const App = (props: AppState) => {
    const {
        project,
        size,
        isEnabled,
        isLicenseValid,
        serviceUrl,
        licenseUploadUrl,
        pathStatsUrl,
        commonlyCachedPaths,
        error,
        hint,
    } = props;

    if (!isEnabled) {
        return <div className="widget-booster-error">{error}</div>;
    }

    if (!isLicenseValid) {
        return <LicenseUpload licenseUploadUrl={licenseUploadUrl} />;
    }

    return (
        <div className="widget-booster-content">
            {hint && <p className="widget-booster-hint">{hint}</p>}
            <PurgePanel project={project} size={size} serviceUrl={serviceUrl} />
            <CachedPathsTable paths={commonlyCachedPaths} pathStatsUrl={pathStatsUrl} />
        </div>
    );
};

App.displayName = 'BoosterApp';
