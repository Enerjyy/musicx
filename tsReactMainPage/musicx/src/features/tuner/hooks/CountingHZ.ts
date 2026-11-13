import * as Tone from "tone";
import { YIN } from "pitchfinder";
import {useState, useRef, useEffect} from "react";


export const useCountingHZ = () => {
    const [pitch, setPitch] = useState<number | null>(null);
    const [note, setNote] = useState<string | null>(null);
    const [isActive, setIsActive] = useState<boolean>(false);

    const micRef = useRef<Tone.UserMedia | null>(null);
    const analyserRef = useRef<Tone.Analyser | null>(null);
    const requestRef = useRef<number | null>(null);

    const toggleMic = async () => {
        if (isActive) {
            if (requestRef.current) cancelAnimationFrame(requestRef.current);
            micRef.current?.close();
            micRef.current?.dispose();
            analyserRef.current?.dispose();
            setIsActive(false);
            setPitch(null);
            setNote(null);
            return;
        }

        try {
            await Tone.start();

            const mic = new Tone.UserMedia();
            await mic.open({
                audio: {
                    echoCancellation: false,
                    noiseSuppression: false,
                    autoGainControl: false,
                }
            } as any);

            const analyser = new Tone.Analyser("waveform", 8192);
            mic.connect(analyser);

            const gainNode = new Tone.Gain(10);
            mic.connect(gainNode);
            gainNode.connect(analyser);

            micRef.current = mic;
            analyserRef.current = analyser;
            setIsActive(true);

            const DetectPitch = YIN({sampleRate: Tone.context.sampleRate, threshold: 0.15});

            const analyze = () => {
                if (!analyserRef.current) return;



                const buffer = analyserRef.current.getValue() as Float32Array;
                const detectedFrequency = DetectPitch(buffer);

                if (detectedFrequency && detectedFrequency > 20 && detectedFrequency < 2000) {
                    const roundedFreq = Math.round(detectedFrequency);
                    setPitch(roundedFreq);

                    const mucialNote = Tone.Frequency(detectedFrequency).toNote();
                    setNote(mucialNote);
                }
                else {
                    setPitch(null);
                    setNote(null);
                }
                requestRef.current = requestAnimationFrame(analyze);

            };
            analyze();

        }
        catch (error) {
            console.error(error);
        }
    };

    useEffect(() => {
        return () => {
            if (requestRef.current) cancelAnimationFrame(requestRef.current);
            micRef.current?.close();
            micRef.current?.dispose();
            analyserRef.current?.dispose();
        }
    }, []);



    return {
        note,
        pitch,
        toggleMic,
    }
}
