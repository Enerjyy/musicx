import Piano from "./piano";

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
        <main className="checker">
            <header className="checker__header">
                <div>
                    <p className="checker__eyebrow">Pitch Checker</p>
                    <h1 className="checker__title">Голос → нота</h1>
                </div>

                <button
                    type="button"
                    className={`mic-button${isActive ? " is-active" : ""}`}
                    onClick={toggleMic}
                >
                    <span className="mic-button__dot" aria-hidden="true" />
                    {isActive ? "Микрофон включён" : "Включить микрофон"}
                </button>
            </header>

            <div className="checker__body">
                <section className="checker__keyboard">
                    <div className="keyboard-head">
                        <span className="checker__eyebrow">Клавиатура</span>
                        <span className="keyboard-range">{rangeLabel}</span>
                    </div>

                    <Piano
                        startOctave={KEYBOARD_START_OCTAVE}
                        octaves={KEYBOARD_OCTAVES}
                        detectedNote={note}
                    />

                    <p className="checker__caption">
                        Спойте ноту — клавиатура сама прокрутится к нужной клавише.
                    </p>
                </section>

                <section className={`checker__readout${hasSignal ? " has-signal" : ""}`}>
                    <div className="readout__glow" aria-hidden="true" />

                    <div className="readout">
                        <p className="readout__label">
                            {isActive ? "Сейчас слышно" : "Микрофон выключен"}
                        </p>

                        <p className={`readout__note${note ? "" : " is-idle"}`}>
                            {note ?? "—"}
                        </p>

                        <p className="readout__hz">
                            {pitch !== null ? Math.round(pitch) : "—"}
                            <span>Hz</span>
                        </p>

                        <p className={`readout__state${hasSignal ? " is-live" : ""}`}>
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
