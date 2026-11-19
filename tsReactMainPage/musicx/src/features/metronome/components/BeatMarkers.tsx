import type {JSX} from "react";
import styles from './styles/MetronomeSlider.module.css';

interface beatMarkerProps {
    totalBeats: number;
    currentBeat: number;
}

const BeatMarkers = ({totalBeats, currentBeat}: beatMarkerProps): JSX.Element => {
    const beatsArray = Array.from({ length: totalBeats }, (_, index) => index);

    return (
        <div className={styles['retro-beats-container']}>
            {beatsArray.map((index) => (
                <div
                    key={index}
                    className={`${styles['retro-beat-dot']} ${currentBeat === index ? styles.active : ''}`}
                />
            ))}
        </div>
    );
}

export default BeatMarkers;