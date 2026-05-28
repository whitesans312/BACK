# ERG-POS — Backend

Sistema POS para gestión de servicios técnicos, inventario, ventas y facturación de repuestos de sillas ergonómicas.

- **Base de datos:** PostgreSQL (Supabase)
- **Stack:** Java 21 · Spring Boot 3.5 · Lombok · JPA/Hibernate
- **Esquema BD:** ver [`DATABASE_SCHEMA.md`](./DATABASE_SCHEMA.md)

---

## Cómo correr

```bash
# En la carpeta del backend (donde está el pom.xml)
mvn spring-boot:run
```

El servidor queda en: `http://localhost:8080/api`

> **Antes de la primera ejecución:** ejecutar el script completo de `DATABASE_SCHEMA.md`
> (sección SQL) en el Editor SQL de Supabase. **El orden importa**, hay dependencias entre bloques.

---

## Variables de entorno (`application.properties`)

| Variable | Descripción |
|----------|-------------|
| `spring.datasource.url` | URL JDBC de Supabase (jdbc:postgresql://...) |
| `spring.datasource.username` | Usuario de la BD |
| `spring.datasource.password` | Contraseña de la BD |
| `app.jwt.secret` | Clave secreta para firmar JWT (HS256). **Cambiar en producción** |
| `app.jwt.expiration-ms` | Duración del token en milisegundos (default: 28800000 = 8 horas) |

---

## Autenticación — JWT (JSON Web Token)

Todos los endpoints protegidos requieren un token JWT en el header:
```bash
Authorization: Bearer <token_jwt>
```

**Generación del token:**
1. Login: `POST /api/auth/login` con email y password
2. Respuesta incluye el token JWT con validez de 8 horas (configurable)

**Claims del token:**
- `sub`: Email del usuario
- `userId`: UUID del usuario
- `name`: Nombre del usuario
- `role`: Rol asignado (ADMIN, TECNICO, INVENTARIO)
- `iat`: Timestamp emisión
- `exp`: Timestamp expiración

**Algoritmo:** HS256 (HMAC-SHA256)

> **Importante:** La clave secreta `app.jwt.secret` es de desarrollo. Cambiar en producción a una cadena segura de 32+ caracteres.

---

## Endpoints

### 🔐 Auth
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/auth/login` | Login — devuelve datos del usuario |
| POST | `/api/auth/register` | Crear cuenta nueva |

---

### 👥 Usuarios
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/usuarios` | Todos los usuarios |
| GET | `/api/usuarios/activos` | Solo activos |
| GET | `/api/usuarios/tecnicos` | Técnicos activos (para asignar órdenes) |
| GET | `/api/usuarios/{id}` | Por ID |
| POST | `/api/usuarios` | Crear usuario |
| PUT | `/api/usuarios/{id}` | Actualizar datos |
| PATCH | `/api/usuarios/{id}/activar?activo=true` | Activar / Desactivar |
| PATCH | `/api/usuarios/{id}/password` | Cambiar contraseña |
| DELETE | `/api/usuarios/{id}` | Desactivar |

---

### 👤 Clientes
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/clientes` | Todos los clientes |
| GET | `/api/clientes/activos` | Solo activos |
| GET | `/api/clientes/{id}` | Por ID |
| POST | `/api/clientes` | Crear cliente |
| PUT | `/api/clientes/{id}` | Actualizar |
| PATCH | `/api/clientes/{id}/activar?activo=true` | Activar / Desactivar |

---

### 🏭 Proveedores
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/proveedores` | Todos |
| GET | `/api/proveedores/activos` | Solo activos |
| GET | `/api/proveedores/{id}` | Por ID |
| POST | `/api/proveedores` | Crear |
| PUT | `/api/proveedores/{id}` | Actualizar |
| PATCH | `/api/proveedores/{id}/activar?activo=true` | Activar / Desactivar |

---

### 📦 Productos
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/productos` | Todos |
| GET | `/api/productos/activos` | Solo activos |
| GET | `/api/productos/stock-bajo` | Stock ≤ stockMinimo |
| GET | `/api/productos/buscar?q=rueda` | Buscar por nombre o código |
| GET | `/api/productos/{id}` | Por ID |
| GET | `/api/productos/codigo/{codigo}` | Por código interno |
| POST | `/api/productos` | Crear |
| PUT | `/api/productos/{id}` | Actualizar |
| PATCH | `/api/productos/{id}/activar?activo=true` | Activar / Desactivar |
| DELETE | `/api/productos/{id}` | Desactivar |

---

### 🗂️ Categorías
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/categorias` | Todas las categorías |
| GET | `/api/categorias/activas` | Solo activas |
| GET | `/api/categorias/{id}` | Por ID |
| POST | `/api/categorias` | Crear |
| PUT | `/api/categorias/{id}` | Actualizar |

---

### 🔧 Órdenes de Servicio (Entregas)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/entregas` | Todas las órdenes |
| GET | `/api/entregas/{id}` | Por ID |
| GET | `/api/entregas/estado/{estado}` | Filtrar por estado |
| GET | `/api/entregas/tecnico/{tecnicoId}` | Por técnico asignado |
| GET | `/api/entregas/resumen` | Conteo de órdenes por estado |
| GET | `/api/entregas/contadores` | Contadores: `porConfirmar`, `pagoPendiente` |
| GET | `/api/entregas/pago-pendiente` | Entregas con pago pendiente |
| GET | `/api/entregas/por-confirmar` | Entregas pendientes de confirmar (COMPLETADAS sin FINALIZAR) |
| POST | `/api/entregas` | Crear orden |
| PUT | `/api/entregas/{id}` | Editar orden (técnico, dirección, cliente, tipo, descripción, mano de obra) |
| PATCH | `/api/entregas/{id}/estado?estado=EN_PROCESO&notas=ok` | Cambiar estado (admin solo para CANCELADO) |
| POST | `/api/entregas/{id}/confirmar?adminId=uuid` | Confirmar servicio — pone FINALIZADO, descuenta stock |
| POST | `/api/entregas/{id}/pagos?monto=X&notas=Y&usuarioId=uuid` | Registrar pago parcial |
| PATCH | `/api/entregas/{id}/anticipo?porcentaje=50` | Guardar % de anticipo acordado |
| DELETE | `/api/entregas/{id}` | Cancelar orden (solo admin) |

**Flujo de estados:**
```
PENDIENTE → EN_PROCESO → COMPLETADO → FINALIZADO
               ↘ (cualquier estado) → CANCELADO
```
- `FINALIZADO` solo desde `/confirmar` (admin). Descuenta stock e inventario.
- `CANCELADO` solo por admin mediante DELETE o PATCH estado.

**Estados de pago:** `PENDIENTE` → `ANTICIPO` → `PARCIAL` → `COMPLETO`

**Control de acceso:**
- Cancelación de órdenes: Solo `ADMIN`
- Confirmar servicio: Solo `ADMIN`

---

### 🛒 Ventas (POS / Mostrador)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/ventas` | Todas las ventas |
| GET | `/api/ventas/{id}` | Por ID |
| GET | `/api/ventas/resumen` | Estadísticas rápidas |
| POST | `/api/ventas` | Registrar venta (descuenta stock automáticamente) |
| PATCH | `/api/ventas/{id}/estado?estado=CANCELADA` | Cambiar estado |

---

### 🛍️ Compras a Proveedores
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/compras` | Todas las compras |
| GET | `/api/compras/{id}` | Por ID |
| GET | `/api/compras/proveedor/{proveedorId}` | Por proveedor |
| POST | `/api/compras` | Crear orden de compra |
| PUT | `/api/compras/{id}` | Actualizar |
| PATCH | `/api/compras/{id}/estado?estado=CONFIRMADA` | Confirmar (sube stock) |

---

### 📊 Movimientos de Inventario (Kardex)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/movimientos-inventario` | Todos los movimientos |
| GET | `/api/movimientos-inventario/recientes` | Últimos movimientos |
| GET | `/api/movimientos-inventario/producto/{productoId}` | Por producto |
| POST | `/api/movimientos-inventario/entrada` | Entrada manual (suma stock) |
| POST | `/api/movimientos-inventario/salida` | Salida manual (resta stock) |

> El trigger `fn_actualizar_stock()` en la BD mueve el stock automáticamente
> al insertar en esta tabla. **Nunca** usar `UPDATE productos SET stock = ...` directamente.

---

### 🎭 Roles
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/roles` | Todos los roles |
| GET | `/api/roles/{id}` | Por ID |

**Roles disponibles:** `ADMIN` · `TECNICO` · `INVENTARIO`

---

## Reglas de negocio clave

- **Stock:** Solo se mueve a través de `movimientos_inventario`. El trigger en BD lo aplica atómicamente. Si el stock es insuficiente, la BD lanza excepción y revierte toda la transacción.
- **Historial:** `cliente_nombre` y `cliente_telefono` se guardan como snapshot en entregas y ventas para no perder datos históricos si el cliente cambia su información.
- **Facturación:** Secuencias `FO-XXXX` (órdenes, borradores) y `FV-XXXX` (ventas oficiales). Definidas en BD con `NO CYCLE`.
- **Passwords:** El seed tiene `123456` en texto plano. **Hashear con BCrypt antes de producción.**#   B A C K  
 #   B A C K  
 