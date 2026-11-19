import type {JSX} from "react";
import styles from './styles/MetronomeSlider.module.css';

interface MetronomeButtonProps {
    currentImage: string;
    toggleSlider: () => void;
}

const MetronomeButton = ({ currentImage, toggleSlider }: MetronomeButtonProps): JSX.Element => {
    return (
        <button
            type="button"
            className={styles.catButton}
            onClick={toggleSlider}
            aria-label="Start / stop metronome"
        >
            <img src={currentImage} alt="" draggable={false} />
        </button>
    )
}

export default MetronomeButton;