interface CheckerHZProps {
    pitch: number | null;
    note: any;
    toggleMic: () => void;
}

const CheckerHZ = ({pitch, note, toggleMic}: CheckerHZProps) => {
    return (
        <div>
            <p>{pitch}</p>
            <p>{note}</p>
            <button onClick={toggleMic} >Включить микро</button>
        </div>
    )
}

export default CheckerHZ;