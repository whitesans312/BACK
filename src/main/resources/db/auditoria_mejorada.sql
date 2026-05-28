-- ================================================================
-- MIGRACION: Auditoria mejorada
-- Objetivo:
--   - Mantener compatibilidad con los registros existentes.
--   - Agregar contexto consultable para auditoria operativa y forense.
--   - Reforzar la tabla como log inmutable.
-- ================================================================

DROP TRIGGER IF EXISTS trg_auditoria_no_update_delete ON public.auditoria;

ALTER TABLE public.auditoria
    ADD COLUMN IF NOT EXISTS entidad_tipo   CHARACTER VARYING(40)  NULL,
    ADD COLUMN IF NOT EXISTS resultado      CHARACTER VARYING(20)  NOT NULL DEFAULT 'EXITOSO',
    ADD COLUMN IF NOT EXISTS severidad      CHARACTER VARYING(10)  NOT NULL DEFAULT 'INFO',
    ADD COLUMN IF NOT EXISTS ip_origen      CHARACTER VARYING(45)      NULL,
    ADD COLUMN IF NOT EXISTS user_agent     CHARACTER VARYING(255)     NULL,
    ADD COLUMN IF NOT EXISTS metodo_http    CHARACTER VARYING(10)      NULL,
    ADD COLUMN IF NOT EXISTS ruta           CHARACTER VARYING(255)     NULL,
    ADD COLUMN IF NOT EXISTS estado_anterior JSONB                    NULL,
    ADD COLUMN IF NOT EXISTS estado_nuevo    JSONB                    NULL,
    ADD COLUMN IF NOT EXISTS metadata        JSONB                    NULL;

COMMENT ON COLUMN public.auditoria.entidad_tipo IS
    'Tipo legible de entidad afectada: VENTA, ENTREGA, PRODUCTO, CLIENTE, USUARIO, MOVIMIENTO_INVENTARIO, etc.';
COMMENT ON COLUMN public.auditoria.resultado IS
    'Resultado del evento auditado: EXITOSO, FALLIDO, DENEGADO.';
COMMENT ON COLUMN public.auditoria.severidad IS
    'Severidad operativa: INFO, WARN, ERROR, CRITICO.';
COMMENT ON COLUMN public.auditoria.ip_origen IS
    'IP del cliente HTTP. Se guarda como texto para tolerar proxies y cabeceras X-Forwarded-For.';
COMMENT ON COLUMN public.auditoria.estado_anterior IS
    'Snapshot JSON antes del cambio, cuando aplique.';
COMMENT ON COLUMN public.auditoria.estado_nuevo IS
    'Snapshot JSON despues del cambio, cuando aplique.';
COMMENT ON COLUMN public.auditoria.metadata IS
    'Datos estructurados adicionales del evento. No guardar secretos ni contrasenas.';

-- Backfill suave para que el historial anterior sea mas filtrable.
UPDATE public.auditoria
SET usuario_nombre = u.nombre
FROM public.usuarios u
WHERE auditoria.usuario_id = u.id
  AND (auditoria.usuario_nombre IS NULL OR btrim(auditoria.usuario_nombre) = '');

UPDATE public.auditoria
SET usuario_nombre = 'Sistema'
WHERE usuario_nombre IS NULL OR btrim(usuario_nombre) = '';

UPDATE public.auditoria
SET entidad_tipo = CASE modulo
    WHEN 'ORDENES' THEN 'ENTREGA'
    WHEN 'VENTAS' THEN 'VENTA'
    WHEN 'COMPRAS' THEN 'COMPRA'
    WHEN 'INVENTARIO' THEN 'MOVIMIENTO_INVENTARIO'
    WHEN 'DEVOLUCIONES' THEN 'DEVOLUCION_GARANTIA'
    WHEN 'CLIENTES' THEN 'CLIENTE'
    WHEN 'USUARIOS' THEN 'USUARIO'
    WHEN 'SEGURIDAD' THEN 'USUARIO'
    WHEN 'PRODUCTOS' THEN 'PRODUCTO'
    WHEN 'PROVEEDORES' THEN 'PROVEEDOR'
    WHEN 'CATEGORIAS' THEN 'CATEGORIA'
    WHEN 'CONFIGURACION' THEN 'CONFIGURACION_NEGOCIO'
    ELSE modulo
END
WHERE entidad_tipo IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'auditoria_resultado_check'
    ) THEN
        ALTER TABLE public.auditoria
            ADD CONSTRAINT auditoria_resultado_check
            CHECK (resultado IN ('EXITOSO', 'FALLIDO', 'DENEGADO'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'auditoria_severidad_check'
    ) THEN
        ALTER TABLE public.auditoria
            ADD CONSTRAINT auditoria_severidad_check
            CHECK (severidad IN ('INFO', 'WARN', 'ERROR', 'CRITICO'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_auditoria_usuario_fecha
    ON public.auditoria USING btree (usuario_id, fecha DESC) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_auditoria_entidad
    ON public.auditoria USING btree (entidad_tipo, entidad_id) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_auditoria_resultado_fecha
    ON public.auditoria USING btree (resultado, fecha DESC) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_auditoria_severidad_fecha
    ON public.auditoria USING btree (severidad, fecha DESC) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_auditoria_ruta_fecha
    ON public.auditoria USING btree (ruta, fecha DESC) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_auditoria_metadata_gin
    ON public.auditoria USING gin (metadata) TABLESPACE pg_default;

-- Log inmutable: se permite INSERT, pero no UPDATE ni DELETE desde la app.
-- Si una migracion futura necesita corregir datos, debe hacer DROP TRIGGER,
-- ejecutar el ajuste y recrearlo.
CREATE OR REPLACE FUNCTION public.fn_auditoria_bloquear_modificaciones()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'La tabla auditoria es inmutable: use INSERT para registrar nuevos eventos';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_no_update_delete
    BEFORE UPDATE OR DELETE ON public.auditoria
    FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_bloquear_modificaciones();
