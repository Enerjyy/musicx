import Piano from "./Piano.tsx";
import styles from "./styles/CheckerHZ.module.css";

interface CheckerHZProps {
    pitch: number | null;
    note: string | null;
    isActive: boolean;
    toggleMic: () => void;
}

const KEYBOARD_START_OCTAVE = 2;
const KEYBOARD_OCTAVES = 4;

const CheckerHZ = ({ pitch, note, isActive, toggleMic }: CheckerHZProps) => {
    return (
        <main className={styles['retro-checker']}>
            <section className={styles['retro-keyboard']}>
                <Piano
                    startOctave={KEYBOARD_START_OCTAVE}
                    octaves={KEYBOARD_OCTAVES}
                    detectedNote={note}
                />
            </section>

            <aside className={styles['retro-side']}>
                <button
                    type="button"
                    className={`${styles['retro-mic-button']} ${isActive ? styles['isOn'] : styles['isOff']}`}
                    aria-label={isActive ? "Выключить микрофон" : "Включить микрофон"}
                    aria-pressed={isActive}
                    onClick={toggleMic}
                >
                    <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.8"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        aria-hidden="true"
                    >
                        <rect x="9" y="2.5" width="6" height="11" rx="3" />
                        <path d="M5.5 10.5a6.5 6.5 0 0 0 13 0" />
                        <path d="M12 17v3.5" />
                        <path d="M8.5 20.5h7" />
                    </svg>
                </button>

                <div className={styles['retro-readout']}>
                    {note !== null && <p className={styles['retro-note']}>{note}</p>}

                    {pitch !== null && (
                        <p className={styles['retro-hz']}>
                            {Math.round(pitch)}
                            <span>Hz</span>
                        </p>
                    )}
                </div>
                
                <div className={styles['retro-tuning-display']}>
                    <div className={styles['retro-tuning-value']}>A = 440Hz</div>
                    <div className={styles['retro-tuning-status']}>{isActive ? 'MIC ACTIVE' : 'MIC INACTIVE'}</div>
                </div>
            </aside>
        </main>
    );
};

export default CheckerHZ;
