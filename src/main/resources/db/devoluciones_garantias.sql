CREATE TABLE IF NOT EXISTS public.catalogo_tipos_devolucion (
    codigo      CHARACTER VARYING(20) NOT NULL,
    descripcion CHARACTER VARYING(80) NOT NULL,
    CONSTRAINT catalogo_tipos_devolucion_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_tipos_devolucion (codigo, descripcion) VALUES
    ('DEVOLUCION', 'Devolucion de producto facturado'),
    ('GARANTIA',   'Atencion por garantia sin reembolso')
ON CONFLICT (codigo) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.catalogo_estados_devolucion (
    codigo       CHARACTER VARYING(20) NOT NULL,
    descripcion  CHARACTER VARYING(80) NOT NULL,
    orden_visual SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT catalogo_estados_devolucion_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_estados_devolucion (codigo, descripcion, orden_visual) VALUES
    ('REGISTRADA', 'Registro aplicado al inventario y trazabilidad', 1),
    ('ANULADA',    'Registro reversado con movimiento contrario',    2)
ON CONFLICT (codigo) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.catalogo_acciones_dinero_devolucion (
    codigo      CHARACTER VARYING(20) NOT NULL,
    descripcion CHARACTER VARYING(80) NOT NULL,
    CONSTRAINT catalogo_acciones_dinero_devolucion_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_acciones_dinero_devolucion (codigo, descripcion) VALUES
    ('REEMBOLSO',     'Se devuelve dinero al cliente'),
    ('SALDO_FAVOR',   'Se deja saldo a favor del cliente'),
    ('SIN_REEMBOLSO', 'No hay movimiento de dinero')
ON CONFLICT (codigo) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.devoluciones_garantias (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo CHARACTER VARYING(20) NOT NULL DEFAULT 'DEVOLUCION',
    estado CHARACTER VARYING(20) NOT NULL DEFAULT 'REGISTRADA',
    venta_id UUID NULL,
    entrega_id UUID NULL,
    cliente_id UUID NULL,
    cliente_nombre CHARACTER VARYING(120),
    razon TEXT NOT NULL,
    accion_dinero CHARACTER VARYING(20) NOT NULL DEFAULT 'REEMBOLSO',
    monto_devuelto NUMERIC(10, 2) NOT NULL DEFAULT 0,
    notas TEXT,
    registrado_por UUID NULL,
    fecha TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT devoluciones_garantias_origen_check CHECK (
        (venta_id IS NOT NULL AND entrega_id IS NULL)
        OR (venta_id IS NULL AND entrega_id IS NOT NULL)
    ),
    CONSTRAINT devoluciones_garantias_monto_check CHECK (monto_devuelto >= 0),
    CONSTRAINT devoluciones_garantias_tipo_fkey FOREIGN KEY (tipo)
        REFERENCES public.catalogo_tipos_devolucion (codigo) ON DELETE RESTRICT,
    CONSTRAINT devoluciones_garantias_estado_fkey FOREIGN KEY (estado)
        REFERENCES public.catalogo_estados_devolucion (codigo) ON DELETE RESTRICT,
    CONSTRAINT devoluciones_garantias_accion_dinero_fkey FOREIGN KEY (accion_dinero)
        REFERENCES public.catalogo_acciones_dinero_devolucion (codigo) ON DELETE RESTRICT,
    CONSTRAINT devoluciones_garantias_venta_fkey FOREIGN KEY (venta_id)
        REFERENCES public.ventas (id) ON DELETE RESTRICT,
    CONSTRAINT devoluciones_garantias_entrega_fkey FOREIGN KEY (entrega_id)
        REFERENCES public.entregas (id) ON DELETE RESTRICT,
    CONSTRAINT devoluciones_garantias_cliente_fkey FOREIGN KEY (cliente_id)
        REFERENCES public.clientes (id) ON DELETE SET NULL,
    CONSTRAINT devoluciones_garantias_usuario_fkey FOREIGN KEY (registrado_por)
        REFERENCES public.usuarios (id) ON DELETE SET NULL
) TABLESPACE pg_default;

CREATE TABLE IF NOT EXISTS public.devolucion_garantia_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    devolucion_id UUID NOT NULL,
    producto_id UUID NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(10, 2) NOT NULL DEFAULT 0,
    motivo_item CHARACTER VARYING(255),
    CONSTRAINT devolucion_garantia_items_devolucion_fkey FOREIGN KEY (devolucion_id)
        REFERENCES public.devoluciones_garantias (id) ON DELETE CASCADE,
    CONSTRAINT devolucion_garantia_items_producto_fkey FOREIGN KEY (producto_id)
        REFERENCES public.productos (id) ON DELETE RESTRICT,
    CONSTRAINT devolucion_garantia_items_precio_check CHECK (precio_unitario >= 0)
) TABLESPACE pg_default;

DROP TRIGGER IF EXISTS trg_devoluciones_garantias_updated_at
    ON public.devoluciones_garantias;

CREATE TRIGGER trg_devoluciones_garantias_updated_at
    BEFORE UPDATE ON public.devoluciones_garantias
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

ALTER TABLE public.movimientos_inventario
    DROP CONSTRAINT IF EXISTS mov_origen_tipo_check;

ALTER TABLE public.movimientos_inventario
    ADD CONSTRAINT mov_origen_tipo_check CHECK (
        origen_tipo IS NULL OR
        origen_tipo IN ('VENTA', 'COMPRA', 'ENTREGA', 'DEVOLUCION', 'AJUSTE', 'MANUAL')
    );

CREATE INDEX IF NOT EXISTS idx_devoluciones_venta ON public.devoluciones_garantias USING btree (venta_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_devoluciones_entrega ON public.devoluciones_garantias USING btree (entrega_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_devoluciones_fecha ON public.devoluciones_garantias USING btree (fecha DESC) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_devolucion_items_devolucion ON public.devolucion_garantia_items USING btree (devolucion_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_devolucion_items_producto ON public.devolucion_garantia_items USING btree (producto_id) TABLESPACE pg_default;
