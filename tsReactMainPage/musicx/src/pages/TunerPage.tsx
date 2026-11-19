import {useCountingHZ} from "../features/tuner/hooks/useCountingHZ.ts";
import CheckerHZ from "../features/tuner/components/CheckerHZ.tsx";


const TunerPage = () => {
    const { pitch, note, isActive, toggleMic } = useCountingHZ();
    return (
        <CheckerHZ
            pitch={pitch}
            note={note}
            isActive={isActive}
            toggleMic={toggleMic}
        />
    )
}

export default TunerPage;