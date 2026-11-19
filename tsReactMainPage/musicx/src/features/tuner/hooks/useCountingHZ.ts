import * as Tone from "tone";
import { YIN } from "pitchfinder";
import { useCallback, useEffect, useRef, useState } from "react";

const MIN_FREQUENCY = 55; // A1
const MAX_FREQUENCY = 1800; // ~A6
const UI_REFRESH_MS = 70;
const SMOOTHING_SIZE = 5;
const ANALYSER_SIZE = 4096;

export const useCountingHZ = () => {
    const [pitch, setPitch] = useState<number | null>(null);
    const [note, setNote] = useState<string | null>(null);
    const [isActive, setIsActive] = useState<boolean>(false);

    const streamRef = useRef<MediaStream | null>(null);
    const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
    const analyserRef = useRef<AnalyserNode | null>(null);
    const bufferRef = useRef<Float32Array<ArrayBuffer> | null>(null);

    const requestRef = useRef<number | null>(null);
    const activeRef = useRef(false);
    const startingRef = useRef(false);
    const aliveRef = useRef(true);

    const lastUpdateRef = useRef(0);
    const recentFreqsRef = useRef<number[]>([]);

    const teardownAudio = useCallback((resetState: boolean) => {
        activeRef.current = false;

        if (requestRef.current !== null) {
            cancelAnimationFrame(requestRef.current);
            requestRef.current = null;
        }

        sourceRef.current?.disconnect();
        sourceRef.current = null;

        streamRef.current?.getTracks().forEach((track) => track.stop());
        streamRef.current = null;

        analyserRef.current = null;
        bufferRef.current = null;
        recentFreqsRef.current = [];

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

        try {
            await Tone.start();

            const stream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    echoCancellation: false,
                    noiseSuppression: false,
                    autoGainControl: false,
                },
            });

            if (!aliveRef.current) {
                stream.getTracks().forEach((track) => track.stop());
                return;
            }

            const context = Tone.getContext();
            const source = context.createMediaStreamSource(stream);
            const analyser = context.createAnalyser();

            analyser.fftSize = ANALYSER_SIZE;
            analyser.smoothingTimeConstant = 0;
            source.connect(analyser);

            streamRef.current = stream;
            sourceRef.current = source;
            analyserRef.current = analyser;
            bufferRef.current = new Float32Array(analyser.fftSize);
            recentFreqsRef.current = [];
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

                    const frequency = detectPitch(buffer);

                    if (
                        frequency !== null &&
                        frequency >= MIN_FREQUENCY &&
                        frequency <= MAX_FREQUENCY
                    ) {
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
                        recentFreqsRef.current = [];
                        setPitch((prev) => (prev === null ? prev : null));
                        setNote((prev) => (prev === null ? prev : null));
                    }
                }

                requestRef.current = requestAnimationFrame(analyze);
            };

            analyze();
        } catch (error) {
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
