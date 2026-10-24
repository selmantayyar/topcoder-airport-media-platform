import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { PassengerScreen } from './components/PassengerScreen'
import { ClaimPage } from './components/ClaimPage'
import { ServerStreamProvider } from './server/stream'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<App />} />
        {/* Bare full-screen kiosk; needs the clock stream to refresh as sim time moves. */}
        <Route
          path="/screen/:id"
          element={
            <ServerStreamProvider>
              <PassengerScreen />
            </ServerStreamProvider>
          }
        />
        {/* LAN-served mobile claim page; standalone, no app chrome or stream. */}
        <Route path="/claim/:code" element={<ClaimPage />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
