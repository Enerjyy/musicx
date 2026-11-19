import type {JSX} from "react";
import styles from './styles/MetronomeSlider.module.css';

interface BeatMarkersProps {
    totalBeats: number;
    currentBeat: number;
}

const BeatMarkers = ({ totalBeats, currentBeat }: BeatMarkersProps): JSX.Element => {
    const beats = Array.from({ length: totalBeats }, (_, index) => index);

    return (
        <div className={styles.beats} aria-hidden="true">
            {beats.map((index) => (
                <div
                    key={index}
                    className={
                        styles.beatDot +
                        (currentBeat === index ? ` ${styles['beatDot--active']}` : '')
                    }
                />
            ))}
        </div>
    )
}

export default BeatMarkers;