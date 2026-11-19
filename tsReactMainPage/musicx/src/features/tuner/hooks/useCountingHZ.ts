import * as Tone from "tone";
import { YIN } from "pitchfinder";
import { useCallback, useEffect, useRef, useState } from "react";

const MIN_FREQUENCY = 65.41; // C2 (минимальная нота, совпадающая с клавиатурой)
const MAX_FREQUENCY = 1800; // ~A6
const UI_REFRESH_MS = 70;
const SMOOTHING_SIZE = 5;
const ANALYSER_SIZE = 4096;
// Сколько подряд пустых кадров держим последнюю ноту, прежде чем погасить табло.
// 4 × 70 мс ≈ 280 мс — сглаживает «дрожание» сигнала на iPhone.
const SIGNAL_LOST_FRAMES = 4;
// Ниже этого уровня (уже после MIC_GAIN) считаем, что тишина/шум, а не голос.
// Понижено, чтобы слабые низкие ноты с микрофона на телефоне проходили порог.
const MIN_RMS = 0.0003;
// Усиление сигнала с микрофона. Низкие ноты на телефоне приходят заметно тише
// (завал АЧХ микрофона + меньше энергии в фундаментальной гармонике) и без
// усиления не проходят порог MIN_RMS — поэтому раньше «не работало» ниже ~430 Гц.
// Увеличено, чтобы сенситизировать тихие низкие ноты.
const MIC_GAIN = 32;
// YIN из pitchfinder зависит от амплитуды: тихий сигнал он разбирает неправильно.
// Поэтому перед детекцией приводим пик буфера к этому значению.
const NORMALIZE_PEAK = 1;

