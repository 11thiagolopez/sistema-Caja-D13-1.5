-- Datos de demostración 100% inventados — ningún nombre, dirección, CUIT ni dato real de ningún
-- negocio. Se corre solo bajo el perfil "demo" (ver application-demo.properties), contra H2 en
-- memoria que se recrea en cada arranque.
--
-- Usuarios: admin / demo1234 (ADMIN) · vendedor / demo1234 (VENDEDOR)
-- (hash BCrypt de "demo1234", generado una sola vez para este seed)

INSERT INTO empleados (id_empleado, nombre, usuario, password_hash, rol, email, comision, activo) VALUES
  (1, 'Admin Demo', 'admin', '$2a$10$qXNoL20lYFKSPNdsDi7PQ.s5NnwzCMZANDTexZlDvYpqqPYzh6Bo2', 'ADMIN', 'admin@demo.local', NULL, TRUE),
  (2, 'Vendedor Demo', 'vendedor', '$2a$10$qXNoL20lYFKSPNdsDi7PQ.s5NnwzCMZANDTexZlDvYpqqPYzh6Bo2', 'VENDEDOR', 'vendedor@demo.local', 8.00, TRUE),
  (3, 'Técnico Demo', 'tecnico', '$2a$10$qXNoL20lYFKSPNdsDi7PQ.s5NnwzCMZANDTexZlDvYpqqPYzh6Bo2', 'TECNICO', 'tecnico@demo.local', 15.00, TRUE);

INSERT INTO marcas (id_marca, nombre, codigo, activo) VALUES
  (1, 'Marca Norte', '01', TRUE),
  (2, 'Marca Sur', '02', TRUE),
  (3, 'ElectroPlus', '03', TRUE);

INSERT INTO proveedores (id_proveedor, nombre, contacto, telefono, email, activo) VALUES
  (1, 'Distribuidora Central Demo SRL', 'Juan Pérez', '011-4000-1111', 'ventas@distcentral.demo', TRUE),
  (2, 'Repuestos del Sur Demo', 'Mara Gómez', '011-4000-2222', 'contacto@repusur.demo', TRUE);

INSERT INTO clientes (id_cliente, nombre, doc_tipo, doc_nro, condicion_iva, telefono, email, direccion, activo, creado_en) VALUES
  (1, 'Consumidor Final', 99, NULL, NULL, NULL, NULL, NULL, TRUE, CURRENT_TIMESTAMP),
  (2, 'Laura Fernández', 96, '30123456', 5, '011-1555-0001', 'laura.fernandez@demo.local', 'Av. Siempreviva 742', TRUE, CURRENT_TIMESTAMP),
  (3, 'Carlos Rodríguez', 80, '20345678901', 1, '011-1555-0002', 'carlos.rodriguez@demo.local', 'Calle Falsa 123', TRUE, CURRENT_TIMESTAMP);

