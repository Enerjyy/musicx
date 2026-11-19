import './App.css'
import MetronomePage from "./pages/MetronomePage.tsx";
import TunerPage from "./pages/TunerPage.tsx";
import { useState } from 'react';

const PAGES = {
    tuner: {label: 'Tuner', component: <TunerPage />},
    metronome: {label: 'Metronome', component: <MetronomePage />},
}

type PageKey = keyof typeof PAGES;

const App = () => {
    const [currentPage, setCurrentPage] = useState<PageKey>('tuner');
    return (
        <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>

        <div style={{ flex: 1, paddingBottom: '60px' }}>
                {PAGES[currentPage].component}
        </div>
        <footer style={{
                position: 'fixed',
                bottom: 0,
                left: 0,
                right: 0,
                display: 'flex',
                justifyContent: 'center',
                gap: '12px',
                padding: '12px',
                background: '#fff',
                borderTop: '1px solid #eaeaea'
        }}>
            {(Object.keys(PAGES) as PageKey[]).map((key) => (
                <button key={key} onClick={() => setCurrentPage(key)} style={{
                      fontWeight: currentPage === key ? 'bold' : 'normal',
                      padding: '8px 16px',
                      cursor: 'pointer'
                    }}> {PAGES[key].label}
                </button>
                ))}
        </footer>

    </div>);
}

export default App