import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { PointerEvent as ReactPointerEvent } from "react";
import * as Tone from "tone";
import { unlockAudio } from "../../../audio/unlockAudio";
import styles from "./styles/Piano.module.css";

const NOTE_NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"] as const;
const BLACK_SIZE_RATIO = 0.62;

/** Окно двойного тапа для «залипания» ноты. */
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
    /** Ноты, которые сейчас звучат. Единственный источник истины для UI. */
    const [pressedNotes, setPressedNotes] = useState<ReadonlySet<string>>(() => new Set());

    /** Залипшая нота (двойной тап). Живёт отдельно от pointer-состояния. */
    const [latchedNote, setLatchedNote] = useState<string | null>(null);

    const synthRef = useRef<Tone.PolySynth | null>(null);

    /** pointerId -> нота, которую этот палец/кнопка держит. */
    const heldPointersRef = useRef<Map<number, string>>(new Map());

    /** Реально звучащие ноты — чтобы не дублировать triggerAttack. */
    const soundingRef = useRef<Set<string>>(new Set());

    const latchedRef = useRef<string | null>(null);
    const lastTapRef = useRef<{ note: string; time: number } | null>(null);

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

    /**
     * Лениво создаёт синтезатор. Раньше его вызывал attack(); после
     * переписывания на sync() вызов потерялся, synthRef оставался null и
     * звука не было вообще. Теперь здесь единственная точка создания.
     */
    const getSynth = useCallback(() => {
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
    }, []);

    /**
     * Приводит звук и подсветку в соответствие с желаемым состоянием.
     * Единственная точка, где что-либо звучит или перестаёт звучать, —
     * поэтому она идемпотентна и её можно вызывать откуда угодно.
     *
     * Желаемое = все запиннутые указателями ноты + залипшая нота.
     * Подсветка `isPressed` при этом показывает только запиннутые,
     * чтобы залипшая клавиша выглядела отдельно.
     */
    const sync = useCallback(() => {
        const desired = new Set<string>(heldPointersRef.current.values());
        if (latchedRef.current) desired.add(latchedRef.current);

        // Синтезатор создаём лениво здесь — до первого звука граф не нужен.
        const synth = desired.size > 0 ? getSynth() : synthRef.current;
        const audioReady = synth !== null && Tone.getContext().state === "running";

        if (audioReady && synth) {
            for (const note of Array.from(soundingRef.current)) {
                if (desired.has(note)) continue;
                synth.triggerRelease(note);
                soundingRef.current.delete(note);
            }

            for (const note of desired) {
                if (soundingRef.current.has(note)) continue;
                synth.triggerAttack(note);
                soundingRef.current.add(note);
            }
        }

        setPressedNotes(new Set(heldPointersRef.current.values()));
    }, [getSynth]);

    /**
     * Синхронно стартует AudioContext внутри user gesture, затем синхронизирует.
     *
     * `shortTapNote` — нота, которую тапнули, но отпустили до того, как контекст
     * успел разморозиться. Без этого первый тап в жизни приложения уходил в
     * тишину: sync() видел, что нота уже не удерживается, и играть было нечего.
     */
    const ensureAudio = useCallback(
        (shortTapNote?: string) => {
            void unlockAudio().then(() => {
                const synth = getSynth();
                const ready = synth !== null && Tone.getContext().state === "running";

                if (ready && shortTapNote) {
                    const stillHeld = Array.from(heldPointersRef.current.values()).includes(
                        shortTapNote,
                    );
                    const stillLatched = latchedRef.current === shortTapNote;

                    if (!stillHeld && !stillLatched) {
                        synth.triggerAttackRelease(shortTapNote, 0.18);
                    }
                }

                sync();
            });
        },
        [getSynth, sync],
    );

    const setLatch = useCallback(
        (note: string | null) => {
            if (latchedRef.current === note) return;

            latchedRef.current = note;
            setLatchedNote(note);
            sync();
        },
        [sync],
    );

    const handlePointerDown = (
        event: ReactPointerEvent<HTMLButtonElement>,
        noteToPlay: string,
    ) => {
        if (event.pointerType === "mouse" && event.button !== 0) return;

        // На тач-устройствах не мешаем вертикальной прокрутке клавиатуры
        if (event.pointerType === "mouse") event.preventDefault();

        const now = performance.now();
        const latched = latchedRef.current;

        // Эту ноту уже держит другой палец — это мультитач по одной клавише,
        // а не двойной тап. Просто регистрируем ещё один указатель.
        const alreadyHeld = Array.from(heldPointersRef.current.values()).includes(noteToPlay);
        if (alreadyHeld) {
            heldPointersRef.current.set(event.pointerId, noteToPlay);
            ensureAudio(noteToPlay);
            return;
        }

        // Тап по самой залипшей клавише — снимаем её (toggle).
        if (latched === noteToPlay) {
            lastTapRef.current = null;
            setLatch(null);
            return;
        }

        // Тап по любой другой клавише сначала сбрасывает залипшую.
        // lastTapRef не трогаем, чтобы двойной тап по новой клавише сработал.
        if (latched) setLatch(null);

        const last = lastTapRef.current;
        const isDoubleTap = last !== null && last.note === noteToPlay && now - last.time <= DOUBLE_TAP_MS;

        if (isDoubleTap) {
            // Второй тап подряд — залипаем и НЕ регистрируем указатель:
            // pointerup тогда ничего не отпустит, нота держится до внешнего тапа.
            lastTapRef.current = null;
            setLatch(noteToPlay);
            ensureAudio(noteToPlay);
            return;
        }

        lastTapRef.current = { note: noteToPlay, time: now };

        heldPointersRef.current.set(event.pointerId, noteToPlay);
        ensureAudio(noteToPlay);
    };

    useEffect(() => {
        const handlePointerUp = (event: PointerEvent) => {
            if (!heldPointersRef.current.has(event.pointerId)) return;

            heldPointersRef.current.delete(event.pointerId);
            sync();
        };

        /** Снимаем всё, включая залипшую ноту — чтобы не было зависших звуков. */
        const silenceAll = () => {
            if (heldPointersRef.current.size === 0 && latchedRef.current === null) return;

            heldPointersRef.current.clear();
            latchedRef.current = null;
            lastTapRef.current = null;

            setLatchedNote(null);
            sync();

            // Страховка: если контекст не был готов на момент sync().
            for (const note of Array.from(soundingRef.current)) {
                synthRef.current?.triggerRelease(note);
            }
            soundingRef.current.clear();
        };

        const handleVisibilityChange = () => {
            if (document.hidden) silenceAll();
        };

        /**
         * Тап вне клавиш снимает залипшую ноту.
         * Слушатель на window в bubble-фазе, поэтому React-обработчик клавиши
         * успевает отработать первым — здесь мы просто проверяем, что тап не по клавише.
         */
        const handleDocumentPointerDown = (event: PointerEvent) => {
            if (latchedRef.current === null) return;

            const target = event.target as HTMLElement | null;
            if (target?.closest("[data-piano-key]")) return;

            lastTapRef.current = null;
            latchedRef.current = null;
            setLatchedNote(null);
            sync();
        };

        window.addEventListener("pointerup", handlePointerUp);
        window.addEventListener("pointercancel", handlePointerUp);
        window.addEventListener("pointerdown", handleDocumentPointerDown);
        window.addEventListener("blur", silenceAll);
        document.addEventListener("visibilitychange", handleVisibilityChange);

        return () => {
            window.removeEventListener("pointerup", handlePointerUp);
            window.removeEventListener("pointercancel", handlePointerUp);
            window.removeEventListener("pointerdown", handleDocumentPointerDown);
            window.removeEventListener("blur", silenceAll);
            document.removeEventListener("visibilitychange", handleVisibilityChange);

            heldPointersRef.current.clear();
            soundingRef.current.clear();
            latchedRef.current = null;

            const synth = synthRef.current;
            synthRef.current = null;
            synth?.releaseAll();
            synth?.dispose();
        };
    }, [sync]);

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
        <div
            className={styles.piano}
            ref={viewportRef}
            role="group"
            aria-label="Пианино"
        >
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
                                aria-pressed={pressedNotes.has(key.note) || latchedNote === key.note}
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
                        aria-pressed={pressedNotes.has(key.note) || latchedNote === key.note}
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