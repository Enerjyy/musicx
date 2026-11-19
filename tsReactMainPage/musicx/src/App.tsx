import './App.css'
import MetronomePage from "./pages/MetronomePage.tsx";
import TunerPage from "./pages/TunerPage.tsx";
import { useState } from 'react';

const PAGES = {
    tuner: {label: 'Tuner', component: <TunerPage />},
    metronome: {label: 'Metronome', component: <MetronomePage />},
}

type PageKey = keyof typeof PAGES;

const PAGE_KEYS = Object.keys(PAGES) as PageKey[];

const App = () => {
    const [currentPage, setCurrentPage] = useState<PageKey>('tuner');

    return (
        <div className="app">
            <header className="app-header">
                <h1 className="app-title">musicx</h1>
                <p className="app-subtitle">tuner &amp; metronome</p>
            </header>

            <main className={`app-main app-main--${currentPage}`}>
                {PAGES[currentPage].component}
            </main>

            <nav className="app-nav" aria-label="Sections">
                <div className="app-nav-inner">
                    {PAGE_KEYS.map((key) => (
                        <button
                            key={key}
                            type="button"
                            onClick={() => setCurrentPage(key)}
                            aria-current={currentPage === key ? 'page' : undefined}
                            className={
                                'app-nav-item' + (currentPage === key ? ' app-nav-item--active' : '')
                            }
                        >
                            {PAGES[key].label}
                        </button>
                    ))}
                </div>
            </nav>
        </div>
    );
}

export default App