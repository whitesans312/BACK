package com.ergpos.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AppApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
		System.out.println("Iniciando migración de base de datos para configuracion_negocio...");
		
		// 1. Crear tabla configuracion_negocio si no existe
		String createTableSql = """
			CREATE TABLE IF NOT EXISTS public.configuracion_negocio (
				clave        VARCHAR(50)  NOT NULL,
				valor        TEXT                   NOT NULL,
				categoria    VARCHAR(30)  NOT NULL,
				descripcion  VARCHAR(255) NULL,
				updated_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
				CONSTRAINT configuracion_negocio_pkey PRIMARY KEY (clave)
			);
			""";
		jdbcTemplate.execute(createTableSql);
		System.out.println("Tabla configuracion_negocio verificada/creada.");

		// 2. Insertar valores por defecto con ON CONFLICT DO NOTHING
		String insertSql = """
			INSERT INTO public.configuracion_negocio (clave, valor, categoria, descripcion) VALUES
			('COMPANY_NAME', 'ERG-POS Sillas Ergonómicas', 'IDENTIFICACION', 'Nombre de la empresa para facturas'),
			('COMPANY_NIT', '901.234.567-8', 'IDENTIFICACION', 'NIT o identificación tributaria'),
			('COMPANY_CITY', 'Cali', 'IDENTIFICACION', 'Ciudad base de operación'),
			('TAX_PERCENTAGE', '19.00', 'FINANZAS', 'Porcentaje de IVA aplicable'),
			('CURRENCY_CODE', 'COP', 'FINANZAS', 'Código internacional de moneda'),
			('CURRENCY_SYMBOL', '$', 'FINANZAS', 'Símbolo monetario de visualización'),
			('BILLING_PREFIX_POS', 'FV-', 'FACTURACION', 'Prefijo de facturas de venta directas'),
			('BILLING_PREFIX_ORDER', 'FO-', 'FACTURACION', 'Prefijo de facturas de órdenes de servicio'),
			('BILLING_RESOLUTION', 'Resolución DIAN No. 18760000 de 2026. Rango FV-0001 al FV-9999.', 'FACTURACION', 'Texto legal obligatorio en pie de factura'),
			('SERVICE_WARRANTY_DAYS', '90', 'SERVICIOS', 'Días de garantía para reparaciones técnicas'),
			('SERVICE_MIN_ANTICIPO_PCT', '30.00', 'SERVICIOS', 'Porcentaje mínimo de anticipo para iniciar orden'),
			('SERVICE_FIELD_LABEL', 'Silla Ergonómica', 'SERVICIOS', 'Campo dinámico a reparar para versatilidad de industrias'),
			('INVENTORY_ALERT_AUTO_BUY', 'false', 'INVENTARIO', 'Crear compra borrador automáticamente al llegar a stock mínimo'),
			('INVENTORY_ALLOW_NEGATIVE', 'false', 'INVENTARIO', 'Permitir ventas sin stock (no recomendado)'),
			('SESSION_TIMEOUT_MINUTES', '480', 'SEGURIDAD', 'Tiempo límite de sesión antes de auto-logout')
			ON CONFLICT (clave) DO NOTHING;
			""";
		jdbcTemplate.execute(insertSql);
		System.out.println("Parámetros por defecto de configuracion_negocio verificados/insertados.");

		// 3. Crear índice en devoluciones_garantias.cliente_id
		String createIndexSql = "CREATE INDEX IF NOT EXISTS idx_devoluciones_cliente ON public.devoluciones_garantias(cliente_id);";
		jdbcTemplate.execute(createIndexSql);
		System.out.println("Índice idx_devoluciones_cliente verificado/creado.");
		
		System.out.println("Migración completada con éxito.");
	}

}

