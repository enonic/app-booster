export const TASK_STATE_FINISHED = 'FINISHED';
export const TASK_STATE_FAILED = 'FAILED';

export type TaskState = typeof TASK_STATE_FINISHED | typeof TASK_STATE_FAILED;

type PurgeResponse = {
    taskId?: string;
};

type StatusResponse = {
    state?: TaskState;
};

type LicenseResponse = {
    licenseValid: boolean;
};

const postJson = async <T>(url: string, body: object): Promise<T> => {
    const res = await fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/json;charset=UTF-8'},
        body: JSON.stringify(body),
    });

    if (!res.ok) {
        throw new Error(`${res.status} ${res.statusText}`);
    }

    return res.json() as Promise<T>;
};

export const startPurge = (serviceUrl: string, project: string): Promise<PurgeResponse> =>
    postJson<PurgeResponse>(serviceUrl, {action: 'invalidate', data: {project}});

export const fetchTaskStatus = (serviceUrl: string, taskId: string): Promise<StatusResponse> =>
    postJson<StatusResponse>(serviceUrl, {action: 'status', data: {taskId}});

export const waitForTaskCompletion = async (
    serviceUrl: string,
    taskId: string,
    timeoutMs = 10_000,
): Promise<TaskState> => {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
        const res = await fetchTaskStatus(serviceUrl, taskId);
        if (res.state === TASK_STATE_FINISHED || res.state === TASK_STATE_FAILED) {
            return res.state;
        }
        await new Promise(resolve => setTimeout(resolve, 1000));
    }
    throw new Error('Task completion timeout');
};

export const uploadLicense = async (
    licenseUploadUrl: string,
    file: File,
): Promise<LicenseResponse> => {
    const formData = new FormData();
    formData.append('license', file);
    const res = await fetch(licenseUploadUrl, {
        method: 'POST',
        body: formData,
    });
    return res.json() as Promise<LicenseResponse>;
};

export const fetchPathDetails = async (pathStatsUrl: string, path: string): Promise<string> => {
    const url = `${pathStatsUrl}?path=${encodeURIComponent(path)}`;
    const res = await fetch(url);
    return res.text();
};
