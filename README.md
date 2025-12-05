# Sistema de Gestión de Caja Diaria 💰

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) ![Console](https://img.shields.io/badge/Interface-Console-4EAA25?style=for-the-badge)

## 📖 Descripción del Proyecto

Aplicación de consola desarrollada en **Java** para la gestión eficiente del flujo de caja diario en un comercio. El sistema automatiza el registro de operaciones (ventas, retiros, transferencias) y garantiza la integridad de los datos financieros al final del día.

El proyecto sigue estrictamente los principios de la **Programación Orientada a Objetos (POO)**, encapsulando la lógica de negocio en clases definidas y utilizando patrones para la persistencia de datos mediante serialización.

## ✨ Características Principales

* **🛡️ Gestión de Caja:** Inicialización y control de flujo de efectivo.
* **🛒 Registro de Ventas:** Soporte para múltiples medios de pago (Efectivo, Transferencia, Tarjeta).
* **💸 Control de Retiros:** Validación de fondos para retiros de efectivo o transferencias salientes.
* **💾 Persistencia de Datos:** Guardado automático del estado (`estado_caja.dat`). La aplicación recupera la sesión anterior si se cierra inesperadamente.
* **📊 Reportes y Auditoría:**
    * Resumen financiero por medio de pago.
    * Listado detallado de transacciones para auditoría.
* **☕ Java Moderno:** Uso de la **API de Streams** para cálculos eficientes y código limpio.

---

## 🏗️ Arquitectura del Sistema

El software se estructura en 4 componentes principales, cada uno con responsabilidad única (SRP):

### 1. `Main`
Punto de entrada de la aplicación. Orquesta la interacción entre el usuario y el sistema, gestiona el menú interactivo y coordina la carga inicial de datos.

### 2. `CajaDiaria` (Lógica de Negocio)
El núcleo del sistema. Centraliza las operaciones y cálculos. Implementa `Serializable` para la persistencia.

```java
public class CajaDiaria implements Serializable {
    private double comienzoCaja;
    private List<Transaccion> transacciones;

    // Métodos para registrar transacciones y calcular totales
    // Uso de Streams para reportes
}
3. Transaccion (Modelo)
Clase inmutable que modela cada operación. Utiliza Enums para garantizar la integridad de los tipos de datos (evitando errores de "strings mágicos").

Java

public class Transaccion implements Serializable {
    public enum Tipo { VENTA, RETIRO }
    public enum MedioDePago { EFECTIVO, TRANSFERENCIA, TARJETA }

    private final Tipo tipo;
    private final MedioDePago medioDePago;
    private final double monto;
    private final String nombreProducto;

    // Constructores y getters
}
4. GestorArchivo (Persistencia)
Clase utilitaria (static) encargada del I/O. Maneja la serialización y deserialización de objetos, gestionando excepciones de entrada/salida.

Java

public class GestorArchivo {
    private static final String NOMBRE_ARCHIVO = "estado_caja.dat";

    public static void guardar(CajaDiaria caja) { /* ... */ }
    public static CajaDiaria cargar() { /* ... */ }
}
🚀 Instalación y Ejecución
Requisitos Previos
Java Development Kit (JDK) 8 o superior.

Pasos
Clonar el repositorio:

Bash

git clone [https://github.com/tu-usuario/tu-repositorio.git](https://github.com/tu-usuario/tu-repositorio.git)
Navegar al directorio:

Bash

cd tu-repositorio/src/preoyectRecuperacion
Compilar el código:

Bash

javac *.java
Ejecutar la aplicación:

Bash

java Main
💻 Uso
Al iniciar, el sistema cargará el estado previo o solicitará un monto inicial. El menú principal permite la navegación fluida:

Plaintext

========================================
     SISTEMA DE GESTIÓN DE CAJA
========================================
1. Registrar Venta
2. Realizar Retiro de Efectivo
3. Realizar Retiro por Transferencia
4. Ver Reporte de Caja
5. Ver Detalle de Ventas
6. Salir
========================================
Seleccione una opción:
Opción 1: Solicita producto, precio y medio de pago.

Opción 4: Muestra el balance general (Auditoría).

Opción 6: Cierra el programa y guarda automáticamente los cambios en disco.

Hecho con ☕ y Java.


### 💡 Un par de sugerencias extra:

1.  **En el paso de "Instalación":** Revisa que la ruta `cd tu-repositorio/src/preoyectRecuperacion` sea la correcta en tu GitHub real. Si la carpeta se llama diferente, cámbiala antes de subirlo.
2.  **Enlace al repositorio:** En el comando `git clone`, no olvides reemplazar `tu-usuario/tu-repositorio` por la URL real de tu GitHub.

¿Te gustaría que agreguemos una sección de "Próximas mejoras" (roadmap) para mostrar ambición de crecimiento del proyecto?
