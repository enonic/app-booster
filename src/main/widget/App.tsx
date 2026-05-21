import {useState} from 'react';
import {LicenseUpload} from './LicenseUpload';
import {PurgePanel} from './PurgePanel';
import {CachedPathsTable, type CachedPath} from './CachedPathsTable';
import {fetchRefreshState} from './api';

export type AppState = {
    project: string;
    size: number;
    isEnabled: boolean;
    isLicenseValid: boolean;
    serviceUrl: string;
    licenseUploadUrl: string;
    pathStatsUrl: string;
    refreshUrl: string;
    commonlyCachedPaths: CachedPath[];
    error?: string;
    hint?: string;
};

export const App = (props: AppState) => {
    const {
        project,
        isEnabled,
        serviceUrl,
        licenseUploadUrl,
        pathStatsUrl,
        refreshUrl,
        error,
    } = props;

    const [size, setSize] = useState(props.size);
    const [paths, setPaths] = useState(props.commonlyCachedPaths);
    const [hint, setHint] = useState(props.hint);
    const [isLicenseValid, setLicenseValid] = useState(props.isLicenseValid);

    const handleRefresh = async () => {
        const next = await fetchRefreshState(refreshUrl);
        setSize(next.size);
        setPaths(next.commonlyCachedPaths);
        setHint(next.hint);
    };

    if (!isEnabled) {
        return <div className="widget-booster-error">{error}</div>;
    }

    if (!isLicenseValid) {
        const handleLicenseSuccess = () => {
            setLicenseValid(true);
            handleRefresh().catch(() => {});
        };
        return <LicenseUpload licenseUploadUrl={licenseUploadUrl} onSuccess={handleLicenseSuccess} />;
    }

    const handlePurgeSuccess = () => {
        setSize(0);
    };

    return (
        <div className="widget-booster-content">
            {hint && <p className="widget-booster-hint">{hint}</p>}
            <PurgePanel
                project={project}
                size={size}
                serviceUrl={serviceUrl}
                onPurgeSuccess={handlePurgeSuccess}
                onRefresh={handleRefresh}
            />
            <CachedPathsTable paths={paths} pathStatsUrl={pathStatsUrl} />
        </div>
    );
};

App.displayName = 'BoosterApp';