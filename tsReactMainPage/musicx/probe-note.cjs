const Tone = require('tone');

const hz = [65.41, 110, 220, 277.18, 311.13, 440, 466.16, 880];
for (const f of hz) {
  const note = Tone.Frequency(f).toNote();
  console.log(f, '->', JSON.stringify(note));
}