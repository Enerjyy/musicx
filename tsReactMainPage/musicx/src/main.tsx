import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { installAudioUnlock } from './audio/unlockAudio'

// Unlock the AudioContext on the first user gesture anywhere in the app.
// Without this the piano/metronome stay silent until something else happens
// to call Tone.start() (e.g. enabling the microphone).
installAudioUnlock()

createRoot(document.getElementById('root')!).render(
    <App />,
)