import {render} from 'preact';
import {App, type AppState} from './App';

import '@enonic/ui/style.css';
import './widget.css';

const ROOT_ID = 'widget-booster-root';
const STATE_ID = 'widget-booster-state';

const readState = (): AppState | undefined => {
    const stateScript = document.getElementById(STATE_ID);
    if (!stateScript?.textContent) {
        return undefined;
    }
    try {
        return JSON.parse(stateScript.textContent) as AppState;
    } catch {
        return undefined;
    }
};

const root = document.getElementById(ROOT_ID);
const state = readState();
if (root && state) {
    render(<App {...state} />, root);
}
