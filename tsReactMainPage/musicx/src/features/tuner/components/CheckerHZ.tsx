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
        <main className={styles.checker}>
            <section className={styles.keyboard}>
                <Piano
                    startOctave={KEYBOARD_START_OCTAVE}
                    octaves={KEYBOARD_OCTAVES}
                    detectedNote={note}
                />
            </section>

            <aside className={styles.side}>
                <button
                    type="button"
                    className={`${styles.micButton} ${isActive ? styles.isOn : styles.isOff}`}
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

                <div className={styles.readout}>
                    {note !== null && <p className={styles.note}>{note}</p>}

                    {pitch !== null && (
                        <p className={styles.hz}>
                            {Math.round(pitch)}
                            <span>Hz</span>
                        </p>
                    )}
                </div>
            </aside>
        </main>
    );
};

export default CheckerHZ;