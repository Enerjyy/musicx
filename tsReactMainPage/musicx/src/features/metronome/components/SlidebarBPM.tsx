import type {JSX} from "react";
import styles from './styles/MetronomeSlider.module.css';

interface MetronomeSliderProps {
    BPMValue: number;
    setBPMValue: (value: number) => void;
}

const MetronomeSlider = ({BPMValue, setBPMValue}: MetronomeSliderProps): JSX.Element => {

    return (
        <div className={styles['retro-slider-container']}>
            <div className={styles['retro-slider-value']}>BPM: {BPMValue}</div>
            <input 
                type="range" 
                min="20" 
                className={styles['retro-slider']} 
                max="340" 
                onChange={(e) => setBPMValue(Number(e.target.value))} 
                value={BPMValue}
            />
        </div>)
}

export default MetronomeSlider;