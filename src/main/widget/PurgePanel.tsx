import {Button, Dialog, IconButton} from '@enonic/ui';
import {Loader2, RefreshCw} from 'lucide-react';
import {useState} from 'react';
import {
    TASK_STATE_FAILED,
    TASK_STATE_FINISHED,
    startPurge,
    waitForTaskCompletion,
} from './api';

const NOTIFICATION_DISMISS_MS = 3000;

type NotificationKind = 'pending' | 'success' | 'failure';

type Notification = {
    kind: NotificationKind;
    message: string;
};

export type PurgePanelProps = {
    project: string;
    size: number;
    serviceUrl: string;
    onPurgeSuccess: () => void;
    onRefresh: () => Promise<void>;
};

export const PurgePanel = ({project, size, serviceUrl, onPurgeSuccess, onRefresh}: PurgePanelProps) => {
    const [open, setOpen] = useState(false);
    const [busy, setBusy] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [notification, setNotification] = useState<Notification | null>(null);

    const disabled = size === 0;

    const handleRefresh = async () => {
        setRefreshing(true);
        try {
            await onRefresh();
        } catch (e) {
            const reason = e instanceof Error ? e.message : 'Unknown error';
            setNotification({kind: 'failure', message: `Failed to refresh: ${reason}`});
            setTimeout(() => setNotification(null), NOTIFICATION_DISMISS_MS);
        } finally {
            setRefreshing(false);
        }
    };

    const confirmPurge = async () => {
        setOpen(false);
        setBusy(true);
        setNotification({kind: 'pending', message: 'Initiated cache purge...'});

        try {
            const {taskId} = await startPurge(serviceUrl, project);
            if (!taskId) {
                throw new Error('No task started');
            }
            const state = await waitForTaskCompletion(serviceUrl, taskId);
            if (state === TASK_STATE_FINISHED) {
                setNotification({kind: 'success', message: 'Cache successfully purged'});
                onPurgeSuccess();
            } else if (state === TASK_STATE_FAILED) {
                setNotification({kind: 'failure', message: 'Failed to purge cache'});
            }
        } catch (e) {
            const reason = e instanceof Error ? e.message : 'Unknown error';
            setNotification({kind: 'failure', message: `Failed to purge: ${reason}`});
        } finally {
            setTimeout(() => {
                setBusy(false);
                setNotification(null);
            }, NOTIFICATION_DISMISS_MS);
        }
    };

    return (
        <>
            <div className="widget-booster-actions">
                <Button
                    variant="filled"
                    disabled={disabled || busy}
                    onClick={() => setOpen(true)}
                    startIcon={busy ? Loader2 : undefined}
                    startIconClassName={busy ? 'animate-spin' : undefined}
                    label={disabled ? 'No cache to purge' : `Purge cache (${size})`}
                />
                <IconButton
                    icon={RefreshCw}
                    variant="text"
                    disabled={busy || refreshing}
                    onClick={handleRefresh}
                    aria-label="Refresh"
                    title="Refresh"
                />
            </div>
            {notification && (
                <p
                    className={`widget-booster-action-response widget-booster-action-response--${notification.kind}`}
                >
                    {notification.message}
                </p>
            )}
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
