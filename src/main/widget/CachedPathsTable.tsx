import {useState} from 'react';
import {Separator, TreeList} from '@enonic/ui';
import {fetchPathDetails, type PathStatsParam} from './api';

export type CachedPath = {
    key: string;
    docCount: number;
};

export type CachedPathsTableProps = {
    paths: CachedPath[];
    pathStatsUrl: string;
};

type DetailsState =
    | {status: 'loading'}
    | {status: 'loaded'; params: PathStatsParam[]}
    | {status: 'error'};

export const CachedPathsTable = ({paths, pathStatsUrl}: CachedPathsTableProps) => {
    const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set());
    const [details, setDetails] = useState<Record<string, DetailsState>>({});

    if (paths.length === 0) {
        return null;
    }

    const toggle = (key: string) => {
        const next = new Set(expanded);
        if (next.has(key)) {
            next.delete(key);
            setExpanded(next);
            return;
        }
        next.add(key);
        setExpanded(next);
        if (details[key]) {
            return;
        }
        setDetails(d => ({...d, [key]: {status: 'loading'}}));
        fetchPathDetails(pathStatsUrl, key)
            .then(params => setDetails(d => ({...d, [key]: {status: 'loaded', params}})))
            .catch(() => setDetails(d => ({...d, [key]: {status: 'error'}})));
    };

    return (
        <>
            <Separator label="Commonly Cached Paths" decorative />
            <TreeList
                selectionMode="none"
                expanded={expanded}
                onExpandedChange={setExpanded}
                className="widget-booster-paths-tree"
            >
                <TreeList.Container>
                    {paths.map((p, i) => {
                        const isExpanded = expanded.has(p.key);
                        const detail = details[p.key];
                        return (
                            <PathNode
                                key={p.key}
                                path={p}
                                expanded={isExpanded}
                                detail={detail}
                                posinset={i + 1}
                                setsize={paths.length}
                                onToggle={() => toggle(p.key)}
                            />
                        );
                    })}
                </TreeList.Container>
            </TreeList>
        </>
    );
};

CachedPathsTable.displayName = 'CachedPathsTable';

type PathNodeProps = {
    path: CachedPath;
    expanded: boolean;
    detail: DetailsState | undefined;
    posinset: number;
    setsize: number;
    onToggle: () => void;
};

const PathNode = ({path, expanded, detail, posinset, setsize, onToggle}: PathNodeProps) => (
    <>
        <TreeList.Row
            id={path.key}
            level={1}
            hasChildren
            expanded={expanded}
            posinset={posinset}
            setsize={setsize}
        >
            <TreeList.RowLeft>
                <TreeList.RowExpandControl
                    rowId={path.key}
                    expanded={expanded}
                    hasChildren
                    onToggle={onToggle}
                />
            </TreeList.RowLeft>
            <TreeList.RowContent>
                <span className="widget-booster-path-count">{path.docCount}</span>
                <span className="widget-booster-path-key">{path.key}</span>
            </TreeList.RowContent>
        </TreeList.Row>
        {expanded && <PathDetails parentId={path.key} detail={detail} />}
    </>
);

type PathDetailsProps = {
    parentId: string;
    detail: DetailsState | undefined;
};

const PathDetails = ({parentId, detail}: PathDetailsProps) => (
    <>
        <TreeList.RowPlaceholder level={2}>Cached querystring params</TreeList.RowPlaceholder>
        <PathDetailsBody parentId={parentId} detail={detail} />
    </>
);

const PathDetailsBody = ({parentId, detail}: PathDetailsProps) => {
    if (!detail || detail.status === 'loading') {
        return <TreeList.RowLoading level={2}>Loading…</TreeList.RowLoading>;
    }
    if (detail.status === 'error') {
        return <TreeList.RowPlaceholder level={2}>(failed to load details)</TreeList.RowPlaceholder>;
    }
    if (detail.params.length === 0) {
        return <TreeList.RowPlaceholder level={2}>(none)</TreeList.RowPlaceholder>;
    }
    return (
        <>
            {detail.params.map((p, i) => (
                <TreeList.Row
                    key={`${parentId}::${p.paramName}`}
                    id={`${parentId}::${p.paramName}`}
                    level={2}
                    posinset={i + 1}
                    setsize={detail.params.length}
                >
                    <TreeList.RowLevelSpacer level={2} />
                    <TreeList.RowContent>
                        <span className="widget-booster-path-count">{p.uniqueValuesCount}</span>
                        <span className="widget-booster-path-key">{p.paramName}</span>
                    </TreeList.RowContent>
                </TreeList.Row>
            ))}
        </>
    );
};
