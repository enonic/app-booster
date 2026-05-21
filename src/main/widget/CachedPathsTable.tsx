import {useState} from 'react';
import {fetchPathDetails} from './api';

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

const PathRow = ({path, pathStatsUrl}: PathRowProps) => {
    const [detailsHtml, setDetailsHtml] = useState<string | null>(null);

    const loadDetailsOnce = async () => {
        if (detailsHtml !== null) {
            return;
        }
        try {
            const html = await fetchPathDetails(pathStatsUrl, path.key);
            setDetailsHtml(html);
        } catch (e) {
            console.error('Error fetching details:', e);
        }
    };

    return (
        <tr>
            <td>{path.docCount}</td>
            <td>
                <details onToggle={loadDetailsOnce}>
                    <summary>{path.key}</summary>
                    {detailsHtml !== null && (
                        <div
                            className="widget-booster-detail-target"
                            // eslint-disable-next-line react/no-danger
                            dangerouslySetInnerHTML={{__html: detailsHtml}}
                        />
                    )}
                </details>
            </td>
        </tr>
    );
};

PathRow.displayName = 'PathRow';
