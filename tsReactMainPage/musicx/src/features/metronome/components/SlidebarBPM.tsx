import type {JSX} from "react";
import styles from './styles/MetronomeSlider.module.css';

interface MetronomeSliderProps {
    BPMValue: number;
    setBPMValue: (value: number) => void;
}

const MetronomeSlider = ({ BPMValue, setBPMValue }: MetronomeSliderProps): JSX.Element => {
    return (
        <div className={styles.sliderRow}>
            <div className={styles.tempo}>
                <span className={styles.tempoValue}>{BPMValue}</span>
                <span className={styles.tempoUnit}>bpm</span>
            </div>
            <input
                type="range"
                min="20"
                max="340"
                className={styles.rangeInput}
                onChange={(e) => setBPMValue(Number(e.target.value))}
                value={BPMValue}
                aria-label="Tempo in beats per minute"
            />
        </div>
    )
}

export default MetronomeSlider;