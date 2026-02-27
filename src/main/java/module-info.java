module com.thiago.escenasFX {
    // 1. Traemos las herramientas de JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;
	requires javafx.base;

    // 2. Permitimos que el cargador de FXML entre a tu carpeta de controladores
    opens controllers to javafx.fxml;
    opens com.thiago.escenasFX to javafx.fxml;
    
    // 3. Exportamos tu paquete principal para que Java pueda arrancar la App
    exports com.thiago.escenasFX;
    
    // Si usas clases de 'model' o 'utils' en el FXML, también deberías abrirlos:
   // opens model to javafx.fxml;
}