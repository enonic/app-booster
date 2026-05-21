import {Button, Dialog, FilledCircleCheck, FilledCircleX, IconButton, Tooltip} from '@enonic/ui';
import {Loader2, RefreshCw} from 'lucide-react';
import {useState} from 'react';

export type PurgeResult = {
    kind: 'success' | 'failure';
    message: string;
};

export type PurgePanelProps = {
    project: string;
    size: number;
    busy: boolean;
    lastResult: PurgeResult | null;
    onPurge: () => Promise<void>;
    onRefresh: () => Promise<void>;
};

export const PurgePanel = ({project, size, busy, lastResult, onPurge, onRefresh}: PurgePanelProps) => {
    const [open, setOpen] = useState(false);
    const [refreshing, setRefreshing] = useState(false);

    const disabled = size === 0;

    const handleRefresh = async () => {
        setRefreshing(true);
        try {
            await onRefresh();
        } catch (e) {
            console.error('Failed to refresh:', e);
        } finally {
            setRefreshing(false);
        }
    };

    const confirmPurge = async () => {
        setOpen(false);
        await onPurge();
    };

    return (
        <>
            <div className="widget-booster-actions">
                <div className="widget-booster-purge-group">
                    <Button
                        variant="filled"
                        disabled={disabled || busy}
                        onClick={() => setOpen(true)}
                        startIcon={busy ? Loader2 : undefined}
                        startIconClassName={busy ? 'animate-spin' : undefined}
                        label={disabled ? 'No cache to purge' : `Purge cache (${size})`}
                    />
                    {!busy && lastResult && (
                        <Tooltip value={lastResult.message}>
                            {lastResult.kind === 'success'
                                ? <FilledCircleCheck className="widget-booster-result-icon widget-booster-result-icon--success" />
                                : <FilledCircleX className="widget-booster-result-icon widget-booster-result-icon--failure" />}
                        </Tooltip>
                    )}
                </div>
                <IconButton
                    icon={RefreshCw}
                    variant="text"
                    disabled={refreshing}
                    onClick={handleRefresh}
                    aria-label="Refresh"
                    title="Refresh"
                />
            </div>
            <Dialog open={open} onOpenChange={setOpen}>
                <Dialog.Portal>
                    <Dialog.Overlay />
                    <Dialog.Content>
                        <Dialog.DefaultHeader title="Confirmation" withClose />
                        <Dialog.Body>
                            <p>
                                Purge cache for all content in project &quot;<b>{project}</b>&quot;?
                            </p>
                        </Dialog.Body>
                        <Dialog.Footer>
                            <Button
                                variant="outline"
                                onClick={() => setOpen(false)}
                                label="No"
                            />
                            <Button variant="filled" onClick={confirmPurge} label="Yes" />
                        </Dialog.Footer>
                    </Dialog.Content>
                </Dialog.Portal>
            </Dialog>
        </>
    );
};

PurgePanel.displayName = 'PurgePanel';