export const useCountingHZ = () => {
    const [pitch, setPitch] = useState<number | null>(null);
    const [note, setNote] = useState<string | null>(null);
    const [isActive, setIsActive] = useState<boolean>(false);

    const streamRef = useRef<MediaStream | null>(null);
    const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
    const gainRef = useRef<GainNode | null>(null);
    const analyserRef = useRef<AnalyserNode | null>(null);
    const bufferRef = useRef<Float32Array<ArrayBuffer> | null>(null);

    const requestRef = useRef<number | null>(null);
    const activeRef = useRef(false);
    const startingRef = useRef(false);
    const aliveRef = useRef(true);

    const lastUpdateRef = useRef(0);
    const recentFreqsRef = useRef<number[]>([]);
    const missedFramesRef = useRef(0);

    const teardownAudio = useCallback((resetState: boolean) => {
        activeRef.current = false;

        if (requestRef.current !== null) {
            cancelAnimationFrame(requestRef.current);
            requestRef.current = null;
        }

        sourceRef.current?.disconnect();
        sourceRef.current = null;

        gainRef.current?.disconnect();
        gainRef.current = null;

        streamRef.current?.getTracks().forEach((track) => track.stop());
        streamRef.current = null;

        analyserRef.current = null;
        bufferRef.current = null;
        recentFreqsRef.current = [];
        missedFramesRef.current = 0;

        if (resetState) {
            setIsActive(false);
            setPitch(null);
            setNote(null);
        }
    }, []);

    const toggleMic = useCallback(async () => {
        if (startingRef.current) return;

        // Выключение
        if (activeRef.current) {
            teardownAudio(true);
            return;
        }

        startingRef.current = true;

        let stream: MediaStream | null = null;

        try {
            // На iOS Safari запись и воспроизведение одновременно надо разрешить явно.
            try {
                const audioSession = (
                    navigator as Navigator & {
                        audioSession?: { type: string };
                    }
                ).audioSession;

                if (audioSession) audioSession.type = "play-and-record";
            } catch {
                // Старые версии Safari — просто игнорируем
            }

            const getStream = async () => {
                try {
                    return await navigator.mediaDevices.getUserMedia({
                        audio: {
                            echoCancellation: false,
                            noiseSuppression: false,
                            autoGainControl: false,
                        },
                    });
                } catch (error) {
                    // Некоторые браузеры/устройства не принимают такие constraints.
                    const name = error instanceof DOMException ? error.name : "";

                    if (
                        name === "OverconstrainedError" ||
                        name === "NotSupportedError" ||
                        name === "TypeError"
                    ) {
                        return navigator.mediaDevices.getUserMedia({ audio: true });
                    }

                    throw error;
                }
            };

            // Оба вызова стартуют синхронно из обработчика клика,
            // чтобы iOS не потерял user gesture после await.
            const tonePromise = Tone.start();
            stream = await getStream();
            await tonePromise;

            if (!aliveRef.current) {
                stream.getTracks().forEach((track) => track.stop());
                return;
            }

            // Пока показывался системный запрос доступа, AudioContext мог уснуть.
            if (Tone.getContext().state !== "running") {
                await Tone.start();
            }

            const context = Tone.getContext();
            const source = context.createMediaStreamSource(stream);
            const gainNode = context.createGain();
            const analyser = context.createAnalyser();

            // Усиливаем вход, чтобы слабые низкие ноты проходили порог MIN_RMS.
            gainNode.gain.value = MIC_GAIN;
            analyser.fftSize = ANALYSER_SIZE;
            analyser.smoothingTimeConstant = 0;

            source.connect(gainNode);
            gainNode.connect(analyser);

            streamRef.current = stream;
            sourceRef.current = source;
            gainRef.current = gainNode;
            analyserRef.current = analyser;
            bufferRef.current = new Float32Array(analyser.fftSize);
            recentFreqsRef.current = [];
            missedFramesRef.current = 0;
            lastUpdateRef.current = 0;
            activeRef.current = true;
            setIsActive(true);

            const detectPitch = YIN({
                sampleRate: context.sampleRate,
                threshold: 0.15,
            });

            const analyze = () => {
                if (!activeRef.current) return;

                const analyserNode = analyserRef.current;
                const buffer = bufferRef.current;
                if (!analyserNode || !buffer) return;

                analyserNode.getFloatTimeDomainData(buffer);

                const now = performance.now();

                if (now - lastUpdateRef.current >= UI_REFRESH_MS) {
                    lastUpdateRef.current = now;

                    let sumSquares = 0;
                    let peak = 0;

                    for (let index = 0; index < buffer.length; index += 1) {
                        const value = buffer[index];
                        sumSquares += value * value;

                        const magnitude = Math.abs(value);
                        if (magnitude > peak) peak = magnitude;
                    }

                    const rms = Math.sqrt(sumSquares / buffer.length);
                    const hasLevel = rms >= MIN_RMS && peak > 0;

                    // Нормализация убирает зависимость YIN от громкости:
                    // тихое пение на iPhone определяется так же, как громкое.
                    if (hasLevel) {
                        const gain = NORMALIZE_PEAK / peak;

                        for (let index = 0; index < buffer.length; index += 1) {
                            buffer[index] *= gain;
                        }
                    }

                    const frequency = hasLevel ? detectPitch(buffer) : null;

                    if (
                        frequency !== null &&
                        frequency >= MIN_FREQUENCY &&
                        frequency <= MAX_FREQUENCY
                    ) {
                        missedFramesRef.current = 0;

                        const window = recentFreqsRef.current;
                        window.push(frequency);
                        if (window.length > SMOOTHING_SIZE) window.shift();

                        const sorted = [...window].sort((a, b) => a - b);
                        const median = sorted[Math.floor(sorted.length / 2)];

                        const nextPitch = Math.round(median);
                        const nextNote = Tone.Frequency(median).toNote();

                        setPitch((prev) => (prev === nextPitch ? prev : nextPitch));
                        setNote((prev) => (prev === nextNote ? prev : nextNote));
                    } else {
                        missedFramesRef.current += 1;

                        // Короткие провалы сигнала не гасим сразу — иначе на телефоне
                        // нота мигает при каждом вдохе или колебании уровня.
                        if (missedFramesRef.current >= SIGNAL_LOST_FRAMES) {
                            recentFreqsRef.current = [];
                            setPitch((prev) => (prev === null ? prev : null));
                            setNote((prev) => (prev === null ? prev : null));
                        }
                    }
                }

                requestRef.current = requestAnimationFrame(analyze);
            };

            analyze();
        } catch (error) {
            stream?.getTracks().forEach((track) => track.stop());
            console.error("Не удалось включить микрофон:", error);
            teardownAudio(true);
        } finally {
            startingRef.current = false;
        }
    }, [teardownAudio]);

    useEffect(() => {
        aliveRef.current = true;

        return () => {
            aliveRef.current = false;
            teardownAudio(false);
        };
    }, [teardownAudio]);

    return {
        note,
        pitch,
        isActive,
        toggleMic,
    };
};
