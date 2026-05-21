import {Button, Dialog} from '@enonic/ui';
import {useState} from 'react';
import {
    TASK_STATE_FAILED,
    TASK_STATE_FINISHED,
    startPurge,
    waitForTaskCompletion,
} from './api';

const RELOAD_DELAY_MS = 3000;

const reloadWidget = () => {
    window.dispatchEvent(new CustomEvent('ReloadActiveWidgetEvent'));
};

type NotificationKind = 'pending' | 'success' | 'failure';

type Notification = {
    kind: NotificationKind;
    message: string;
};

export type PurgePanelProps = {
    project: string;
    size: number;
    serviceUrl: string;
};

export const PurgePanel = ({project, size, serviceUrl}: PurgePanelProps) => {
    const [open, setOpen] = useState(false);
    const [busy, setBusy] = useState(false);
    const [notification, setNotification] = useState<Notification | null>(null);

    const disabled = size === 0;

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
                reloadWidget();
            }, RELOAD_DELAY_MS);
        }
    };

    return (
        <>
            <Button
                variant="filled"
                disabled={disabled || busy}
                onClick={() => setOpen(true)}
                label={disabled ? 'No cache to purge' : `Purge cache (${size})`}
            />
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
