import { Navigate, Route, Routes } from 'react-router-dom'
import './App.css'
import { Layout } from './components/Layout'
import { RequireAuth, RequireRole } from './auth/ProtectedRoute'
import { Login } from './pages/Login'
import { Productos } from './pages/Productos'
import { RegistrarVenta } from './pages/RegistrarVenta'
import { HistorialVentas } from './pages/HistorialVentas'
import { Caja } from './pages/Caja'
import { Reportes } from './pages/Reportes'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/" element={<Navigate to="/productos" replace />} />
          <Route path="/productos" element={<Productos />} />
          <Route path="/ventas/nueva" element={<RegistrarVenta />} />
          {/* Caja: ambos roles entran (abrir/cerrar/solicitar retiro), la pantalla misma
              branchea por rol para el resto (confirmar retiro y resumen son solo ADMIN). */}
          <Route path="/caja" element={<Caja />} />
          <Route element={<RequireRole allow={['ADMIN']} />}>
            <Route path="/ventas/historial" element={<HistorialVentas />} />
            <Route path="/reportes" element={<Reportes />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  )
}

export default App
