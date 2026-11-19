import Piano from "./piano";
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
    const hasSignal = pitch !== null && note !== null;
    const rangeLabel = `C${KEYBOARD_START_OCTAVE}–C${KEYBOARD_START_OCTAVE + KEYBOARD_OCTAVES}`;

    return (
        <main className={styles.checker}>
            <header className={styles.header}>
                <div>
                    <p className={styles.eyebrow}>Pitch Checker</p>
                    <h1 className={styles.title}>Голос → нота</h1>
                </div>

                <button
                    type="button"
                    className={`${styles.micButton}${isActive ? ` ${styles.micButtonActive}` : ""}`}
                    onClick={toggleMic}
                >
                    <span className={styles.micDot} aria-hidden="true" />
                    {isActive ? "Микрофон включён" : "Включить микрофон"}
                </button>
            </header>

            <div className={styles.body}>
                <section className={styles.keyboard}>
                    <div className={styles.keyboardHead}>
                        <span className={styles.eyebrow}>Клавиатура</span>
                        <span className={styles.keyboardRange}>{rangeLabel}</span>
                    </div>

                    <Piano
                        startOctave={KEYBOARD_START_OCTAVE}
                        octaves={KEYBOARD_OCTAVES}
                        detectedNote={note}
                    />

                    <p className={styles.caption}>
                        Спойте ноту — клавиатура сама прокрутится к нужной клавише.
                    </p>
                </section>

                <section
                    className={`${styles.readoutPanel}${hasSignal ? ` ${styles.readoutHasSignal}` : ""}`}
                >
                    <div className={styles.readoutGlow} aria-hidden="true" />

                    <div className={styles.readout}>
                        <p className={styles.readoutLabel}>
                            {isActive ? "Сейчас слышно" : "Микрофон выключен"}
                        </p>

                        <p
                            className={`${styles.readoutNote}${note ? "" : ` ${styles.readoutNoteIdle}`}`}
                        >
                            {note ?? "—"}
                        </p>

                        <p className={styles.readoutHz}>
                            {pitch !== null ? Math.round(pitch) : "—"}
                            <span>Hz</span>
                        </p>

                        <p
                            className={`${styles.readoutState}${hasSignal ? ` ${styles.readoutStateLive}` : ""}`}
                        >
                            {!isActive
                                ? "Включите микрофон"
                                : hasSignal
                                    ? "Нота поймана"
                                    : "Слушаю…"}
                        </p>
                    </div>
                </section>
            </div>
        </main>
    );
};

export default CheckerHZ;
