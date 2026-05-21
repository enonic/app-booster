import {useRef, useState} from 'react';
import {Button} from '@enonic/ui';
import {uploadLicense} from './api';

const ERROR_VISIBLE_MS = 5000;

export type LicenseUploadProps = {
    licenseUploadUrl: string;
};

export const LicenseUpload = ({licenseUploadUrl}: LicenseUploadProps) => {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [showError, setShowError] = useState(false);

    const triggerFileChooser = () => fileInputRef.current?.click();

    const handleFileChange = async (event: Event) => {
        const target = event.currentTarget as HTMLInputElement;
        const file = target.files?.[0];
        if (!file) {
            return;
        }
        setShowError(false);
        try {
            const data = await uploadLicense(licenseUploadUrl, file);
            if (data.licenseValid) {
                window.dispatchEvent(new CustomEvent('ReloadActiveWidgetEvent'));
            } else {
                setShowError(true);
                setTimeout(() => setShowError(false), ERROR_VISIBLE_MS);
            }
        } catch {
            setShowError(true);
            setTimeout(() => setShowError(false), ERROR_VISIBLE_MS);
        } finally {
            target.value = '';
        }
    };

    return (
        <div className="widget-booster-license-invalid">
            <h6>Booster requires a valid Enonic license. Please contact your administrator</h6>
            <input
                ref={fileInputRef}
                type="file"
                accept=".lic"
                style={{display: 'none'}}
                onChange={handleFileChange}
            />
            <Button variant="filled" onClick={triggerFileChooser} label="Upload license" />
            {showError && (
                <p className="widget-booster-license-error">
                    The uploaded license is invalid or expired
                </p>
            )}
        </div>
    );
};

LicenseUpload.displayName = 'LicenseUpload';
