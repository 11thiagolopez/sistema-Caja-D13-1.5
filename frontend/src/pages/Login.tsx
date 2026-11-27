import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiRequestError } from '../api/client'
import { Logo } from '../components/Logo'

export function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [usuario, setUsuario] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setCargando(true)
    try {
      const rol = await login(usuario.trim(), password)
      const destinoPorDefecto = rol === 'TECNICO' ? '/mis-trabajos' : '/ventas/nueva'
      navigate((location.state as { desde?: string } | null)?.desde ?? destinoPorDefecto, { replace: true })
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo iniciar sesión')
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="pantalla-login">
      <div className="login-marca">
        <Logo tamano={64} claro />
        <div>
          <h1>Tu caja, tus ventas y tus trabajos, en un solo lugar.</h1>
          <p>Sistema de gestión para comercios y distribuidoras.</p>
        </div>
        <small>© {new Date().getFullYear()} oNce</small>
      </div>

      <div className="login-panel">
        <div className="login-logo-celular">
          <Logo tamano={48} />
        </div>
        <form onSubmit={onSubmit}>
          <h2>Iniciar sesión</h2>
          <p className="login-subtitulo">Ingresá con tu usuario del sistema</p>
          <label>
            Usuario
            <input
              name="username"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              value={usuario}
              onChange={(e) => setUsuario(e.target.value)}
              autoFocus
              required
            />
          </label>
          <label>
            Contraseña
            <input
              type="password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          {error && (
            <p className="alerta alerta-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" disabled={cargando}>
            {cargando && <span className="spinner" aria-hidden="true" />}
            {cargando ? 'Ingresando…' : 'Entrar'}
          </button>
        </form>
      </div>
    </div>
  )
}
