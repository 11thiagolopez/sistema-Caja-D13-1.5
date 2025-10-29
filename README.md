Sistema de Gestión de Caja Diaria
Descripción del Proyecto
Este proyecto es una aplicación de consola desarrollada en Java que implementa un sistema para la gestión de una caja diaria de un negocio. El programa permite llevar un registro de las ventas, retiros de efectivo y transferencias, y generar reportes detallados.
La aplicación está desarrollada siguiendo los principios de la Programación Orientada a Objetos (POO), encapsulando la lógica de negocio en clases bien definidas. Además, cuenta con persistencia de datos, guardando el estado de la caja en un archivo para poder recuperar la información en futuras ejecuciones.
Características
Gestión de Caja: Permite inicializar la caja con un monto inicial.
Registro de Ventas: Se pueden registrar ventas especificando el producto, el precio y el medio de pago (Efectivo, Transferencia o Tarjeta).
Retiros: Soporta retiros de dinero tanto en efectivo como por transferencia, validando siempre que haya saldo suficiente.
Persistencia de Datos: Guarda automáticamente el estado de la caja en un archivo (estado_caja.dat). Si se cierra la aplicación y se vuelve a abrir, cargará el último estado guardado.
Reportes Detallados:
Muestra un resumen del estado de la caja: monto inicial, total de ventas por cada medio de pago, total de retiros y caja final.
Genera un listado detallado de todas las ventas realizadas.
Estructura Orientada a Objetos: El código está organizado en clases con responsabilidades únicas, lo que facilita su mantenimiento y escalabilidad.
Estructura del Proyecto
El proyecto está compuesto por 4 clases principales que se encargan de la lógica y el funcionamiento del sistema:
1. Main
Es la clase principal y el punto de entrada de la aplicación (main). Se encarga de:
Cargar el estado anterior de la caja desde el archivo estado_caja.dat utilizando el GestorArchivo.
Si no existe un estado guardado, solicita al usuario un monto inicial para crear una nueva caja.
Presentar un menú interactivo al usuario para que elija las operaciones a realizar (registrar venta, hacer retiro, ver reportes, etc.).
Orquestar la interacción entre las demás clases para que la aplicación funcione correctamente.
2. CajaDiaria
Representa la caja del día y centraliza toda la lógica de negocio.
Almacena el monto inicial y una lista de todas las Transaccion (ventas y retiros).
Contiene los métodos para registrarVenta(), realizarRetiroEfectivo() y realizarRetiroTransferencia().
Calcula dinámicamente los totales (getVentasEfectivo(), getVentasTransferencia(), etc.) a partir de la lista de transacciones, utilizando la API de Streams de Java para un código más limpio y eficiente.
Genera los detalles de las ventas para los reportes.
Implementa la interfaz Serializable para poder ser guardada en un archivo.
code
Java
public class CajaDiaria implements Serializable {
    private double comienzoCaja;
    private List<Transaccion> transacciones;

    // Métodos para registrar transacciones y calcular totales
}
3. Transaccion
Modela una única transacción, que puede ser una venta o un retiro.
Utiliza enums para definir los tipos de transacción (VENTA, RETIRO) y los medios de pago (EFECTIVO, TRANSFERENCIA, TARJETA), lo que evita errores y hace el código más legible.
Almacena el monto, el tipo, el medio de pago y el nombre del producto (en caso de ser una venta).
Es un objeto inmutable una vez creado (sus atributos son final), lo que aporta seguridad.
También implementa Serializable para que los objetos de esta clase puedan ser guardados junto con la CajaDiaria.
code
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
4. GestorArchivo
Clase de utilidad (static) encargada exclusivamente de la persistencia de datos.
guardar(CajaDiaria caja): Serializa el objeto CajaDiaria y lo guarda en el archivo estado_caja.dat.
cargar(): Lee el archivo estado_caja.dat y lo deserializa para recuperar el objeto CajaDiaria del estado anterior.
Maneja las posibles excepciones de entrada/salida (IOException) y de casting (ClassNotFoundException).
code
Java
public class GestorArchivo {
    private static final String NOMBRE_ARCHIVO = "estado_caja.dat";

    public static void guardar(CajaDiaria caja) { /* ... */ }
    public static CajaDiaria cargar() { /* ... */ }
}
Requisitos Previos
Para compilar y ejecutar este proyecto, necesitarás tener instalado:
Java Development Kit (JDK), versión 8 o superior.
Instalación y Ejecución
Clonar el repositorio:
code
Bash
git clone https://github.com/tu-usuario/tu-repositorio.git
Navegar al directorio del proyecto:
code
Bash
cd tu-repositorio/src/preoyectRecuperacion
Compilar los archivos Java:
code
Bash
javac *.java
Ejecutar la aplicación:
code
Bash
java Main
Al ejecutar la aplicación, se presentará un menú en la consola para interactuar con el sistema. La primera vez, te pedirá el monto inicial para la caja. En las siguientes ejecuciones, cargará automáticamente la información guardada.
Uso
Una vez iniciada la aplicación, verás un menú como el siguiente:
code
Code
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
Opción 1: Te pedirá el nombre del producto, su precio y el medio de pago (1 para Efectivo, 2 para Transferencia, 3 para Tarjeta).
Opción 2 y 3: Te solicitará el monto a retirar y validará que haya fondos suficientes.
Opción 4: Mostrará un resumen completo del estado actual de la caja.
Opción 5: Listará todas las ventas registradas durante la sesión.
Opción 6: Cerrará la aplicación, guardando automáticamente el estado de la caja.
