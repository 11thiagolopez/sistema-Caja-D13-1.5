# Sistema de Gestión de Caja Diaria 💰

[![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![Maven](https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)](https://maven.apache.org/)
[![Console](https://img.shields.io/badge/Interface-Console-4EAA25?style=for-the-badge)]()
[![Descargar Release](https://img.shields.io/badge/⬇️_Descargar-v1.0_Estable-blue?style=for-the-badge&logo=github)](https://github.com/11thiagolopez/sistema-Caja-D13-1.5/releases/tag/v1.0.0)

## 📖 Descripción del Proyecto

Aplicación de consola desarrollada en **Java** para la gestión eficiente del flujo de caja diario en un comercio. El sistema automatiza el registro de operaciones (ventas, retiros, transferencias) y garantiza la integridad de los datos financieros al final del día.

El proyecto sigue estrictamente los principios de la **Programación Orientada a Objetos (POO)** y utiliza **Maven** para la gestión de dependencias y construcción.

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

El software se estructura en componentes con responsabilidad única (SRP), organizados bajo el paquete `com.thiagolopez.cajadiaria`:

### 1. `Main`
Punto de entrada. Orquesta la interacción usuario-sistema y gestiona el bucle principal.

### 2. `CajaDiaria` (Lógica de Negocio)
El núcleo del sistema. Centraliza operaciones y cálculos.

```java
public class CajaDiaria implements Serializable {
    private double comienzoCaja;
    private List<Transaccion> transacciones;
    // Métodos con Streams para reportes en tiempo real
}
3. Transaccion (Modelo)
Clase inmutable que modela cada operación usando Enums para seguridad de tipos.

Java

public class Transaccion implements Serializable {
    public enum Tipo { VENTA, RETIRO }
    public enum MedioDePago { EFECTIVO, TRANSFERENCIA, TARJETA }
    // ...
}
4. GestorArchivo (Persistencia)
Maneja la serialización y deserialización de objetos (estado_caja.dat).

🚀 Instalación y Ejecución
Tienes dos formas de usar el programa:

Opción A: Ejecutar el Binario (Recomendado para probar)
No necesitas compilar nada. Solo necesitas tener Java instalado.

Descarga el archivo .jar desde la sección de Releases.

Abre tu terminal en la carpeta de descargas.

Ejecuta:

Bash

java -jar sistema-gestion-caja-1.0-SNAPSHOT.jar

Opción B: Compilar desde el Código Fuente (Maven)
Si quieres ver el código y compilarlo tú mismo:

Clonar el repositorio:

Bash

git clone [https://github.com/11thiagolopez/sistema-Caja-D13-1.5.git](https://github.com/11thiagolopez/sistema-Caja-D13-1.5.git)
cd sistema-Caja-D13-1.5
Compilar con Maven:

Bash

mvn clean package
Ejecutar:

Bash

java -jar target/sistema-gestion-caja-1.0-SNAPSHOT.jar

💻 Uso
La aplicación está diseñada para ser rápida (Flujo de Cajero):

Venta Rápida: Simplemente escribe el precio y presiona Enter.

El sistema te pedirá el medio de pago y el nombre del producto.

Retiro de Dinero: Escribe R (o r) y presiona Enter.

Selecciona si es Efectivo (E) o Transferencia (T).

Salir: Escribe 0 para cerrar la caja y generar el reporte final.

Ejemplo de flujo:

📦 Ingrese precio del producto ($) o (R) para retirar dinero: 1500.50 💳 Ingrese medio de pago 1/ef 2/trans 3/tarj: 1 🏷️ Ingrese nombre del producto vendido: Bebida Energética ✅ Venta registrada.

Hecho con ☕ y Java.

