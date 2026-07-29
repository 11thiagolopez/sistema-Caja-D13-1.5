import { useEffect, useRef, useState, type KeyboardEvent } from 'react'

interface BarcodeInputProps {
  onScan: (codigo: string) => void
  placeholder?: string
}

/**
 * Input pensado para un lector de código de barras físico (USB/Bluetooth): el lector emula un
 * teclado, tipea el código y manda Enter solo. Se mantiene enfocado todo el tiempo para que un
 * escaneo entre directo sin volver a hacer click.
 */
export function BarcodeInput({ onScan, placeholder }: BarcodeInputProps) {
  const [valor, setValor] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter') return
    event.preventDefault()
    const codigo = valor.trim()
    if (!codigo) return
    onScan(codigo)
    setValor('')
    inputRef.current?.focus()
  }

  return (
    <input
      ref={inputRef}
      type="text"
      value={valor}
      onChange={(e) => setValor(e.target.value)}
      onKeyDown={onKeyDown}
      placeholder={placeholder ?? 'Escanear código de barras...'}
    />
  )
}
