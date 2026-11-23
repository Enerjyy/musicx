import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { installAudioUnlock } from './audio/unlockAudio'
import { installSession } from './telegram/session'

// Unlock the AudioContext on the first user gesture anywhere in the app.
// Without this the piano/metronome stay silent until some other code path
// happens to call Tone.start() (e.g. enabling the microphone).
installAudioUnlock()

// Telegram login: обмениваем initData на токен и учим http.ts обновлять его
// на 401. В обычном браузере — no-op.
installSession()

createRoot(document.getElementById('root')!).render(
    <App />,
)