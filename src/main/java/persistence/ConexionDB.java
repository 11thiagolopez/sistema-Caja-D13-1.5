package persistence; // Asegúrate de que este sea el package correcto

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import model.Venta; // Asegúrate de que el import de Venta sea correcto

public class ConexionDB {
    // Usamos una ruta fuera de la carpeta de usuario para que sea compartida
    private static final String URL = "jdbc:sqlite:C:/CajaCompartida/sistema_d13.db";

    public static Connection conectar() {
        try {
            // 1. Verificamos si la carpeta existe, si no, la creamos
            File directorio = new File("C:/CajaCompartida");
            if (!directorio.exists()) {
                directorio.mkdirs();
                System.out.println("📁 Carpeta 'CajaCompartida' creada automáticamente.");
            }
            return DriverManager.getConnection(URL);
        } catch (SQLException e) {
            System.err.println("❌ Error al conectar: " + e.getMessage());
            return null;
        }
    }

    public static void crearTablas() {
        String sqlVentas = "CREATE TABLE IF NOT EXISTS ventas (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "descripcion TEXT," + "cantidad INTEGER," + "precio REAL," + "medio_pago TEXT," + "tipo TEXT,"
                + "fecha TEXT DEFAULT (date('now','localtime'))"
                + ");";

        String sqlSesiones = "CREATE TABLE IF NOT EXISTS sesiones (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "fecha TEXT DEFAULT (date('now','localtime')),"
                + "monto_inicial REAL, estado TEXT DEFAULT 'ABIERTA');";

        try (Connection conn = conectar(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlVentas);
            stmt.execute(sqlSesiones);
            System.out.println("✅ Base de datos lista en ruta compartida.");
        } catch (SQLException e) {
            System.err.println("❌ Error al crear tablas: " + e.getMessage());
        }
    }

    public static void insertarVenta(String desc, int cant, double precio, String medio, String tipo) {
        String sql = "INSERT INTO ventas (descripcion, cantidad, precio, medio_pago, tipo) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = conectar(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, desc);
            pstmt.setInt(2, cant);
            pstmt.setDouble(3, precio);
            pstmt.setString(4, medio);
            pstmt.setString(5, tipo);
            pstmt.executeUpdate();
            System.out.println("✅ Movimiento guardado en SQLite.");
        } catch (SQLException e) {
            System.out.println("❌ Error al insertar: " + e.getMessage());
        }
    }

    public static void insertarSesion(double montoInicial) {
        String sql = "INSERT INTO sesiones (monto_inicial, fecha, estado) VALUES (?, date('now','localtime'), 'ABIERTA')";
        try (Connection conn = conectar(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, montoInicial);
            pstmt.executeUpdate();
            System.out.println("✅ Sesión de caja iniciada en la base de datos.");
        } catch (SQLException e) {
            System.err.println("❌ Error al iniciar sesión: " + e.getMessage());
        }
    }

    public static double obtenerMontoInicialHoy() {
        String sql = "SELECT monto_inicial FROM sesiones WHERE fecha = date('now','localtime') AND estado = 'ABIERTA' LIMIT 1";
        try (Connection conn = conectar();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getDouble("monto_inicial");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al verificar sesión: " + e.getMessage());
        }
        return 0.0;
    }

    public static List<Venta> obtenerVentasDelDia() {
        List<Venta> lista = new ArrayList<>();
        String sql = "SELECT * FROM ventas WHERE fecha = date('now','localtime')";
        try (Connection conn = conectar();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Venta v = new Venta(rs.getString("descripcion"), rs.getInt("cantidad"), rs.getDouble("precio"),
                        rs.getString("medio_pago"));
                if (rs.getString("tipo").equals("RETIRO")) {
                    v.setDescripcion("RETIRO: " + v.getDescripcion());
                }
                lista.add(v);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al leer reporte: " + e.getMessage());
        }
        return lista;
    }

    public static void insertarRetiro(String motivo, double monto, String medio) {
        String sql = "INSERT INTO ventas (descripcion, cantidad, precio, medio_pago, tipo) VALUES (?, 1, ?, ?, 'RETIRO')";
        try (Connection conn = conectar(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, motivo);
            pstmt.setDouble(2, monto);
            pstmt.setString(3, medio);
            pstmt.executeUpdate();
            System.out.println("✅ Retiro de " + medio + " guardado en la base de datos.");
        } catch (SQLException e) {
            System.err.println("❌ Error al guardar retiro: " + e.getMessage());
        }
    }
        
    public static void cerrarSesionActual() {
        String sql = "UPDATE sesiones SET estado = 'CERRADA' WHERE fecha = date('now','localtime') AND estado = 'ABIERTA'";
        try (Connection conn = conectar(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int filasAfectadas = pstmt.executeUpdate();
            if (filasAfectadas > 0) {
                System.out.println("✅ La sesión ha sido marcada como CERRADA en la base de datos.");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al cerrar la sesión: " + e.getMessage());
        }
    }

    public static void respaldarBaseDeDatos() {
        try {
            java.io.File carpetaBackup = new java.io.File("C:/CajaCompartida/Backups");
            if (!carpetaBackup.exists()) carpetaBackup.mkdirs();

            java.nio.file.Path origen = java.nio.file.Paths.get("C:/CajaCompartida/sistema_d13.db");
            String fecha = new java.text.SimpleDateFormat("dd_MM_yyyy").format(new java.util.Date());
            java.nio.file.Path destino = java.nio.file.Paths.get("C:/CajaCompartida/Backups/backup_d13_" + fecha + ".db");

            java.nio.file.Files.copy(origen, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("🛡️ Copia de seguridad creada con éxito.");
        } catch (java.io.IOException e) {
            System.err.println("❌ No se pudo crear el respaldo: " + e.getMessage());
        }
    }
}