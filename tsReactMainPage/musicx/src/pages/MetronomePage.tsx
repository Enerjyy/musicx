import {useCatSlider} from "../features/metronome/hooks/catOnButton.tsx";
import MetronomeButton from "../features/metronome/components/ButtonBPM.tsx";
import MetronomeSlider from "../features/metronome/components/SlidebarBPM.tsx";
import BeatMarkers from "../features/metronome/components/BeatMarkers.tsx";
import VerticalScrollingInput from "../features/metronome/components/VerticalScrollingInput.tsx";
import styles from "./styles/MetronomePage.module.css";
const MetronomePage = () => {
    const {currentImage, toggleSlider, BPMValue, setBPMValue, currentBeat, numerator, setNumerator, denominator, setDenominator} = useCatSlider();
    return (
        <div>
            <MetronomeButton currentImage={currentImage} toggleSlider={toggleSlider}/>
            <MetronomeSlider BPMValue={BPMValue} setBPMValue={setBPMValue}/>
            <BeatMarkers totalBeats={numerator} currentBeat={currentBeat}/>
            <div className={styles.controlsRow}>
                <VerticalScrollingInput max={340} min={20} value={BPMValue} setValue={setBPMValue} label={"BPM"} />
                <VerticalScrollingInput max={16} min={1} value={numerator} setValue={setNumerator} label={"Numerator"} />
                <VerticalScrollingInput max={16} min={1} value={denominator} setValue={setDenominator} label={"Denominator"} />
            </div>
        </div>
    );
}

export default MetronomePage;