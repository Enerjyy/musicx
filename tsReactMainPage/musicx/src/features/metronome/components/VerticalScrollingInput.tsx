import type {ChangeEvent, JSX} from "react";
import styles from './styles/MetronomeSlider.module.css';

interface VerticalScrollingInputProps {
    min: number;
    max: number;
    value: number | string;
    setValue: (value:number) => void;
    label: string;
}
const VerticalScrollingInput = ({min, max, value, setValue, label}: VerticalScrollingInputProps): JSX.Element => {
    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        const rawValue = e.target.value;
        if (rawValue === "") {
            setValue(60);
            return;
        }

        const numericValue = Number(rawValue);

        if (numericValue <= max) {
            setValue(numericValue);
        }

    }
    return (
        <div className={styles['retro-scrolling-input']}>
            {label && <div className={styles['retro-label']}>{label}</div>}
            <div className={styles['retro-input-wrapper']}>                <input 
                    type={"number"}
                    value={value}
                    min={min}
                    max={max}
                    onChange={handleChange}
                    className={styles['retro-input']}
                />
            </div>
        </div>
    )
}

export default VerticalScrollingInput;