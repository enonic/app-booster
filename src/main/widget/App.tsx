import {useEffect, useState} from 'react';
import {LicenseUpload} from './LicenseUpload';
import {PurgePanel, type PurgeResult} from './PurgePanel';
import {CachedPathsTable, type CachedPath} from './CachedPathsTable';
import {
    TASK_STATE_FAILED,
    TASK_STATE_FINISHED,
    fetchRefreshState,
    pollTaskToCompletion,
    startPurge,
} from './api';

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
    runningTaskId?: string;
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
    const [runningTaskId, setRunningTaskId] = useState<string | undefined>(props.runningTaskId);
    const [lastResult, setLastResult] = useState<PurgeResult | null>(null);

    useEffect(() => {
        if (!runningTaskId) {
            return;
        }
        const abort = new AbortController();
        pollTaskToCompletion(serviceUrl, runningTaskId, undefined, abort.signal)
            .then(state => {
                if (state === TASK_STATE_FINISHED) {
                    setLastResult({kind: 'success', message: 'Cache successfully purged'});
                    setSize(0);
                } else if (state === TASK_STATE_FAILED) {
                    setLastResult({kind: 'failure', message: 'Failed to purge cache'});
                }
                setRunningTaskId(undefined);
            })
            .catch(e => {
                if (e instanceof Error && e.message === 'aborted') {
                    return;
                }
                const reason = e instanceof Error ? e.message : 'Unknown error';
                setLastResult({kind: 'failure', message: `Failed to purge: ${reason}`});
                setRunningTaskId(undefined);
            });
        return () => abort.abort();
    }, [runningTaskId, serviceUrl]);

    const handleRefresh = async () => {
        const next = await fetchRefreshState(refreshUrl);
        setSize(next.size);
        setPaths(next.commonlyCachedPaths);
        setHint(next.hint);
        if (next.runningTaskId) {
            setRunningTaskId(prev => prev ?? next.runningTaskId);
        }
    };

    const handleStartPurge = async () => {
        setLastResult(null);
        try {
            const {taskId} = await startPurge(serviceUrl, project);
            if (!taskId) {
                throw new Error('No task started');
            }
            setRunningTaskId(taskId);
        } catch (e) {
            const reason = e instanceof Error ? e.message : 'Unknown error';
            setLastResult({kind: 'failure', message: `Failed to purge: ${reason}`});
        }
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

    return (
        <div className="widget-booster-content">
            {hint && <p className="widget-booster-hint">{hint}</p>}
            <PurgePanel
                project={project}
                size={size}
                busy={!!runningTaskId}
                lastResult={lastResult}
                onPurge={handleStartPurge}
                onRefresh={handleRefresh}
            />
            <CachedPathsTable paths={paths} pathStatsUrl={pathStatsUrl} />
        </div>
    );
};

App.displayName = 'BoosterApp';
