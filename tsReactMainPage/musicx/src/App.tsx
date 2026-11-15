import './App.css'
import MetronomePage from "./pages/MetronomePage.tsx";
import TunerPage from "./pages/TunerPage.tsx";
import { useState } from 'react';



/* Для приложения нужно реализовать:
- Метроном, который можно будет настраивать на различное количество щелчков в такт, бпм и второй метроном для полиритмов. Можно добавить плавное автоускорение по тактам. Функция замолкания раз в такт/количетство тактов заданное пользователем.
- Тюнер для гитары/клавишных/басс гитарки, в нём нужно реализовать моментальное распознание ноты, возможность делать кастомные ноты и мб импортировать бд с различными настройками гитар для популярных треков
- Вокальные/клавишные ноты отдельно для определения(с возможностью нажать чтобы услышать как та звучит или кликнуть быстро два раза чтобы зажать 1 ноту)
- сохранение всего от пользователя на джава-спринг бэкенде.
- организация занятий(с напоминаниями про тренировку внутри тг бота) вместе с дневником и таймером сессии
- запись чего-либо чтобы потом прослушать и не забыть(можно сразу проанализировать на частоты и прочее через бота)
- мини игры(определение тона, продолжить ритм, дать максимально чистую ноту)
- определение бпм песни по настукиванию на экран
- чтение файлов с нотной грамотой */




const PAGES = {
    tuner: {label: 'Tuner', component: <TunerPage />},
    metronome: {label: 'Metronome', component: <MetronomePage />},
}

type PageKey = keyof typeof PAGES;

const App = () => {
    const [currentPage, setCurrentPage] = useState<PageKey>('tuner');


    return (
        <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>

        <div style={{ flex: 1, paddingBottom: '60px' }}>
                {PAGES[currentPage].component}
        </div>
        <footer style={{
                position: 'fixed',
                bottom: 0,
                left: 0,
                right: 0,
                display: 'flex',
                justifyContent: 'center',
                gap: '12px',
                padding: '12px',
                background: '#fff',
                borderTop: '1px solid #eaeaea'
        }}>
            {(Object.keys(PAGES) as PageKey[]).map((key) => (
                <button key={key} onClick={() => setCurrentPage(key)} style={{
                      fontWeight: currentPage === key ? 'bold' : 'normal',
                      padding: '8px 16px',
                      cursor: 'pointer'
                    }}> {PAGES[key].label}
                </button>
                ))}
        </footer>

    </div>);







}



export default App
