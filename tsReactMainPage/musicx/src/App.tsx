import './App.css'
import MetronomePage from "./pages/MetronomePage.tsx";
import TunerPage from "./pages/TunerPage.tsx";
import { useState, useEffect } from 'react';

const PAGES = {
    tuner: {label: 'Tuner', component: <TunerPage />},
    metronome: {label: 'Metronome', component: <MetronomePage />},
}

type PageKey = keyof typeof PAGES;
const PageKeys = Object.keys(PAGES) as PageKey[];

const App = () => {
    const [currentPage, setCurrentPage] = useState<PageKey>('tuner');
    const [isLoading, setIsLoading] = useState(true);
    const [isOnline, setIsOnline] = useState(true);
    
    useEffect(() => {
        const timer = setTimeout(() => {
            setIsLoading(false);
        }, 2000);
        
        return () => clearTimeout(timer);
    }, []);
    
    useEffect(() => {
        const handleOnline = () => setIsOnline(true);
        const handleOffline = () => setIsOnline(false);
        
        window.addEventListener('online', handleOnline);
        window.addEventListener('offline', handleOffline);
        
        return () => {
            window.removeEventListener('online', handleOnline);
            window.removeEventListener('offline', handleOffline);
        };
    }, []);
    
    if (isLoading) {
        return (
            <div className="loading-screen">
                <div className="loading-content">
                    <h1 className="loading-title">MUSICX</h1>
                    <div className="loading-bar">
                        <div className="loading-fill"></div>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="app-container">
            <div className="status-bar">
                <div className="status-indicator">
                    <div className={`status-dot ${!isOnline ? 'off' : ''}`}></div>
                    <span>{isOnline ? 'ONLINE' : 'OFFLINE'}</span>
                </div>
                <div className="status-indicator">
                    <span>BPM: 120</span>
                </div>
                <div className="status-indicator">
                    <span id="date-display">{new Date().toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}</span>
                </div>
            </div>
            
            <main>
                {PAGES[currentPage].component}
            </main>
            
            <nav className="retro-navbar">
                {PageKeys.map((key) => (
                    <button
                        key={key}
                        onClick={() => setCurrentPage(key)}
                        className={currentPage === key ? 'active' : ''}
                    >
                        {PAGES[key].label}
                    </button>
                ))}
            </nav>
        </div>
    );
}

export default App