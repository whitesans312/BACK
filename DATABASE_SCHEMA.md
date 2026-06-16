# DATABASE SCHEMA — ERG-POS

Base de datos PostgreSQL (Supabase). Versión 1.0 — producción.

## Orden de ejecución

> ⚠️ **No reordenar los bloques.** Hay dependencias entre tablas, triggers y secuencias.

| Bloque | Contenido |
|--------|-----------|
| 0 | Extensiones (`pgcrypto`) |
| 1 | Funciones de triggers (`fn_set_updated_at`, `fn_actualizar_stock`) |
| 2 | Secuencias de facturación (`seq_facturas_orden`, `seq_facturas_venta`) |
| 3 | Tablas catálogo (valores de referencia para estados y tipos) |
| 4 | Tablas base (`categorias`, `roles`) |
| 5 | Tablas principales (`usuarios`, `clientes`, `proveedores`, `productos`, `configuracion_negocio`) |
| 6 | Compras y sus ítems |
| 7 | Órdenes de servicio (entregas), ítems y pagos |
| 8 | Ventas directas (POS) y sus ítems |
| 9 | Facturas (`FO-XXXX` borradores, `FV-XXXX` oficiales) |
| 10 | Devoluciones y garantías |
| 11 | Movimientos de inventario + trigger de stock (va al final) |
| 12 | Auditoría — log inmutable de todas las acciones del sistema |
| 13 | Cajas, movimientos de caja y catálogos de caja |
| 14 | Vistas de analítica y reportes |
| 15 | Función de proyección de cash flow |

---

## Notas de arquitectura

- **Stock:** Nunca se modifica con `UPDATE productos SET stock = ...`. Siempre se inserta en `movimientos_inventario` y el trigger `fn_actualizar_stock` lo aplica atómicamente. Si no hay stock suficiente, la BD lanza excepción y revierte la transacción completa.
- **Historial:** `cliente_nombre` y `cliente_telefono` se guardan como snapshot en `entregas` y `ventas` para preservar el historial aunque el cliente cambie sus datos.
- **Catálogos:** Los estados y tipos son FKs a tablas catálogo, no ENUMs ni CHECKs simples. Para agregar un nuevo estado, solo se inserta una fila. Sin DDL.
- **Passwords:** Los usuarios seed deben cargarse con hash BCrypt. No guardar contraseñas en texto plano.

---

## Script SQL completo

