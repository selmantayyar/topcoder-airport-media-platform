import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { PassengerScreen } from './components/PassengerScreen'
import { ClaimPage } from './components/ClaimPage'
import { ServerStreamProvider } from './server/stream'
import { I18nProvider } from './i18n/I18nProvider'
import { applyBrand } from './brand'

applyBrand()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <I18nProvider>
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
      </I18nProvider>
    </BrowserRouter>
  </StrictMode>,
)