-- Stock variado a propósito para mostrar el código de colores: 0 rojo, 1-2 naranja, 3-4 amarillo, 5+ verde.
INSERT INTO productos (id_producto, rubro, familia, numero_marca, marca, correlativo, codigo_interno, proveedor, codigo_fabrica, codigo_barras, descripcion, precio_venta, precio_compra, precio_venta_usd, precio_compra_usd, stock_actual, activo) VALUES
  (1, 'FERRETERIA', 'CERRADURAS', '01', 'Marca Norte', '001', 'FE-CER-01-001', 'Distribuidora Central Demo SRL', 'FAB-1001', 'FAB-1001', 'Cerradura de embutir 60mm', 18500.00, 11000.00, 14.80, 8.80, 12, TRUE),
  (2, 'FERRETERIA', 'CERRADURAS', '01', 'Marca Norte', '002', 'FE-CER-01-002', 'Distribuidora Central Demo SRL', 'FAB-1002', 'FAB-1002', 'Cerradura de sobreponer', 15200.00, 9000.00, 12.16, 7.20, 4, TRUE),
  (3, 'FERRETERIA', 'CANDADOS', '02', 'Marca Sur', '001', 'FE-CAN-02-001', 'Repuestos del Sur Demo', 'FAB-2001', 'FAB-2001', 'Candado de acero 40mm', 8900.00, 5200.00, 7.12, 4.16, 2, TRUE),
  (4, 'FERRETERIA', 'CANDADOS', '02', 'Marca Sur', '002', 'FE-CAN-02-002', 'Repuestos del Sur Demo', 'FAB-2002', 'FAB-2002', 'Candado de acero 50mm', 12300.00, 7100.00, 9.84, 5.68, 0, TRUE),
  (5, 'CERRAJERIA', 'LLAVES', '01', 'Marca Norte', '003', 'CE-LLA-01-003', 'Distribuidora Central Demo SRL', 'FAB-1003', 'FAB-1003', 'Copia de llave tipo Yale', 3500.00, 1800.00, 2.80, 1.44, 25, TRUE),
  (6, 'FERRETERIA', 'HERRAJES', '02', 'Marca Sur', '003', 'FE-HER-02-003', 'Repuestos del Sur Demo', 'FAB-2003', 'FAB-2003', 'Bisagra reforzada 3 pulgadas', 2200.00, 1200.00, 1.76, 0.96, 8, TRUE),
  (7, 'FERRETERIA', 'HERRAJES', '03', 'ElectroPlus', '001', 'FE-HER-03-001', 'Distribuidora Central Demo SRL', 'FAB-3001', 'FAB-3001', 'Pomo cromado para puerta', 6400.00, 3600.00, 5.12, 2.88, 3, TRUE),
  (8, 'CERRAJERIA', 'CILINDROS', '01', 'Marca Norte', '004', 'CE-CIL-01-004', 'Distribuidora Central Demo SRL', 'FAB-1004', 'FAB-1004', 'Cilindro de seguridad antibumping', 21000.00, 13500.00, 16.80, 10.80, 1, TRUE),
  (9, 'HERRAMIENTAS', 'CAJAS', '03', 'ElectroPlus', '002', 'HE-CAJ-03-002', 'Repuestos del Sur Demo', 'FAB-3002', 'FAB-3002', 'Caja de herramientas 16 pulgadas', 45000.00, 29000.00, 36.00, 23.20, 6, TRUE),
  (10, 'HERRAMIENTAS', 'MANUALES', '02', 'Marca Sur', '004', 'HE-MAN-02-004', 'Repuestos del Sur Demo', 'FAB-2004', 'FAB-2004', 'Set de destornilladores x6', 9800.00, 5600.00, 7.84, 4.48, 0, TRUE);

INSERT INTO cotizaciones_dolar (id_cotizacion, fecha, valor_venta, fuente, manual, creado_en) VALUES
  (1, CURRENT_DATE, 1250.00, 'MANUAL', TRUE, CURRENT_TIMESTAMP);

-- Sesiones de caja: una abierta hoy, dos cerradas en días anteriores (para que Reportes/Historial
-- tengan rango para mostrar).
INSERT INTO sesiones_caja (id_sesion, fecha, monto_inicial, estado, cotizacion_usd_venta, id_empleado_apertura) VALUES
  (1, CURRENT_DATE, 50000.00, 'ABIERTA', 1250.00, 1),
  (2, CURRENT_DATE - 1, 45000.00, 'CERRADA', 1250.00, 2),
  (3, CURRENT_DATE - 2, 40000.00, 'CERRADA', 1250.00, 2);

-- Ventas de mostrador + un trabajo a domicilio + una venta pendiente de autorización, para que
-- Historial, Reportes, Autorizaciones y Trabajo a domicilio tengan contenido real para mostrar.
INSERT INTO ventas (id_venta, fecha, id_empleado, id_sesion, medio_pago, tipo_comprobante, total_venta, estado, descuento, motivo_descuento, tipo_venta, id_cliente, comprobante_enviado_por_email) VALUES
  (1, CURRENT_TIMESTAMP, 2, 1, 'EFECTIVO', 'TICKET', 25500.00, 'CONFIRMADA', 0, NULL, 'MOSTRADOR', 2, FALSE),
  (2, CURRENT_TIMESTAMP, 2, 1, 'TRANSFERENCIA', 'TICKET', 6400.00, 'CONFIRMADA', 0, NULL, 'MOSTRADOR', 1, FALSE),
  (3, CURRENT_TIMESTAMP - 1, 2, 2, 'EFECTIVO', 'TICKET', 40000.00, 'CONFIRMADA', 5000.00, 'Cliente frecuente', 'MOSTRADOR', 3, FALSE),
  (4, CURRENT_TIMESTAMP - 2, 1, 3, 'TARJETA', 'TICKET', 19600.00, 'CONFIRMADA', 0, NULL, 'MOSTRADOR', 1, FALSE),
  (6, CURRENT_TIMESTAMP, 2, 1, NULL, NULL, 7900.00, 'PENDIENTE_AUTORIZACION', 1000.00, 'Pidió descuento por pago en efectivo', 'MOSTRADOR', 1, FALSE);

