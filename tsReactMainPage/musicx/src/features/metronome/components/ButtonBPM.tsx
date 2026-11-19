import type {JSX} from "react";
import styles from '../styles/MetronomeSlider.module.css';

interface MetronomeButtonProps {
    currentImage: string;
    toggleSlider: () => void;
}

const MetronomeButton = ({ currentImage, toggleSlider }: MetronomeButtonProps): JSX.Element => {
    return (
        <div className={styles['retro-container']}>            
            <button 
                onClick={toggleSlider} 
                className={styles['retro-button']}
                style={{ backgroundImage: `url(${currentImage})` }}
            />
        </div>
    )
}

export default MetronomeButton;