```sql
-- ================================================================
--
--  ███████╗██████╗  ██████╗ ██████╗  ██████╗ ███████╗
--  ██╔════╝██╔══██╗██╔════╝ ██╔══██╗██╔═══██╗██╔════╝
--  █████╗  ██████╔╝██║  ███╗██████╔╝██║   ██║███████╗
--  ██╔══╝  ██╔══██╗██║   ██║██╔═══╝ ██║   ██║╚════██║
--  ███████╗██║  ██║╚██████╔╝██║     ╚██████╔╝███████║
--  ╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚═╝      ╚═════╝ ╚══════╝
--
--  Sistema POS para gestión de servicios técnicos,
--  inventario, ventas y facturación.
--
--  Base de datos: PostgreSQL (Supabase)
--  Versión: 1.0 — producción
--
--  ORDEN DE EJECUCIÓN (no reordenar, hay dependencias):
--    0. Extensiones
--    1. Funciones de triggers
--    2. Secuencias de facturación
--    3. Tablas catálogo (valores de referencia)
--    4. Tablas base (categorías, roles)
--    5. Tablas principales (usuarios, clientes, proveedores, productos, configuracion_negocio)
--    6. Compras y sus ítems
--    7. Entregas, ítems y pagos
--    8. Ventas y sus ítems
--    9. Facturas (usan las secuencias del bloque 2)
--   10. Devoluciones y garantías
--   11. Movimientos de inventario (trigger modifica productos → va al final)
--   12. Auditoría (tabla inmutable: solo INSERT, nunca UPDATE ni DELETE)
--
-- ================================================================


-- ================================================================
-- BLOQUE 0 — EXTENSIONES
--
-- pgcrypto habilita gen_random_uuid() para generar UUIDs v4.
-- En PostgreSQL 13+ la función ya es nativa, pero activar la
-- extensión con IF NOT EXISTS es seguro en cualquier versión y
-- en cualquier configuración de Supabase.
-- Sin esto, los DEFAULT gen_random_uuid() fallan en PG < 13.
-- ================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ================================================================
-- BLOQUE 1 — FUNCIONES DE TRIGGERS
--
-- Deben existir ANTES que cualquier tabla que las referencie.
-- Ambas usan CREATE OR REPLACE → idempotentes, puedes re-ejecutar
-- sin error si ya existen.
-- ================================================================

-- ----------------------------------------------------------------
-- fn_set_updated_at()
-- Se engancha en BEFORE UPDATE de todas las tablas principales.
-- Cada vez que alguien modifica una fila, PostgreSQL actualiza
-- automáticamente el campo updated_at sin que el backend
-- tenga que enviarlo explícitamente.
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ----------------------------------------------------------------
-- fn_actualizar_stock()
-- Se engancha en AFTER INSERT de movimientos_inventario.
-- Centraliza 100% la lógica de stock en la BD:
--   ENTRADA → suma al stock del producto
--   SALIDA  → resta, pero SOLO si hay suficiente stock
--
-- Si el stock es insuficiente lanza RAISE EXCEPTION con mensaje
-- descriptivo, lo que aborta la transacción completa. Así nunca
-- queda un movimiento registrado sin el stock correspondiente.
--
-- El backend (EntregaService) también valida antes de insertar,
-- pero esta función es la última línea de defensa a nivel BD.
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_actualizar_stock()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.tipo = 'ENTRADA' THEN
        -- Compra de proveedor, ajuste manual, devolución → sube stock
        UPDATE productos
        SET stock = stock + NEW.cantidad
        WHERE id = NEW.producto_id;

    ELSIF NEW.tipo = 'SALIDA' THEN
        -- Venta o servicio → baja stock, pero solo si alcanza
        UPDATE productos
        SET stock = stock - NEW.cantidad
        WHERE id = NEW.producto_id
          AND stock >= NEW.cantidad;  -- condición anti stock negativo

        -- Si no se actualizó ninguna fila, es porque no había stock
        IF NOT FOUND THEN
            RAISE EXCEPTION
                'Stock insuficiente para el producto %. Disponible: %, requerido: %',
                NEW.producto_id,
                (SELECT stock FROM productos WHERE id = NEW.producto_id),
                NEW.cantidad;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ================================================================
-- BLOQUE 2 — SECUENCIAS DE FACTURACIÓN
--
-- Deben crearse ANTES de las tablas facturas_* que las usan
-- en sus DEFAULT. En el script original este era el error: las
-- secuencias se creaban al final y el CREATE TABLE fallaba.
--
-- NO CYCLE garantiza que jamás se reutilice un número de factura,
-- incluso si la BD lleva años operando.
--   FO-0001, FO-0002 ... → facturas de orden de servicio (borrador)
--   FV-0001, FV-0002 ... → facturas de venta oficiales
-- ================================================================

CREATE SEQUENCE IF NOT EXISTS seq_facturas_orden
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS seq_facturas_venta
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;


-- ================================================================
-- BLOQUE 3 — TABLAS CATÁLOGO
--
-- Tablas de solo lectura que definen los valores válidos para
-- campos de estado y tipo en otras tablas. Se usan como FK
-- para que la BD rechace cualquier valor que no esté aquí.
--
-- Ventaja frente a un simple CHECK: si mañana agregas un estado
-- nuevo, solo insertas una fila aquí y no tocas la estructura.
-- ================================================================

-- ----------------------------------------------------------------
-- Estados del ciclo de vida de una compra a proveedor
-- ----------------------------------------------------------------
CREATE TABLE public.catalogo_estados_compra (
    codigo       CHARACTER VARYING(20) NOT NULL,
    descripcion  CHARACTER VARYING(80) NOT NULL,
    orden_visual SMALLINT              NOT NULL DEFAULT 0,
    CONSTRAINT catalogo_estados_compra_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_estados_compra (codigo, descripcion, orden_visual) VALUES
    ('PENDIENTE',  'Orden creada, no confirmada con el proveedor', 1),
    ('CONFIRMADA', 'Proveedor confirmó; productos en camino',       2),
    ('CANCELADA',  'Compra cancelada',                              3);


-- ----------------------------------------------------------------
-- Estados del ciclo de vida de una orden de servicio (entrega)
-- Flujo normal: PENDIENTE → EN_PROCESO → COMPLETADO → FINALIZADO
-- Salida de emergencia: cualquier estado → CANCELADO
-- FINALIZADO lo pone el admin tras confirmar; aplica inventario
-- ----------------------------------------------------------------
CREATE TABLE public.catalogo_estados_orden (
    codigo       CHARACTER VARYING(20) NOT NULL,
    descripcion  CHARACTER VARYING(80) NOT NULL,
    orden_visual SMALLINT              NOT NULL DEFAULT 0,
    CONSTRAINT catalogo_estados_orden_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_estados_orden (codigo, descripcion, orden_visual) VALUES
    ('PENDIENTE',  'Orden creada, sin técnico asignado',     1),
    ('EN_PROCESO', 'Técnico asignado, trabajo en curso',     2),
    ('COMPLETADO', 'Trabajo finalizado, pendiente de pago',  3),
    ('CANCELADO',  'Orden cancelada',                        4),
    ('FINALIZADO', 'Cerrado por admin — inventario aplicado',5);


-- ----------------------------------------------------------------
-- Estados de pago de una orden de servicio
-- ANTICIPO → primer pago, no cubre el total
-- PARCIAL  → más de un pago, pero aún falta
-- COMPLETO → pagado en su totalidad
-- ----------------------------------------------------------------
CREATE TABLE public.catalogo_estados_pago (
    codigo       CHARACTER VARYING(20) NOT NULL,
    descripcion  CHARACTER VARYING(80) NOT NULL,
    orden_visual SMALLINT              NOT NULL DEFAULT 0,
    CONSTRAINT catalogo_estados_pago_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_estados_pago (codigo, descripcion, orden_visual) VALUES
    ('PENDIENTE', 'Sin pagos registrados',               1),
    ('ANTICIPO',  'Se recibió el anticipo acordado',     2),
    ('PARCIAL',   'Pago parcial recibido, sin completar',3),
    ('COMPLETO',  'Orden pagada en su totalidad',        4);


-- ----------------------------------------------------------------
-- Estados de una venta directa (mostrador / POS)
-- ----------------------------------------------------------------
CREATE TABLE public.catalogo_estados_venta (
    codigo       CHARACTER VARYING(20) NOT NULL,
    descripcion  CHARACTER VARYING(80) NOT NULL,
    orden_visual SMALLINT              NOT NULL DEFAULT 0,
    CONSTRAINT catalogo_estados_venta_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_estados_venta (codigo, descripcion, orden_visual) VALUES
    ('PENDIENTE',  'Venta registrada, pago pendiente', 1),
    ('COMPLETADA', 'Venta finalizada y cobrada',        2),
    ('CANCELADA',  'Venta anulada',                     3);


-- ----------------------------------------------------------------
-- Tipos de orden de servicio
-- REPARACION  → el cliente trae el equipo a reparar
-- INSTALACION → el técnico va donde el cliente a instalar
-- ENTREGA     → despacho de un producto comprado
-- ----------------------------------------------------------------
CREATE TABLE public.catalogo_tipos_entrega (
    codigo      CHARACTER VARYING(20) NOT NULL,
    descripcion CHARACTER VARYING(80) NOT NULL,
    CONSTRAINT catalogo_tipos_entrega_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_tipos_entrega (codigo, descripcion) VALUES
    ('REPARACION',  'Reparación de equipo o mobiliario'),
    ('INSTALACION', 'Instalación en sitio del cliente'),
    ('ENTREGA',     'Despacho de producto al cliente');


-- ----------------------------------------------------------------
-- Tipos de movimiento de inventario
-- Solo dos valores posibles; el trigger fn_actualizar_stock
-- distingue cuál ejecutar basándose en este campo
-- ----------------------------------------------------------------
CREATE TABLE public.catalogo_tipos_movimiento (
    codigo      CHARACTER VARYING(10) NOT NULL,
    descripcion CHARACTER VARYING(80) NOT NULL,
    CONSTRAINT catalogo_tipos_movimiento_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_tipos_movimiento (codigo, descripcion) VALUES
    ('ENTRADA', 'Incrementa el stock del producto'),
    ('SALIDA',  'Decrementa el stock del producto');


-- ================================================================
-- BLOQUE 4 — TABLAS BASE
--
-- No dependen entre sí, pero sí son referenciadas por las tablas
-- principales del bloque siguiente.
-- ================================================================

-- ----------------------------------------------------------------
-- categorias
-- Agrupa los productos por familia.
-- ON DELETE SET NULL en productos → si borras una categoría,
-- los productos quedan sin categoría pero no se pierden.
-- ----------------------------------------------------------------
CREATE TABLE public.categorias (
    id          UUID                   NOT NULL DEFAULT gen_random_uuid(),
    nombre      CHARACTER VARYING(60)  NOT NULL,
    descripcion CHARACTER VARYING(120)     NULL,
    activo      BOOLEAN                NOT NULL DEFAULT TRUE,
    CONSTRAINT categorias_pkey       PRIMARY KEY (id),
    CONSTRAINT categorias_nombre_key UNIQUE (nombre)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_categorias_activo
    ON public.categorias USING btree (activo) TABLESPACE pg_default;

INSERT INTO public.categorias (id, nombre, descripcion, activo) VALUES
    ('06359856-9dc9-455c-a1e3-e71b5be19e41', 'Escritorio',    'Escritorios y superficies de trabajo',      TRUE),
    ('1669da6e-4308-40a3-87a6-feefb89cf195', 'Herramienta',   'Herramientas de uso técnico',                TRUE),
    ('66382854-e693-4182-9d9b-0021a7ec2d7a', 'Araña',         'Bases tipo araña para sillas',               TRUE),
    ('92797d77-4ef0-4c5d-ac1f-8daa5bc34ef5', 'Silla oficina', 'Sillas y asientos ergonómicos para oficina', TRUE),
    ('93aac1fd-5e9d-47c3-8467-5c9c5370080e', 'Guaya',         'Cables y guayas para mecanismos',            TRUE),
    ('c0acd70d-ee3e-446d-9a35-01d2f7755cde', 'Rodachín',      'Ruedas y rodachines para sillas',            TRUE),
    ('d66b3b78-8dab-493f-b592-2d54b1f0d78a', 'Cilindro',      'Cilindros neumáticos para sillas',           TRUE),
    ('efffd905-970a-45a4-9af0-c07b574494d0', 'Insumo general','Materiales de uso general',                  TRUE);


-- ----------------------------------------------------------------
-- roles
-- Define los permisos de cada usuario en el sistema:
--   ADMIN      → acceso total, puede confirmar órdenes
--   VENDEDOR   → registra ventas y gestiona clientes
--   TECNICO    → ve y gestiona sus órdenes asignadas
--   INVENTARIO → gestiona compras y movimientos de stock
-- ----------------------------------------------------------------
CREATE TABLE public.roles (
    id         UUID                  NOT NULL DEFAULT gen_random_uuid(),
    nombre     CHARACTER VARYING(50) NOT NULL,
    activo     BOOLEAN               NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT roles_pkey       PRIMARY KEY (id),
    CONSTRAINT roles_nombre_key UNIQUE (nombre)
) TABLESPACE pg_default;

INSERT INTO public.roles (id, nombre, activo, created_at) VALUES
    ('66b95c0f-3387-4da0-8fc9-ecb296551c87', 'ADMIN',      TRUE, '2026-03-24 01:27:19.01355'),
    ('a4f3a9ce-6e58-4c9a-8f9b-9bb4d24a6f10', 'VENDEDOR',   TRUE, '2026-03-24 01:27:19.01355'),
    ('137f5e5b-732c-46b3-93f5-795ae4741760', 'TECNICO',    TRUE, '2026-03-24 01:27:19.01355'),
    ('4f840f85-4f27-4f24-a954-c7f73ae69b44', 'INVENTARIO', TRUE, '2026-03-24 01:27:19.01355');


-- ================================================================
-- BLOQUE 5 — TABLAS PRINCIPALES
--
-- usuarios, clientes, proveedores, productos y configuracion_negocio.
-- Son el núcleo del sistema; casi todas las demás tablas
-- las referencian.
-- ================================================================

-- ----------------------------------------------------------------
-- usuarios
-- Los usuarios seed deben cargarse con hash BCrypt, nunca en texto plano.
--    El backend debe hashearlo con BCrypt ANTES de ir a producción.
-- ----------------------------------------------------------------
CREATE TABLE public.usuarios (
    id         UUID                   NOT NULL DEFAULT gen_random_uuid(),
    nombre     CHARACTER VARYING(120) NOT NULL,
    email      CHARACTER VARYING(120) NOT NULL,
    password   CHARACTER VARYING(255) NOT NULL,
    telefono   CHARACTER VARYING(20)      NULL,
    activo     BOOLEAN                NOT NULL DEFAULT TRUE,
    rol_id     UUID                   NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT usuarios_pkey        PRIMARY KEY (id),
    CONSTRAINT usuarios_email_key   UNIQUE (email),
    CONSTRAINT usuarios_rol_id_fkey FOREIGN KEY (rol_id)
        REFERENCES public.roles (id) ON DELETE RESTRICT
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_usuarios_rol    ON public.usuarios USING btree (rol_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_usuarios_activo ON public.usuarios USING btree (activo) TABLESPACE pg_default;

CREATE TRIGGER trg_usuarios_updated_at
    BEFORE UPDATE ON public.usuarios
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ⚠️ Cambiar password con BCrypt antes de producción real
INSERT INTO public.usuarios (id, nombre, email, password, telefono, activo, rol_id, created_at, updated_at) VALUES
    ('b2e3b18c-66f7-4076-8847-23ac9f01ee3e', 'Admin Principal', 'admin@test.com',
     '<BCrypt hash>', NULL, TRUE, '66b95c0f-3387-4da0-8fc9-ecb296551c87',
     '2026-03-24 01:27:19.01355', '2026-03-24 01:27:19.01355'),
    ('e3c6af64-c4b7-4a0f-a92f-86a5584b6ab0', 'VENDEDOR', 'vendedor@test.com',
     '<BCrypt hash>', '3000000001', TRUE, 'a4f3a9ce-6e58-4c9a-8f9b-9bb4d24a6f10',
     '2026-03-24 01:27:19.01355', '2026-03-24 01:27:19.01355');


-- ----------------------------------------------------------------
-- clientes
-- telefono y email son UNIQUE pero nullable: PostgreSQL permite
-- múltiples NULLs en columnas UNIQUE.
-- El backend debe enviar NULL (no string vacío "") cuando no hay dato.
-- ----------------------------------------------------------------
CREATE TABLE public.clientes (
    id         UUID                   NOT NULL DEFAULT gen_random_uuid(),
    nombre     CHARACTER VARYING(120) NOT NULL,
    documento  CHARACTER VARYING(30)      NULL,
    telefono   CHARACTER VARYING(20)      NULL,
    email      CHARACTER VARYING(120)     NULL,
    direccion  CHARACTER VARYING(200)     NULL,
    barrio     CHARACTER VARYING(80)      NULL,
    ciudad     CHARACTER VARYING(80)  NOT NULL DEFAULT 'Cali',
    notas      TEXT                       NULL,
    activo     BOOLEAN               NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT clientes_pkey         PRIMARY KEY (id),
    CONSTRAINT clientes_email_key    UNIQUE (email),
    CONSTRAINT clientes_telefono_key UNIQUE (telefono)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_clientes_nombre ON public.clientes USING btree (nombre) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_clientes_activo ON public.clientes USING btree (activo) TABLESPACE pg_default;

CREATE TRIGGER trg_clientes_updated_at
    BEFORE UPDATE ON public.clientes
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();


-- ----------------------------------------------------------------
-- proveedores
-- ----------------------------------------------------------------
CREATE TABLE public.proveedores (
    id         UUID                   NOT NULL DEFAULT gen_random_uuid(),
    nombre     CHARACTER VARYING(120) NOT NULL,
    nit        CHARACTER VARYING(30)      NULL,
    telefono   CHARACTER VARYING(20)      NULL,
    email      CHARACTER VARYING(120)     NULL,
    direccion  CHARACTER VARYING(200)     NULL,
    ciudad     CHARACTER VARYING(80)  NOT NULL DEFAULT 'Cali',
    contacto   CHARACTER VARYING(80)      NULL,
    notas      TEXT                       NULL,
    activo     BOOLEAN               NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT proveedores_pkey    PRIMARY KEY (id),
    CONSTRAINT proveedores_nit_key UNIQUE (nit)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_proveedores_nombre ON public.proveedores USING btree (nombre) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_proveedores_activo ON public.proveedores USING btree (activo) TABLESPACE pg_default;

CREATE TRIGGER trg_proveedores_updated_at
    BEFORE UPDATE ON public.proveedores
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();


-- ----------------------------------------------------------------
-- productos
-- stock NUNCA se modifica directamente con UPDATE.
-- Siempre vía movimientos_inventario + trigger.
-- ----------------------------------------------------------------
CREATE TABLE public.productos (
    id           UUID                   NOT NULL DEFAULT gen_random_uuid(),
    codigo       CHARACTER VARYING(30)  NOT NULL,
    nombre       CHARACTER VARYING(120) NOT NULL,
    descripcion  CHARACTER VARYING(255)     NULL,
    categoria_id UUID                       NULL,
    precio       NUMERIC(10, 2)         NOT NULL,
    stock        INTEGER                NOT NULL DEFAULT 0,
    stock_minimo INTEGER                NOT NULL DEFAULT 5,
    activo       BOOLEAN                NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT productos_pkey               PRIMARY KEY (id),
    CONSTRAINT productos_codigo_key         UNIQUE (codigo),
    CONSTRAINT productos_categoria_fkey     FOREIGN KEY (categoria_id)
        REFERENCES public.categorias (id) ON DELETE SET NULL,
    CONSTRAINT productos_precio_check       CHECK (precio >= 0),
    CONSTRAINT productos_stock_check        CHECK (stock >= 0),
    CONSTRAINT productos_stock_minimo_check CHECK (stock_minimo >= 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_productos_codigo    ON public.productos USING btree (codigo)       TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_productos_categoria ON public.productos USING btree (categoria_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_productos_activo    ON public.productos USING btree (activo)       TABLESPACE pg_default;

CREATE TRIGGER trg_productos_updated_at
    BEFORE UPDATE ON public.productos
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();


-- ----------------------------------------------------------------
-- configuracion_negocio
-- Parametros editables del negocio usados por la aplicacion
-- para datos de factura, textos, valores operativos y ajustes.
-- ----------------------------------------------------------------
CREATE TABLE public.configuracion_negocio (
    clave       CHARACTER VARYING(50)  NOT NULL,
    valor       TEXT                   NOT NULL,
    categoria   CHARACTER VARYING(30)  NOT NULL,
    descripcion CHARACTER VARYING(255)     NULL,
    updated_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT configuracion_negocio_pkey PRIMARY KEY (clave)
) TABLESPACE pg_default;


-- ================================================================
-- BLOQUE 6 — COMPRAS A PROVEEDORES
-- ================================================================

CREATE TABLE public.compras (
    id             UUID                   NOT NULL DEFAULT gen_random_uuid(),
    proveedor_id   UUID                   NOT NULL,
    comprador_id   UUID                       NULL,
    numero_factura CHARACTER VARYING(40)      NULL,
    estado         CHARACTER VARYING(20)  NOT NULL DEFAULT 'PENDIENTE',
    total          NUMERIC(10, 2)         NOT NULL DEFAULT 0,
    notas          TEXT                       NULL,
    fecha          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT compras_pkey           PRIMARY KEY (id),
    CONSTRAINT compras_proveedor_fkey FOREIGN KEY (proveedor_id)
        REFERENCES public.proveedores (id) ON DELETE RESTRICT,
    CONSTRAINT compras_comprador_fkey FOREIGN KEY (comprador_id)
        REFERENCES public.usuarios (id) ON DELETE SET NULL,
    CONSTRAINT compras_estado_fkey    FOREIGN KEY (estado)
        REFERENCES public.catalogo_estados_compra (codigo) ON DELETE RESTRICT,
    CONSTRAINT compras_total_check    CHECK (total >= 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_compras_proveedor ON public.compras USING btree (proveedor_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_compras_estado    ON public.compras USING btree (estado)       TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_compras_fecha     ON public.compras USING btree (fecha DESC)   TABLESPACE pg_default;

CREATE TRIGGER trg_compras_updated_at
    BEFORE UPDATE ON public.compras
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();


CREATE TABLE public.compra_items (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    compra_id       UUID           NOT NULL,
    producto_id     UUID           NOT NULL,
    cantidad        INTEGER        NOT NULL,
    precio_unitario NUMERIC(10, 2) NOT NULL,
    CONSTRAINT compra_items_pkey                  PRIMARY KEY (id),
    CONSTRAINT compra_items_compra_fkey           FOREIGN KEY (compra_id)
        REFERENCES public.compras (id) ON DELETE CASCADE,
    CONSTRAINT compra_items_producto_fkey         FOREIGN KEY (producto_id)
        REFERENCES public.productos (id) ON DELETE RESTRICT,
    CONSTRAINT compra_items_cantidad_check        CHECK (cantidad > 0),
    CONSTRAINT compra_items_precio_unitario_check CHECK (precio_unitario >= 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_compra_items_compra   ON public.compra_items USING btree (compra_id)   TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_compra_items_producto ON public.compra_items USING btree (producto_id) TABLESPACE pg_default;


-- ================================================================
-- BLOQUE 7 — ÓRDENES DE SERVICIO (ENTREGAS)
--
-- Flujo: PENDIENTE → EN_PROCESO → COMPLETADO → FINALIZADO
-- FINALIZADO = admin confirma → descuenta inventario
-- ================================================================

CREATE TABLE public.entregas (
    id                  UUID                   NOT NULL DEFAULT gen_random_uuid(),
    cliente_nombre      CHARACTER VARYING(120) NOT NULL,
    cliente_telefono    CHARACTER VARYING(20)      NULL,
    cliente_id          UUID                       NULL,
    tipo                CHARACTER VARYING(20)  NOT NULL DEFAULT 'REPARACION',
    descripcion_problema TEXT                      NULL,
    direccion           CHARACTER VARYING(200) NOT NULL,
    estado              CHARACTER VARYING(20)  NOT NULL DEFAULT 'PENDIENTE',
    tecnico_id          UUID                       NULL,
    creado_por          UUID                       NULL,
    notas_tecnico       CHARACTER VARYING(500)     NULL,
    fecha_creacion      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_entrega       TIMESTAMP WITHOUT TIME ZONE    NULL,
    fecha_completado    TIMESTAMP WITHOUT TIME ZONE    NULL,
    mano_obra           NUMERIC(10, 2)         NOT NULL DEFAULT 0,
    total_orden         NUMERIC(10, 2)         NOT NULL DEFAULT 0,
    anticipo_porcentaje NUMERIC(5, 2)          NOT NULL DEFAULT 0,
    anticipo_recibido   NUMERIC(10, 2)         NOT NULL DEFAULT 0,
    estado_pago         CHARACTER VARYING(20)  NOT NULL DEFAULT 'PENDIENTE',
    updated_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT entregas_pkey                      PRIMARY KEY (id),
    CONSTRAINT entregas_cliente_fkey              FOREIGN KEY (cliente_id)
        REFERENCES public.clientes (id) ON DELETE SET NULL,
    CONSTRAINT entregas_tecnico_fkey              FOREIGN KEY (tecnico_id)
        REFERENCES public.usuarios (id) ON DELETE SET NULL,
    CONSTRAINT entregas_creado_por_fkey           FOREIGN KEY (creado_por)
        REFERENCES public.usuarios (id) ON DELETE SET NULL,
    CONSTRAINT entregas_tipo_fkey                 FOREIGN KEY (tipo)
        REFERENCES public.catalogo_tipos_entrega (codigo) ON DELETE RESTRICT,
    CONSTRAINT entregas_estado_fkey               FOREIGN KEY (estado)
        REFERENCES public.catalogo_estados_orden (codigo) ON DELETE RESTRICT,
    CONSTRAINT entregas_estado_pago_fkey          FOREIGN KEY (estado_pago)
        REFERENCES public.catalogo_estados_pago (codigo) ON DELETE RESTRICT,
    CONSTRAINT entregas_total_orden_check         CHECK (total_orden >= 0),
    CONSTRAINT entregas_mano_obra_check           CHECK (mano_obra >= 0),
    CONSTRAINT entregas_anticipo_recibido_check   CHECK (anticipo_recibido >= 0),
    CONSTRAINT entregas_anticipo_porcentaje_check CHECK (
        anticipo_porcentaje >= 0 AND anticipo_porcentaje <= 100
    )
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_entregas_cliente    ON public.entregas USING btree (cliente_id)     TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_entregas_tecnico    ON public.entregas USING btree (tecnico_id)     TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_entregas_estado     ON public.entregas USING btree (estado)         TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_entregas_estado_pago ON public.entregas USING btree (estado_pago)  TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_entregas_tipo       ON public.entregas USING btree (tipo)           TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_entregas_fecha      ON public.entregas USING btree (fecha_creacion DESC) TABLESPACE pg_default;

CREATE TRIGGER trg_entregas_updated_at
    BEFORE UPDATE ON public.entregas
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();


CREATE TABLE public.entrega_items (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    entrega_id      UUID           NOT NULL,
    producto_id     UUID           NOT NULL,
    cantidad        INTEGER        NOT NULL DEFAULT 1,
    precio_unitario NUMERIC(10, 2) NOT NULL DEFAULT 0,
    CONSTRAINT entrega_items_pkey                 PRIMARY KEY (id),
    CONSTRAINT entrega_items_entrega_fk           FOREIGN KEY (entrega_id)
        REFERENCES public.entregas (id) ON DELETE CASCADE,
    CONSTRAINT entrega_items_producto_fk          FOREIGN KEY (producto_id)
        REFERENCES public.productos (id) ON DELETE RESTRICT,
    CONSTRAINT entrega_items_cantidad_check        CHECK (cantidad >= 1),
    CONSTRAINT entrega_items_precio_unitario_check CHECK (precio_unitario >= 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_entrega_items_entrega  ON public.entrega_items USING btree (entrega_id)  TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_entrega_items_producto ON public.entrega_items USING btree (producto_id) TABLESPACE pg_default;


-- ----------------------------------------------------------------
-- pagos_orden — pagos parciales o totales de una orden
-- ----------------------------------------------------------------
CREATE TABLE public.pagos_orden (
    id         UUID           NOT NULL DEFAULT gen_random_uuid(),
    entrega_id UUID           NOT NULL,
    usuario_id UUID               NULL,
    monto      NUMERIC(10, 2) NOT NULL,
    notas      CHARACTER VARYING(255) NULL,
    fecha      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pagos_orden_pkey       PRIMARY KEY (id),
    CONSTRAINT pagos_orden_entrega_fk FOREIGN KEY (entrega_id)
        REFERENCES public.entregas (id) ON DELETE CASCADE,
    CONSTRAINT pagos_orden_usuario_fk FOREIGN KEY (usuario_id)
        REFERENCES public.usuarios (id) ON DELETE SET NULL,
    CONSTRAINT pagos_orden_monto_check CHECK (monto > 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_pagos_orden_entrega ON public.pagos_orden USING btree (entrega_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_pagos_orden_fecha   ON public.pagos_orden USING btree (fecha DESC) TABLESPACE pg_default;


-- ================================================================
-- BLOQUE 8 — VENTAS DIRECTAS (POS / MOSTRADOR)
-- ================================================================

CREATE TABLE public.ventas (
    id               UUID                   NOT NULL DEFAULT gen_random_uuid(),
    cliente_nombre   CHARACTER VARYING(120) NOT NULL,
    cliente_telefono CHARACTER VARYING(20)      NULL,
    cliente_id       UUID                       NULL,
    vendedor_id      UUID                       NULL,
    estado           CHARACTER VARYING(20)  NOT NULL DEFAULT 'COMPLETADA',
    total            NUMERIC(10, 2)         NOT NULL DEFAULT 0,
    fecha            TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ventas_pkey          PRIMARY KEY (id),
    CONSTRAINT ventas_cliente_fkey  FOREIGN KEY (cliente_id)
        REFERENCES public.clientes (id) ON DELETE SET NULL,
    CONSTRAINT ventas_vendedor_fkey FOREIGN KEY (vendedor_id)
        REFERENCES public.usuarios (id) ON DELETE SET NULL,
    CONSTRAINT ventas_estado_fkey   FOREIGN KEY (estado)
        REFERENCES public.catalogo_estados_venta (codigo) ON DELETE RESTRICT,
    CONSTRAINT ventas_total_check   CHECK (total >= 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_ventas_cliente  ON public.ventas USING btree (cliente_id)  TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_ventas_vendedor ON public.ventas USING btree (vendedor_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_ventas_estado   ON public.ventas USING btree (estado)      TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_ventas_fecha    ON public.ventas USING btree (fecha DESC)  TABLESPACE pg_default;

CREATE TRIGGER trg_ventas_updated_at
    BEFORE UPDATE ON public.ventas
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();


CREATE TABLE public.venta_items (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    venta_id        UUID           NOT NULL,
    producto_id     UUID           NOT NULL,
    cantidad        INTEGER        NOT NULL,
    precio_unitario NUMERIC(10, 2) NOT NULL,
    CONSTRAINT venta_items_pkey                  PRIMARY KEY (id),
    CONSTRAINT venta_items_venta_fkey            FOREIGN KEY (venta_id)
        REFERENCES public.ventas (id) ON DELETE CASCADE,
    CONSTRAINT venta_items_producto_fkey         FOREIGN KEY (producto_id)
        REFERENCES public.productos (id) ON DELETE RESTRICT,
    CONSTRAINT venta_items_cantidad_check        CHECK (cantidad > 0),
    CONSTRAINT venta_items_precio_unitario_check CHECK (precio_unitario >= 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_venta_items_venta   ON public.venta_items USING btree (venta_id)    TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_venta_items_producto ON public.venta_items USING btree (producto_id) TABLESPACE pg_default;


-- ================================================================
-- BLOQUE 9 — FACTURACIÓN
--
-- FO-XXXX → borrador (se genera al COMPLETAR la orden)
-- FV-XXXX → oficial  (se genera al FINALIZAR / confirmar)
-- UNIQUE en entrega_id / venta_id → una sola factura por operación
-- ================================================================

CREATE TABLE public.facturas_orden (
    id            UUID                  NOT NULL DEFAULT gen_random_uuid(),
    entrega_id    UUID                  NOT NULL,
    numero        CHARACTER VARYING(20) NOT NULL DEFAULT (
                      'FO-' || lpad(nextval('seq_facturas_orden')::text, 4, '0')
                  ),
    subtotal      NUMERIC(10, 2)        NOT NULL DEFAULT 0,
    impuesto      NUMERIC(10, 2)        NOT NULL DEFAULT 0,
    total         NUMERIC(10, 2)        NOT NULL DEFAULT 0,
    notas         TEXT                      NULL,
    fecha_emision TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT facturas_orden_pkey           PRIMARY KEY (id),
    CONSTRAINT facturas_orden_entrega_id_key UNIQUE (entrega_id),
    CONSTRAINT facturas_orden_numero_key     UNIQUE (numero),
    CONSTRAINT facturas_orden_entrega_fk     FOREIGN KEY (entrega_id)
        REFERENCES public.entregas (id) ON DELETE CASCADE,
    CONSTRAINT facturas_orden_subtotal_check CHECK (subtotal >= 0),
    CONSTRAINT facturas_orden_impuesto_check CHECK (impuesto >= 0),
    CONSTRAINT facturas_orden_total_check    CHECK (total    >= 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_facturas_orden_entrega ON public.facturas_orden USING btree (entrega_id) TABLESPACE pg_default;


CREATE TABLE public.facturas_venta (
    id            UUID                  NOT NULL DEFAULT gen_random_uuid(),
    venta_id      UUID                  NOT NULL,
    numero        CHARACTER VARYING(20) NOT NULL DEFAULT (
                      'FV-' || lpad(nextval('seq_facturas_venta')::text, 4, '0')
                  ),
    subtotal      NUMERIC(10, 2)        NOT NULL DEFAULT 0,
    impuesto      NUMERIC(10, 2)        NOT NULL DEFAULT 0,
    total         NUMERIC(10, 2)        NOT NULL DEFAULT 0,
    notas         TEXT                      NULL,
    fecha_emision TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT facturas_venta_pkey           PRIMARY KEY (id),
    CONSTRAINT facturas_venta_numero_key     UNIQUE (numero),
    CONSTRAINT facturas_venta_venta_id_key   UNIQUE (venta_id),
    CONSTRAINT facturas_venta_venta_fk       FOREIGN KEY (venta_id)
        REFERENCES public.ventas (id) ON DELETE CASCADE,
    CONSTRAINT facturas_venta_subtotal_check CHECK (subtotal >= 0),
    CONSTRAINT facturas_venta_impuesto_check CHECK (impuesto >= 0),
    CONSTRAINT facturas_venta_total_check    CHECK (total    >= 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_facturas_venta_venta ON public.facturas_venta USING btree (venta_id) TABLESPACE pg_default;


-- ================================================================
-- BLOQUE 10 — DEVOLUCIONES Y GARANTÍAS
--
-- Registra devoluciones de ventas/entregas y garantías sin mezclar
-- este flujo con la cancelación normal de una venta u orden.
-- Cada registro guarda razón, acción sobre el dinero, cliente,
-- usuario que lo registró y los productos afectados.
--
-- Reglas clave:
--   - Debe venir de una venta O de una entrega, nunca ambas.
--   - El historial se conserva aunque cambie el cliente.
--   - Los ítems permiten sumar stock mediante movimientos con
--     origen_tipo = 'DEVOLUCION' y origen_id = devoluciones_garantias.id.
--   - Anular una devolución/garantía debe crear movimiento contrario,
--     no borrar el registro histórico.
-- ================================================================

CREATE TABLE public.catalogo_tipos_devolucion (
    codigo      CHARACTER VARYING(20) NOT NULL,
    descripcion CHARACTER VARYING(80) NOT NULL,
    CONSTRAINT catalogo_tipos_devolucion_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_tipos_devolucion (codigo, descripcion) VALUES
    ('DEVOLUCION', 'Devolucion de producto facturado'),
    ('GARANTIA',   'Atencion por garantia sin reembolso');


CREATE TABLE public.catalogo_estados_devolucion (
    codigo       CHARACTER VARYING(20) NOT NULL,
    descripcion  CHARACTER VARYING(80) NOT NULL,
    orden_visual SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT catalogo_estados_devolucion_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_estados_devolucion (codigo, descripcion, orden_visual) VALUES
    ('REGISTRADA', 'Registro aplicado al inventario y trazabilidad', 1),
    ('ANULADA',    'Registro reversado con movimiento contrario',    2);


CREATE TABLE public.catalogo_acciones_dinero_devolucion (
    codigo      CHARACTER VARYING(20) NOT NULL,
    descripcion CHARACTER VARYING(80) NOT NULL,
    CONSTRAINT catalogo_acciones_dinero_devolucion_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_acciones_dinero_devolucion (codigo, descripcion) VALUES
    ('REEMBOLSO',     'Se devuelve dinero al cliente'),
    ('SALDO_FAVOR',   'Se deja saldo a favor del cliente'),
    ('SIN_REEMBOLSO', 'No hay movimiento de dinero');


CREATE TABLE public.devoluciones_garantias (
    id              UUID                  NOT NULL DEFAULT gen_random_uuid(),
    tipo            CHARACTER VARYING(20) NOT NULL DEFAULT 'DEVOLUCION',
    estado          CHARACTER VARYING(20) NOT NULL DEFAULT 'REGISTRADA',
    venta_id        UUID                      NULL,
    entrega_id      UUID                      NULL,
    cliente_id      UUID                      NULL,
    cliente_nombre  CHARACTER VARYING(120)    NULL,
    razon           TEXT                  NOT NULL,
    accion_dinero   CHARACTER VARYING(20) NOT NULL DEFAULT 'REEMBOLSO',
    monto_devuelto  NUMERIC(10, 2)        NOT NULL DEFAULT 0,
    notas           TEXT                      NULL,
    registrado_por  UUID                      NULL,
    fecha           TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT devoluciones_garantias_pkey PRIMARY KEY (id),
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


CREATE TABLE public.devolucion_garantia_items (
    id              UUID                   NOT NULL DEFAULT gen_random_uuid(),
    devolucion_id   UUID                   NOT NULL,
    producto_id     UUID                   NOT NULL,
    cantidad        INTEGER                NOT NULL,
    precio_unitario NUMERIC(10, 2)         NOT NULL DEFAULT 0,
    motivo_item     CHARACTER VARYING(255)     NULL,
    CONSTRAINT devolucion_garantia_items_pkey PRIMARY KEY (id),
    CONSTRAINT devolucion_garantia_items_devolucion_fkey FOREIGN KEY (devolucion_id)
        REFERENCES public.devoluciones_garantias (id) ON DELETE CASCADE,
    CONSTRAINT devolucion_garantia_items_producto_fkey FOREIGN KEY (producto_id)
        REFERENCES public.productos (id) ON DELETE RESTRICT,
    CONSTRAINT devolucion_garantia_items_cantidad_check CHECK (cantidad > 0),
    CONSTRAINT devolucion_garantia_items_precio_check CHECK (precio_unitario >= 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_devoluciones_venta ON public.devoluciones_garantias USING btree (venta_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_devoluciones_entrega ON public.devoluciones_garantias USING btree (entrega_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_devoluciones_fecha ON public.devoluciones_garantias USING btree (fecha DESC) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_devoluciones_cliente ON public.devoluciones_garantias USING btree (cliente_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_devolucion_items_devolucion ON public.devolucion_garantia_items USING btree (devolucion_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_devolucion_items_producto ON public.devolucion_garantia_items USING btree (producto_id) TABLESPACE pg_default;

CREATE TRIGGER trg_devoluciones_garantias_updated_at
    BEFORE UPDATE ON public.devoluciones_garantias
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();


-- ================================================================
-- BLOQUE 11 — MOVIMIENTOS DE INVENTARIO
--
-- VA AL FINAL: el trigger fn_actualizar_stock modifica productos,
-- que ya debe existir. Cada fila es inmutable (evento histórico).
--
-- origen_tipo + origen_id → trazabilidad completa:
--   VENTA    → ventas.id
--   ENTREGA  → entregas.id
--   COMPRA   → compras.id
--   DEVOLUCION → devoluciones_garantias.id
--   AJUSTE   → corrección manual con justificación
--   MANUAL   → entrada/salida sin operación asociada
-- ================================================================

CREATE TABLE public.movimientos_inventario (
    id           UUID                   NOT NULL DEFAULT gen_random_uuid(),
    producto_id  UUID                   NOT NULL,
    tipo         CHARACTER VARYING(10)  NOT NULL,
    cantidad     INTEGER                NOT NULL,
    proveedor_id UUID                       NULL,
    usuario_id   UUID                       NULL,
    observacion  CHARACTER VARYING(255)     NULL,
    origen_tipo  CHARACTER VARYING(20)      NULL,
    origen_id    UUID                       NULL,
    fecha        TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT movimientos_inventario_pkey    PRIMARY KEY (id),
    CONSTRAINT mov_producto_fkey  FOREIGN KEY (producto_id)
        REFERENCES public.productos (id) ON DELETE RESTRICT,
    CONSTRAINT mov_proveedor_fkey FOREIGN KEY (proveedor_id)
        REFERENCES public.proveedores (id) ON DELETE SET NULL,
    CONSTRAINT mov_tipo_fkey      FOREIGN KEY (tipo)
        REFERENCES public.catalogo_tipos_movimiento (codigo) ON DELETE RESTRICT,
    CONSTRAINT mov_usuario_fkey   FOREIGN KEY (usuario_id)
        REFERENCES public.usuarios (id) ON DELETE SET NULL,
    CONSTRAINT movimientos_inventario_cantidad_check CHECK (cantidad > 0),
    CONSTRAINT mov_origen_tipo_check CHECK (
        origen_tipo IS NULL OR
        origen_tipo IN ('VENTA', 'COMPRA', 'ENTREGA', 'DEVOLUCION', 'AJUSTE', 'MANUAL')
    )
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_mov_origen   ON public.movimientos_inventario USING btree (origen_tipo, origen_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_mov_producto ON public.movimientos_inventario USING btree (producto_id)            TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_mov_proveedor ON public.movimientos_inventario USING btree (proveedor_id)          TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_mov_fecha    ON public.movimientos_inventario USING btree (fecha DESC)             TABLESPACE pg_default;

-- AFTER INSERT → si la función lanza excepción, el INSERT también se revierte
CREATE TRIGGER trg_actualizar_stock
    AFTER INSERT ON public.movimientos_inventario
    FOR EACH ROW EXECUTE FUNCTION fn_actualizar_stock();


-- ================================================================
-- BLOQUE 12 — AUDITORÍA
--
-- Tabla inmutable: solo INSERT, nunca UPDATE ni DELETE.
-- Registra toda acción relevante del sistema con quién la hizo,
-- qué módulo fue y el detalle del evento.
--
-- Relación con usuarios: ON DELETE SET NULL para que el log
-- sobreviva aunque se elimine (desactive) un usuario.
-- El campo usuario_nombre es un snapshot del nombre en el momento
-- del evento; no se pierde si el usuario luego cambia su nombre.
--
-- Valores actuales del campo "modulo":
--   ORDENES    → órdenes de servicio (entregas)
--   VENTAS     → ventas directas al mostrador
--   COMPRAS    → compras a proveedores
--   INVENTARIO → movimientos manuales de stock
--   DEVOLUCIONES → devoluciones y garantías
--   USUARIOS   → creación y edición de usuarios
--
-- Valores actuales del campo "accion":
--   CREAR_ORDEN        → nueva orden de servicio
--   CAMBIAR_ESTADO     → cambio de estado de una orden
--   CONFIRMAR_ORDEN    → admin finaliza orden, descuenta inventario
--   REGISTRAR_PAGO     → pago sobre una orden
--   CREAR_VENTA        → nueva venta directa
--   CANCELAR_VENTA     → anulación de venta
--   CREAR_USUARIO      → nuevo usuario del sistema
--   EDITAR_USUARIO     → modificación de usuario existente
--   ENTRADA_INVENTARIO → entrada manual de stock
--   SALIDA_INVENTARIO  → salida manual de stock
--   CREAR_COMPRA       → nueva orden de compra a proveedor (borrador)
--   CONFIRMAR_COMPRA   → compra confirmada, stock incrementado
--   CANCELAR_COMPRA    → compra cancelada antes de confirmarse
--   REGISTRAR_DEVOLUCION → nueva devolución con impacto en stock/dinero
--   REGISTRAR_GARANTIA   → nueva garantía registrada sin reembolso
--   ANULAR_DEVOLUCION    → reverso histórico de devolución o garantía
-- ================================================================

CREATE TABLE public.auditoria (
    id            UUID                   NOT NULL DEFAULT gen_random_uuid(),
    usuario_id    UUID                       NULL,            -- NULL si fue el sistema
    usuario_nombre CHARACTER VARYING(120)   NULL,            -- snapshot: no cambia si el usuario cambia nombre
    accion        CHARACTER VARYING(60)  NOT NULL,            -- ver valores de accion arriba
    modulo        CHARACTER VARYING(30)  NOT NULL,            -- ver valores de modulo arriba
    entidad_tipo  CHARACTER VARYING(40)      NULL,            -- tipo legible: VENTA, ENTREGA, PRODUCTO...
    entidad_id    UUID                       NULL,            -- ID del objeto afectado (venta, orden, usuario...)
    resultado     CHARACTER VARYING(20)  NOT NULL DEFAULT 'EXITOSO',
    severidad     CHARACTER VARYING(10)  NOT NULL DEFAULT 'INFO',
    detalle       TEXT                       NULL,            -- descripción legible: "Venta $45.000 a Juan Pérez"
    ip_origen     CHARACTER VARYING(45)      NULL,            -- IP cliente o proxy principal
    user_agent    CHARACTER VARYING(255)     NULL,            -- navegador/app que hizo la petición
    metodo_http   CHARACTER VARYING(10)      NULL,            -- GET, POST, PUT, DELETE...
    ruta          CHARACTER VARYING(255)     NULL,            -- endpoint usado
    estado_anterior JSONB                    NULL,            -- snapshot antes del cambio
    estado_nuevo    JSONB                    NULL,            -- snapshot después del cambio
    metadata        JSONB                    NULL,            -- datos estructurados adicionales
    fecha         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT auditoria_pkey        PRIMARY KEY (id),
    CONSTRAINT auditoria_resultado_check CHECK (resultado IN ('EXITOSO', 'FALLIDO', 'DENEGADO')),
    CONSTRAINT auditoria_severidad_check CHECK (severidad IN ('INFO', 'WARN', 'ERROR', 'CRITICO')),
    CONSTRAINT auditoria_usuario_fk  FOREIGN KEY (usuario_id)
        REFERENCES public.usuarios (id) ON DELETE SET NULL
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_auditoria_usuario ON public.auditoria USING btree (usuario_id)  TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_auditoria_modulo  ON public.auditoria USING btree (modulo)      TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_auditoria_accion  ON public.auditoria USING btree (accion)      TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_auditoria_fecha   ON public.auditoria USING btree (fecha DESC)  TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_auditoria_usuario_fecha ON public.auditoria USING btree (usuario_id, fecha DESC) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_auditoria_entidad ON public.auditoria USING btree (entidad_tipo, entidad_id) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_auditoria_resultado_fecha ON public.auditoria USING btree (resultado, fecha DESC) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_auditoria_severidad_fecha ON public.auditoria USING btree (severidad, fecha DESC) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_auditoria_ruta_fecha ON public.auditoria USING btree (ruta, fecha DESC) TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_auditoria_metadata_gin ON public.auditoria USING gin (metadata) TABLESPACE pg_default;

CREATE OR REPLACE FUNCTION public.fn_auditoria_bloquear_modificaciones()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'La tabla auditoria es inmutable: use INSERT para registrar nuevos eventos';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_no_update_delete
    BEFORE UPDATE OR DELETE ON public.auditoria
    FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_bloquear_modificaciones();


-- ================================================================
-- BLOQUE 13 — CAJAS Y MOVIMIENTOS DE CAJA
--
-- Sistema de gestión de efectivo diario. Usa catálogos para estados
-- y tipos de movimiento, manteniendo el mismo patrón del esquema:
-- valores fijos como FK, no ENUMs ni strings libres.
-- ================================================================

CREATE TABLE IF NOT EXISTS public.catalogo_estados_caja (
    codigo       CHARACTER VARYING(20) NOT NULL,
    descripcion  CHARACTER VARYING(80) NOT NULL,
    orden_visual SMALLINT              NOT NULL DEFAULT 0,
    CONSTRAINT catalogo_estados_caja_pkey PRIMARY KEY (codigo)
) TABLESPACE pg_default;

INSERT INTO public.catalogo_estados_caja (codigo, descripcion, orden_visual)
VALUES
    ('ABIERTA', 'Caja abierta, recibiendo transacciones', 1),
    ('CERRADA', 'Caja cerrada y contabilizada',           2),
    ('ANULADA', 'Caja anulada sin aplicar movimientos',   3)
ON CONFLICT (codigo) DO NOTHING;


CREATE TABLE IF NOT EXISTS public.catalogo_tipos_movimiento_caja (
    codigo       CHARACTER VARYING(20) NOT NULL,
    descripcion  CHARACTER VARYING(80) NOT NULL,
    afecta_saldo CHARACTER VARYING(10) NOT NULL,
    orden_visual SMALLINT              NOT NULL DEFAULT 0,
    CONSTRAINT catalogo_tipos_movimiento_caja_pkey PRIMARY KEY (codigo),
    CONSTRAINT catalogo_tipos_movimiento_caja_afecta_check
        CHECK (afecta_saldo IN ('INGRESO', 'EGRESO', 'NEUTRO'))
) TABLESPACE pg_default;

INSERT INTO public.catalogo_tipos_movimiento_caja
    (codigo, descripcion, afecta_saldo, orden_visual)
VALUES
    ('INGRESO',    'Ingreso manual de efectivo',       'INGRESO', 1),
    ('PAGO_VENTA', 'Pago recibido por venta directa',  'INGRESO', 2),
    ('PAGO_ORDEN', 'Pago recibido por orden servicio', 'INGRESO', 3),
    ('EGRESO',     'Egreso manual de efectivo',        'EGRESO',  4),
    ('COMPRA',     'Pago o salida por compra',         'EGRESO',  5),
    ('AJUSTE',     'Ajuste operativo de caja',         'NEUTRO',  6),
    ('DEVOLUCION', 'Salida por devolucion o garantia', 'EGRESO',  7)
ON CONFLICT (codigo) DO NOTHING;


CREATE TABLE IF NOT EXISTS public.cajas (
    id             UUID                   NOT NULL DEFAULT gen_random_uuid(),
    usuario_id     UUID                   NOT NULL,
    monto_inicial  NUMERIC(10, 2)         NOT NULL DEFAULT 0,
    monto_final    NUMERIC(10, 2)             NULL,
    estado         CHARACTER VARYING(20)  NOT NULL DEFAULT 'ABIERTA',
    fecha_apertura TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre   TIMESTAMP WITHOUT TIME ZONE     NULL,
    notas          TEXT                       NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cajas_pkey PRIMARY KEY (id),
    CONSTRAINT cajas_usuario_fkey FOREIGN KEY (usuario_id)
        REFERENCES public.usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT cajas_estado_fkey FOREIGN KEY (estado)
        REFERENCES public.catalogo_estados_caja (codigo) ON DELETE RESTRICT,
    CONSTRAINT cajas_monto_inicial_check CHECK (monto_inicial >= 0),
    CONSTRAINT cajas_monto_final_check CHECK (monto_final IS NULL OR monto_final >= 0),
    CONSTRAINT cajas_fecha_cierre_check CHECK (
        fecha_cierre IS NULL OR fecha_cierre >= fecha_apertura
    )
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_cajas_usuario_id
    ON public.cajas USING btree (usuario_id) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_cajas_estado
    ON public.cajas USING btree (estado) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_cajas_fecha_apertura
    ON public.cajas USING btree (fecha_apertura DESC) TABLESPACE pg_default;

DROP TRIGGER IF EXISTS trg_cajas_updated_at ON public.cajas;
CREATE TRIGGER trg_cajas_updated_at
    BEFORE UPDATE ON public.cajas
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();


CREATE TABLE IF NOT EXISTS public.movimientos_caja (
    id           UUID                   NOT NULL DEFAULT gen_random_uuid(),
    caja_id      UUID                   NOT NULL,
    tipo         CHARACTER VARYING(20)  NOT NULL,
    concepto     CHARACTER VARYING(255) NOT NULL,
    monto        NUMERIC(10, 2)         NOT NULL,
    referencia   CHARACTER VARYING(100)     NULL,
    usuario_id   UUID                       NULL,
    fecha        TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notas        TEXT                       NULL,
    created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT movimientos_caja_pkey PRIMARY KEY (id),
    CONSTRAINT movimientos_caja_caja_fkey FOREIGN KEY (caja_id)
        REFERENCES public.cajas (id) ON DELETE CASCADE,
    CONSTRAINT movimientos_caja_tipo_fkey FOREIGN KEY (tipo)
        REFERENCES public.catalogo_tipos_movimiento_caja (codigo) ON DELETE RESTRICT,
    CONSTRAINT movimientos_caja_usuario_fkey FOREIGN KEY (usuario_id)
        REFERENCES public.usuarios (id) ON DELETE SET NULL,
    CONSTRAINT movimientos_caja_monto_check CHECK (monto > 0)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_movimientos_caja_caja_id
    ON public.movimientos_caja USING btree (caja_id) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_movimientos_caja_tipo
    ON public.movimientos_caja USING btree (tipo) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_movimientos_caja_fecha
    ON public.movimientos_caja USING btree (fecha DESC) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_movimientos_caja_usuario_id
    ON public.movimientos_caja USING btree (usuario_id) TABLESPACE pg_default;


-- ================================================================
-- BLOQUE 14 — VISTAS PARA ANALÍTICA Y REPORTES
--
-- Vistas de solo lectura para dashboards. No almacenan datos físicos;
-- transforman tablas existentes de ventas, órdenes, categorías y caja.
-- ================================================================

DROP VIEW IF EXISTS public.vw_ventas_por_vendedor;
CREATE VIEW public.vw_ventas_por_vendedor AS
SELECT
    v.vendedor_id,
    u.nombre AS vendedor_nombre,
    u.email,
    COUNT(v.id) AS total_ventas,
    SUM(CASE WHEN v.estado = 'COMPLETADA' THEN 1 ELSE 0 END)::INTEGER AS ventas_completadas,
    COALESCE(SUM(CASE WHEN v.estado = 'COMPLETADA' THEN v.total ELSE 0 END), 0::NUMERIC(10,2)) AS total_ingreso,
    MAX(v.fecha) AS ultima_venta,
    MIN(v.fecha) AS primera_venta
FROM public.ventas v
LEFT JOIN public.usuarios u ON v.vendedor_id = u.id
WHERE v.vendedor_id IS NOT NULL
GROUP BY v.vendedor_id, u.nombre, u.email
ORDER BY total_ingreso DESC NULLS LAST;


DROP VIEW IF EXISTS public.vw_rentabilidad_categoria;
CREATE VIEW public.vw_rentabilidad_categoria AS
SELECT
    c.id,
    c.nombre AS categoria,
    COUNT(DISTINCT p.id) AS productos_activos,
    COUNT(DISTINCT CASE WHEN v.id IS NOT NULL THEN vi.id END) AS items_vendidos,
    COALESCE(SUM(CASE WHEN v.id IS NOT NULL THEN vi.cantidad ELSE 0 END), 0)::INTEGER AS cantidad_total_vendida,
    COALESCE(SUM(
        CASE WHEN v.id IS NOT NULL
             THEN vi.cantidad * vi.precio_unitario
             ELSE 0
        END
    ), 0::NUMERIC(10,2))::NUMERIC(10,2) AS ingreso_total,
    COALESCE(ROUND(
        SUM(CASE WHEN v.id IS NOT NULL THEN vi.cantidad * vi.precio_unitario ELSE 0 END)
        / NULLIF(SUM(CASE WHEN v.id IS NOT NULL THEN vi.cantidad ELSE 0 END), 0),
        2
    ), 0::NUMERIC(10,2)) AS precio_promedio,
    (COALESCE(SUM(
        CASE WHEN v.id IS NOT NULL
             THEN vi.cantidad * vi.precio_unitario
             ELSE 0
        END
    ), 0::NUMERIC(10,2)) * 0.6)::NUMERIC(10,2) AS costo_estimado,
    (COALESCE(SUM(
        CASE WHEN v.id IS NOT NULL
             THEN vi.cantidad * vi.precio_unitario
             ELSE 0
        END
    ), 0::NUMERIC(10,2)) * 0.4)::NUMERIC(10,2) AS ganancia_estimada,
    40.0::NUMERIC(5,2) AS margen_porcentaje
FROM public.categorias c
LEFT JOIN public.productos p
    ON c.id = p.categoria_id
   AND p.activo = TRUE
LEFT JOIN public.venta_items vi
    ON p.id = vi.producto_id
LEFT JOIN public.ventas v
    ON vi.venta_id = v.id
   AND v.estado = 'COMPLETADA'
GROUP BY c.id, c.nombre
ORDER BY ingreso_total DESC NULLS LAST;


DROP VIEW IF EXISTS public.vw_rendimiento_tecnicos;
CREATE VIEW public.vw_rendimiento_tecnicos AS
SELECT
    e.tecnico_id,
    u.nombre AS tecnico_nombre,
    u.email,
    COUNT(e.id) AS ordenes_totales,
    SUM(CASE WHEN e.estado = 'FINALIZADO' THEN 1 ELSE 0 END)::INTEGER AS ordenes_completadas,
    SUM(
        CASE
            WHEN e.estado = 'FINALIZADO'
             AND e.fecha_entrega IS NOT NULL
             AND (e.fecha_entrega - e.fecha_creacion) <= INTERVAL '1 day'
            THEN 1 ELSE 0
        END
    )::INTEGER AS ordenes_24horas,
    COALESCE(ROUND(AVG(
        CASE
            WHEN e.fecha_entrega IS NOT NULL
            THEN EXTRACT(EPOCH FROM (e.fecha_entrega - e.fecha_creacion)) / 86400
        END
    )::NUMERIC, 2), 0::NUMERIC) AS dias_promedio,
    COALESCE(SUM(CASE WHEN e.estado = 'FINALIZADO' THEN e.mano_obra ELSE 0 END), 0::NUMERIC(10,2)) AS total_mano_obra,
    COALESCE(SUM(CASE WHEN e.estado = 'FINALIZADO' THEN e.total_orden ELSE 0 END), 0::NUMERIC(10,2)) AS ingreso_total,
    SUM(CASE WHEN e.estado_pago IN ('PENDIENTE', 'ANTICIPO', 'PARCIAL') THEN 1 ELSE 0 END)::INTEGER AS ordenes_deuda,
    MAX(e.fecha_creacion) AS ultima_orden
FROM public.entregas e
LEFT JOIN public.usuarios u ON e.tecnico_id = u.id
WHERE e.tecnico_id IS NOT NULL
GROUP BY e.tecnico_id, u.nombre, u.email
ORDER BY ordenes_completadas DESC NULLS LAST;


DROP VIEW IF EXISTS public.vw_flujo_caja_30dias;
CREATE VIEW public.vw_flujo_caja_30dias AS
SELECT
    DATE(mc.fecha) AS fecha,
    mc.tipo,
    COUNT(*) AS cantidad_movimientos,
    SUM(mc.monto)::NUMERIC(10,2) AS monto_total,
    SUM(CASE WHEN tmc.afecta_saldo = 'INGRESO' THEN mc.monto ELSE 0 END)::NUMERIC(10,2) AS ingresos,
    SUM(CASE WHEN tmc.afecta_saldo = 'EGRESO' THEN mc.monto ELSE 0 END)::NUMERIC(10,2) AS egresos
FROM public.movimientos_caja mc
JOIN public.catalogo_tipos_movimiento_caja tmc ON mc.tipo = tmc.codigo
WHERE mc.fecha >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE(mc.fecha), mc.tipo
ORDER BY fecha DESC;


-- ================================================================
-- BLOQUE 15 — FUNCIÓN PARA PROYECCIÓN DE CASH FLOW
--
-- Calcula flujo histórico de los últimos 30 días y una proyección
-- simple basada en promedios diarios.
-- ================================================================

CREATE OR REPLACE FUNCTION public.fn_flujo_caja_proyectado(dias_adelante INT DEFAULT 30)
RETURNS TABLE (
    fecha DATE,
    tipo_flujo CHARACTER VARYING,
    ingresos NUMERIC,
    egresos NUMERIC,
    saldo_neto NUMERIC,
    saldo_acumulado NUMERIC
) AS $$
DECLARE
    v_saldo_actual NUMERIC;
    v_fecha_inicio DATE;
    v_promedio_ingresos_diarios NUMERIC;
    v_promedio_egresos_diarios NUMERIC;
BEGIN
    v_fecha_inicio := CURRENT_DATE - INTERVAL '30 days';

    SELECT COALESCE(SUM(c.monto_inicial), 0)
         + COALESCE(SUM(CASE WHEN tmc.afecta_saldo = 'INGRESO' THEN mc.monto ELSE 0 END), 0)
         - COALESCE(SUM(CASE WHEN tmc.afecta_saldo = 'EGRESO' THEN mc.monto ELSE 0 END), 0)
    INTO v_saldo_actual
    FROM public.cajas c
    LEFT JOIN public.movimientos_caja mc ON c.id = mc.caja_id
    LEFT JOIN public.catalogo_tipos_movimiento_caja tmc ON mc.tipo = tmc.codigo
    WHERE c.estado = 'ABIERTA';

    SELECT
        COALESCE(AVG(ingresos_dia), 0),
        COALESCE(AVG(egresos_dia), 0)
    INTO v_promedio_ingresos_diarios, v_promedio_egresos_diarios
    FROM (
        SELECT
            DATE(mc.fecha) AS fecha_dia,
            SUM(CASE WHEN tmc.afecta_saldo = 'INGRESO' THEN mc.monto ELSE 0 END) AS ingresos_dia,
            SUM(CASE WHEN tmc.afecta_saldo = 'EGRESO' THEN mc.monto ELSE 0 END) AS egresos_dia
        FROM public.movimientos_caja mc
        JOIN public.catalogo_tipos_movimiento_caja tmc ON mc.tipo = tmc.codigo
        WHERE mc.fecha >= v_fecha_inicio
        GROUP BY DATE(mc.fecha)
    ) t;

    RETURN QUERY
    SELECT
        DATE(mc.fecha) AS fecha,
        'HISTORICO'::CHARACTER VARYING AS tipo_flujo,
        SUM(CASE WHEN tmc.afecta_saldo = 'INGRESO' THEN mc.monto ELSE 0 END)::NUMERIC(10,2) AS ingresos,
        SUM(CASE WHEN tmc.afecta_saldo = 'EGRESO' THEN mc.monto ELSE 0 END)::NUMERIC(10,2) AS egresos,
        (
            SUM(CASE WHEN tmc.afecta_saldo = 'INGRESO' THEN mc.monto ELSE 0 END)
            - SUM(CASE WHEN tmc.afecta_saldo = 'EGRESO' THEN mc.monto ELSE 0 END)
        )::NUMERIC(10,2) AS saldo_neto,
        (
            v_saldo_actual
            + SUM(CASE
                WHEN tmc.afecta_saldo = 'INGRESO' THEN mc.monto
                WHEN tmc.afecta_saldo = 'EGRESO' THEN -mc.monto
                ELSE 0
            END)
        )::NUMERIC(10,2) AS saldo_acumulado
    FROM public.movimientos_caja mc
    JOIN public.catalogo_tipos_movimiento_caja tmc ON mc.tipo = tmc.codigo
    WHERE mc.fecha >= v_fecha_inicio
    GROUP BY DATE(mc.fecha)

    UNION ALL

    SELECT
        (CURRENT_DATE + gs.n)::DATE AS fecha,
        'PROYECTADO'::CHARACTER VARYING AS tipo_flujo,
        v_promedio_ingresos_diarios::NUMERIC(10,2) AS ingresos,
        v_promedio_egresos_diarios::NUMERIC(10,2) AS egresos,
        (v_promedio_ingresos_diarios - v_promedio_egresos_diarios)::NUMERIC(10,2) AS saldo_neto,
        (
            v_saldo_actual
            + (gs.n * (v_promedio_ingresos_diarios - v_promedio_egresos_diarios))
        )::NUMERIC(10,2) AS saldo_acumulado
    FROM generate_series(1, dias_adelante) AS gs(n)
    ORDER BY fecha ASC;
END;
$$ LANGUAGE plpgsql;


-- ================================================================
-- FIN DEL SCRIPT
-- ================================================================
```