INSERT INTO ventas (id_venta, fecha, id_empleado, id_sesion, medio_pago, tipo_comprobante, total_venta, estado, descuento, tipo_venta, cliente_nombre, cliente_telefono, direccion_trabajo, descripcion_trabajo, estado_trabajo, fecha_programada, id_empleado_tecnico, comision_tecnico, comprobante_enviado_por_email) VALUES
  (5, CURRENT_TIMESTAMP, 1, 1, 'EFECTIVO', 'REMITO', 33500.00, 'CONFIRMADA', 0, 'DOMICILIO', 'Jorge Medina', '011-1555-0099', 'Av. Rivadavia 4500', 'Cambio de cerradura de la puerta principal', 'COBRADO', CURRENT_DATE, 3, 15.00, FALSE);

INSERT INTO detalle_ventas (id_detalle, id_venta, id_producto, descripcion, tipo, cantidad, precio_unitario, subtotal) VALUES
  (1, 1, 1, 'Cerradura de embutir 60mm', 'ARTICULO', 1, 18500.00, 18500.00),
  (2, 1, 5, 'Copia de llave tipo Yale', 'ARTICULO', 2, 3500.00, 7000.00),
  (3, 2, 7, 'Pomo cromado para puerta', 'ARTICULO', 1, 6400.00, 6400.00),
  (4, 3, 9, 'Caja de herramientas 16 pulgadas', 'ARTICULO', 1, 45000.00, 45000.00),
  (5, 4, 2, 'Cerradura de sobreponer', 'ARTICULO', 1, 15200.00, 15200.00),
  (6, 4, 6, 'Bisagra reforzada 3 pulgadas', 'ARTICULO', 2, 2200.00, 4400.00),
  (7, 6, 3, 'Candado de acero 40mm', 'ARTICULO', 1, 8900.00, 8900.00),
  (8, 5, 1, 'Cerradura de embutir 60mm', 'ARTICULO', 1, 18500.00, 18500.00),
  (9, 5, NULL, 'Mano de obra: cambio de cerradura', 'SERVICIO', 1, 15000.00, 15000.00);

INSERT INTO gastos (id_gasto, nombre, importe, fecha, categoria, medio_pago, origen, id_empleado_registro, id_sesion, creado_en) VALUES
  (1, 'Pago de luz', 22000.00, CURRENT_DATE - 1, 'Servicios', 'TRANSFERENCIA', 'EXTERNO', 1, NULL, CURRENT_TIMESTAMP),
  (2, 'Compra de insumos de limpieza', 5400.00, CURRENT_DATE, 'Insumos', 'EFECTIVO', 'CAJA', 2, 1, CURRENT_TIMESTAMP);

ALTER TABLE empleados ALTER COLUMN id_empleado RESTART WITH 100;
ALTER TABLE marcas ALTER COLUMN id_marca RESTART WITH 100;
ALTER TABLE proveedores ALTER COLUMN id_proveedor RESTART WITH 100;
ALTER TABLE clientes ALTER COLUMN id_cliente RESTART WITH 100;
ALTER TABLE productos ALTER COLUMN id_producto RESTART WITH 100;
ALTER TABLE cotizaciones_dolar ALTER COLUMN id_cotizacion RESTART WITH 100;
ALTER TABLE sesiones_caja ALTER COLUMN id_sesion RESTART WITH 100;
ALTER TABLE ventas ALTER COLUMN id_venta RESTART WITH 100;
ALTER TABLE detalle_ventas ALTER COLUMN id_detalle RESTART WITH 100;
ALTER TABLE gastos ALTER COLUMN id_gasto RESTART WITH 100;
