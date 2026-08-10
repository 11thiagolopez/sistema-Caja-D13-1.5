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
import { Gastos } from './pages/Gastos'
import { ComprasNueva } from './pages/ComprasNueva'
import { ComprasConsulta } from './pages/ComprasConsulta'
import { PagosProveedores } from './pages/PagosProveedores'
import { Vendedores } from './pages/Vendedores'
import { ComisionesVendedores } from './pages/ComisionesVendedores'
import { VentasPorVendedor } from './pages/VentasPorVendedor'
import { VentasPorMarca } from './pages/VentasPorMarca'
import { VentasPorFormaPago } from './pages/VentasPorFormaPago'
import { Presupuestos } from './pages/Presupuestos'
import { TrabajoDomicilio } from './pages/TrabajoDomicilio'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          {/* Cobros es la pantalla de aterrizaje: adonde te deja el modal de abrir caja post-login. */}
          <Route path="/" element={<Navigate to="/ventas/nueva" replace />} />
          <Route path="/productos" element={<Productos />} />
          <Route path="/ventas/nueva" element={<RegistrarVenta />} />
          <Route path="/presupuestos" element={<Presupuestos />} />
          <Route element={<RequireRole allow={['ADMIN']} />}>
            <Route path="/caja/resumen" element={<Caja />} />
            <Route path="/ventas/historial" element={<HistorialVentas />} />
            <Route path="/ventas/domicilio" element={<TrabajoDomicilio />} />
            <Route path="/reportes" element={<Reportes />} />
            <Route path="/reportes/marcas" element={<VentasPorMarca />} />
            <Route path="/reportes/forma-pago" element={<VentasPorFormaPago />} />
            <Route path="/gastos" element={<Gastos />} />
            <Route path="/compras/nueva" element={<ComprasNueva />} />
            <Route path="/compras" element={<ComprasConsulta />} />
            <Route path="/compras/pagos-proveedores" element={<PagosProveedores />} />
            <Route path="/vendedores" element={<Vendedores />} />
            <Route path="/vendedores/comisiones" element={<ComisionesVendedores />} />
            <Route path="/vendedores/ventas" element={<VentasPorVendedor />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  )
}

export default App
