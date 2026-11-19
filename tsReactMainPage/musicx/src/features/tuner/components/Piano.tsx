import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { PointerEvent as ReactPointerEvent } from "react";
import * as Tone from "tone";
import { unlockAudio } from "../../../audio/unlockAudio";
import styles from "./styles/Piano.module.css";

const NOTE_NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"] as const;
const BLACK_SIZE_RATIO = 0.62;
const DOUBLE_TAP_MS = 320;

type KeyKind = "white" | "black";

type PianoKey = {
    note: string;
    kind: KeyKind;
    whiteIndex: number;
};

type BlackPianoKey = PianoKey & {
    topPct: number;
    heightPct: number;
};

type Keyboard = {
    whiteKeys: PianoKey[];
    blackKeys: BlackPianoKey[];
};

const buildKeyboard = (startOctave: number, octaveCount: number): Keyboard => {
    const ascendingKeys: PianoKey[] = [];
    let whiteIndex = 0;

    for (let octave = startOctave; octave < startOctave + octaveCount; octave += 1) {
        for (const name of NOTE_NAMES) {
            const note = `${name}${octave}`;

            if (name.includes("#")) {
                ascendingKeys.push({ note, kind: "black", whiteIndex: whiteIndex - 1 });
            } else {
                ascendingKeys.push({ note, kind: "white", whiteIndex });
                whiteIndex += 1;
            }
        }
    }

    // Закрывающая нота C следующей октавы
    ascendingKeys.push({
        note: `C${startOctave + octaveCount}`,
        kind: "white",
        whiteIndex,
    });

    const whiteCount = whiteIndex + 1;
    const rowHeightPct = 100 / whiteCount;
    const blackHeightPct = rowHeightPct * BLACK_SIZE_RATIO;

    // Сверху вниз — от высоких нот к низким
    const whiteKeys = ascendingKeys
        .filter((key) => key.kind === "white")
        .reverse();

    const blackKeys = ascendingKeys
        .filter((key) => key.kind === "black")
        .map((key) => ({
            ...key,
            topPct:
                100 -
                (key.whiteIndex + 1) * rowHeightPct -
                blackHeightPct / 2,
            heightPct: blackHeightPct,
        }))
        .sort((a, b) => a.topPct - b.topPct);

    return { whiteKeys, blackKeys };
};

interface PianoProps {
    startOctave?: number;
    octaves?: number;
    detectedNote?: string | null;
}

