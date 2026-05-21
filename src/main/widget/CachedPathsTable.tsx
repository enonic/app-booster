import {useState} from 'react';
import {fetchPathDetails, type PathStatsParam} from './api';

export type CachedPath = {
    key: string;
    docCount: number;
};

export type CachedPathsTableProps = {
    paths: CachedPath[];
    pathStatsUrl: string;
};

export const CachedPathsTable = ({paths, pathStatsUrl}: CachedPathsTableProps) => {
    if (paths.length === 0) {
        return null;
    }

    return (
        <table className="widget-booster-commonly-cached-paths">
            <caption>Commonly Cached Paths</caption>
            <thead>
                <tr>
                    <th>Count</th>
                    <th>Path</th>
                </tr>
            </thead>
            <tbody>
                {paths.map(p => (
                    <PathRow key={p.key} path={p} pathStatsUrl={pathStatsUrl} />
                ))}
            </tbody>
        </table>
    );
};

CachedPathsTable.displayName = 'CachedPathsTable';

type PathRowProps = {
    path: CachedPath;
    pathStatsUrl: string;
};

type DetailsState =
    | {status: 'idle'}
    | {status: 'loading'}
    | {status: 'loaded'; params: PathStatsParam[]}
    | {status: 'error'};

const PathRow = ({path, pathStatsUrl}: PathRowProps) => {
    const [details, setDetails] = useState<DetailsState>({status: 'idle'});

    const loadDetailsOnce = async () => {
        if (details.status !== 'idle') {
            return;
        }
        setDetails({status: 'loading'});
        try {
            const params = await fetchPathDetails(pathStatsUrl, path.key);
            setDetails({status: 'loaded', params});
        } catch (e) {
            console.error('Error fetching details:', e);
            setDetails({status: 'error'});
        }
    };

    return (
        <tr>
            <td>{path.docCount}</td>
            <td>
                <details onToggle={loadDetailsOnce}>
                    <summary>{path.key}</summary>
                    <PathDetails details={details} />
                </details>
            </td>
        </tr>
    );
};

PathRow.displayName = 'PathRow';

const PathDetails = ({details}: {details: DetailsState}) => {
    if (details.status === 'idle' || details.status === 'loading') {
        return null;
    }
    if (details.status === 'error') {
        return <p className="no-q-params">(failed to load details)</p>;
    }
    if (details.params.length === 0) {
        return <p className="no-q-params">(no querystring params in cache)</p>;
    }
    return (
        <table>
            <caption>Cached querystring params</caption>
            <thead>
                <tr>
                    <th>Param</th>
                    <th>Unique value count</th>
                </tr>
            </thead>
            <tbody>
                {details.params.map(p => (
                    <tr key={p.paramName}>
                        <td>{p.paramName}</td>
                        <td>{p.uniqueValuesCount}</td>
                    </tr>
                ))}
            </tbody>
        </table>
    );
};