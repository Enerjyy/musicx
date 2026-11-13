import {useCountingHZ} from "../features/tuner/hooks/CountingHZ.ts";
import CheckerHZ from "../features/tuner/components/CheckerHZ.tsx";


const TunerPage = () => {
    const { pitch, note, toggleMic } = useCountingHZ();
    return (
        <CheckerHZ pitch={pitch} note={note} toggleMic={toggleMic}/>
    )
}

export default TunerPage;