const Piano = ({
                   startOctave = 1,
                   octaves = 6,
                   detectedNote = null,
               }: PianoProps) => {
    const [pressedNotes, setPressedNotes] = useState<ReadonlySet<string>>(() => new Set());
    const heldPointersRef = useRef<Map<number, string>>(new Map());
    const synthRef = useRef<Tone.PolySynth | null>(null);

    // «Залипшая» нота: включается двойным тапом, снимается любым касанием
    // вне её. Держится отдельно от pointer-состояния, поэтому pointerup её
    // не отпускает.
    const [latchedNote, setLatchedNote] = useState<string | null>(null);
    const latchedRef = useRef<string | null>(null);
    const lastTapRef = useRef<{ note: string; time: number }>({ note: "", time: 0 });

    const viewportRef = useRef<HTMLDivElement | null>(null);
    const keyRefsRef = useRef<Map<string, HTMLButtonElement>>(new Map());
    const detectedNoteRef = useRef<string | null>(detectedNote);

    const { whiteKeys, blackKeys } = useMemo(
        () => buildKeyboard(startOctave, octaves),
        [startOctave, octaves],
    );

    const centerKey = (targetNote: string, behavior: ScrollBehavior) => {
        const viewport = viewportRef.current;
        const key = keyRefsRef.current.get(targetNote);
        if (!viewport || !key) return;

        const top = key.offsetTop + key.offsetHeight / 2 - viewport.clientHeight / 2;
        viewport.scrollTo({ top: Math.max(0, top), behavior });
    };

    // При открытии показываем середину — C4
    useLayoutEffect(() => {
        centerKey("C4", "auto");
    }, []);

    // Услышали ноту — плавно подводим клавиатуру к её клавише
    useEffect(() => {
        detectedNoteRef.current = detectedNote;
        if (!detectedNote) return;

        const behavior: ScrollBehavior = window.matchMedia("(prefers-reduced-motion: reduce)")
            .matches
            ? "auto"
            : "smooth";

        centerKey(detectedNote, behavior);
    }, [detectedNote]);

    // При изменении размера окна/повороте экрана возвращаем текущую ноту в центр
    useEffect(() => {
        const handleResize = () => centerKey(detectedNoteRef.current ?? "C4", "auto");

        window.addEventListener("resize", handleResize);
        return () => window.removeEventListener("resize", handleResize);
    }, []);

    const getSynth = () => {
        if (!synthRef.current) {
            const synth = new Tone.PolySynth(Tone.Synth, {
                oscillator: { type: "triangle" },
                envelope: {
                    attack: 0.006,
                    decay: 0.18,
                    sustain: 0.45,
                    release: 1.1,
                },
            });

            synth.volume.value = -9;
            synth.toDestination();
            synthRef.current = synth;
        }

        return synthRef.current;
    };

    // Резолвится, когда AudioContext разморожен. Первый вызов должен произойти
    // синхронно внутри user gesture, иначе браузер отклонит его — поэтому
    // запускаем прямо в обработчике, а не после await.
    const ensureAudio = () => unlockAudio();

    const attack = (noteToPlay: string) => {
        const synth = getSynth();

        // Если контекст ещё не запущен — стартуем и играем в любом случае.
        // Раньше здесь был ранний выход по !stillHeld, из-за чего короткий
        // тап (pointerup до разморозки контекста) не звучал вообще.
        void ensureAudio().then(() => {
            if (Tone.getContext().state !== "running") return;

            const stillHeld = Array.from(heldPointersRef.current.values()).includes(noteToPlay);
            if (stillHeld) {
                synth.triggerAttack(noteToPlay);
            } else {
                // Тап уже отпущен — играем короткий "щелчок", чтобы нажатие
                // всё равно было слышно.
                synth.triggerAttackRelease(noteToPlay, 0.18);
            }
        });
    };

    const releaseLatch = () => {
        const note = latchedRef.current;
        if (!note) return;

        latchedRef.current = null;
        setLatchedNote(null);
        synthRef.current?.triggerRelease(note);

        setPressedNotes((prev) => {
            if (!prev.has(note)) return prev;
            const next = new Set(prev);
            next.delete(note);
            return next;
        });
    };

    // Стабильная ссылка, чтобы useEffect с [] не захватывал устаревшее замыкание.
    const releaseLatchRef = useRef(releaseLatch);
    releaseLatchRef.current = releaseLatch;

    const handlePointerDown = (
        event: ReactPointerEvent<HTMLButtonElement>,
        noteToPlay: string,
    ) => {
        if (event.pointerType === "mouse" && event.button !== 0) return;

        // Любое касание вне «залипшей» клавиши снимает её. Это проверяется
        // до двойного тапа, чтобы тап по другой клавише сначала отпустил
        // предыдущую, а потом уже играл новую.
        const latched = latchedRef.current;
        if (latched && latched !== noteToPlay) {
            releaseLatch();
        }

        const now = Date.now();
        const last = lastTapRef.current;
        const isDoubleTap =
            last.note === noteToPlay && now - last.time <= DOUBLE_TAP_MS && latched !== noteToPlay;

        lastTapRef.current = { note: noteToPlay, time: isDoubleTap ? 0 : now };

        if (isDoubleTap) {
            // Залипаем: играем и держим до внешнего касания.
            latchedRef.current = noteToPlay;
            setLatchedNote(noteToPlay);

            setPressedNotes((prev) => {
                if (prev.has(noteToPlay)) return prev;
                const next = new Set(prev);
                next.add(noteToPlay);
                return next;
            });

            // Клавиша уже могла быть зажата первым тапом — тогда второй
            // simply пере-атакует, что даёт явный щелчок подтверждения.
            attack(noteToPlay);
            return;
        }

        // На тач-устройствах не мешаем вертикальной прокрутке клавиатуры
        if (event.pointerType === "mouse") event.preventDefault();

        heldPointersRef.current.set(event.pointerId, noteToPlay);

        setPressedNotes((prev) => {
            if (prev.has(noteToPlay)) return prev;
            const next = new Set(prev);
            next.add(noteToPlay);
            return next;
        });

        attack(noteToPlay);
    };

    useEffect(() => {
        const releaseNote = (noteToRelease: string) => {
            synthRef.current?.triggerRelease(noteToRelease);

            setPressedNotes((prev) => {
                if (!prev.has(noteToRelease)) return prev;
                const next = new Set(prev);
                next.delete(noteToRelease);
                return next;
            });
        };

        const handlePointerUp = (event: PointerEvent) => {
            const noteToRelease = heldPointersRef.current.get(event.pointerId);
            if (!noteToRelease) return;

            heldPointersRef.current.delete(event.pointerId);

            // Залипшая нота живёт отдельно от pointer-состояния: обычный
            // pointerup её не отпускает.
            if (latchedRef.current === noteToRelease) return;

            const stillHeld = Array.from(heldPointersRef.current.values()).includes(noteToRelease);
            if (!stillHeld) releaseNote(noteToRelease);
        };

        const silenceAll = () => {
            heldPointersRef.current.clear();

            // Снимаем всё, кроме залипшей ноты — она держится до касания
            // по другой клавише или по фону.
            const keep = latchedRef.current;

            setPressedNotes((prev) => {
                for (const note of prev) {
                    if (note !== keep) synthRef.current?.triggerRelease(note);
                }

                if (prev.size === 0) return prev;
                if (keep && prev.size === 1 && prev.has(keep)) return prev;

                const next = new Set<string>();
                if (keep) next.add(keep);
                return next;
            });
        };

        const handleVisibilityChange = () => {
            if (document.hidden) silenceAll();
        };

        // Тап по фону (вне клавиш) снимает залипшую ноту.
        const handleBackgroundPointerDown = (event: PointerEvent) => {
            if (!latchedRef.current) return;

            const target = event.target as HTMLElement | null;
            if (target?.closest("[data-piano-key]")) return;

            releaseLatchRef.current();
        };

        window.addEventListener("pointerup", handlePointerUp);
        window.addEventListener("pointercancel", handlePointerUp);
        window.addEventListener("pointerdown", handleBackgroundPointerDown);
        window.addEventListener("blur", silenceAll);
        document.addEventListener("visibilitychange", handleVisibilityChange);

        return () => {
            window.removeEventListener("pointerup", handlePointerUp);
            window.removeEventListener("pointercancel", handlePointerUp);
            window.removeEventListener("pointerdown", handleBackgroundPointerDown);
            window.removeEventListener("blur", silenceAll);
            document.removeEventListener("visibilitychange", handleVisibilityChange);

            heldPointersRef.current.clear();

            const synth = synthRef.current;
            synthRef.current = null;
            synth?.releaseAll();
            synth?.dispose();
        };
    }, []);

    const registerKey = (note: string) => (element: HTMLButtonElement | null) => {
        if (element) keyRefsRef.current.set(note, element);
        else keyRefsRef.current.delete(note);
    };

    const keyClassName = (key: PianoKey, isC: boolean) =>
        [
            styles.key,
            key.kind === "white" ? styles.white : styles.black,
            isC ? styles.isC : "",
            pressedNotes.has(key.note) ? styles.isPressed : "",
            latchedNote === key.note ? styles.isLatched : "",
            detectedNote === key.note ? styles.isDetected : "",
        ]
            .filter(Boolean)
            .join(" ");

    return (
        <div className={styles.piano} ref={viewportRef} role="group" aria-label="Пианино">
            <div className={styles.inner}>
                <div className={styles.whites}>
                    {whiteKeys.map((key) => {
                        const isC = key.note.startsWith("C") && key.note[1] !== "#";

                        return (
                            <button
                                key={key.note}
                                ref={registerKey(key.note)}
                                type="button"
                                data-piano-key={key.note}
                                className={keyClassName(key, isC)}
                                aria-label={`Нота ${key.note}`}
                                onPointerDown={(event) => handlePointerDown(event, key.note)}
                                onContextMenu={(event) => event.preventDefault()}
                            >
                                <span className={styles.label}>{key.note}</span>
                            </button>
                        );
                    })}
                </div>

                {blackKeys.map((key) => (
                    <button
                        key={key.note}
                        ref={registerKey(key.note)}
                        type="button"
                        data-piano-key={key.note}
                        className={keyClassName(key, false)}
                        aria-label={`Нота ${key.note}`}
                        style={{
                            top: `${key.topPct}%`,
                            height: `${key.heightPct}%`,
                        }}
                        onPointerDown={(event) => handlePointerDown(event, key.note)}
                        onContextMenu={(event) => event.preventDefault()}
                    >
                        <span className={styles.label}>{key.note}</span>
                    </button>
                ))}
            </div>
        </div>
    );
};

export default Piano;
