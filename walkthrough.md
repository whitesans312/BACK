# Walkthrough — ERG-POS Implementation

## Bloque 2 — IVA Dinámico
- `ConfiguracionNegocioService.java`: Modified `getTaxDivisor()` method to map `TAX_PERCENTAGE` dynamically from the database without try-catch or other redundancies, returning a secure default `1.19` fallback.
- `EntregaService.java`: Verified constructor injection of `ConfiguracionNegocioService` and checked replacement of `BigDecimal.valueOf(1.19)` with `configService.getTaxDivisor()`.
- `FacturaVentaService.java`: Verified constructor injection of `ConfiguracionNegocioService` and checked replacement of `BigDecimal.valueOf(1.19)` with `configService.getTaxDivisor()`.
- `VentaRegistroService.java`: Verified constructor injection of `ConfiguracionNegocioService` and checked replacement of `BigDecimal.valueOf(1.19)` with `configService.getTaxDivisor()`.
- `VentaService.java`: Verified constructor injection of `ConfiguracionNegocioService` and checked replacement of `BigDecimal.valueOf(1.19)` with `configService.getTaxDivisor()`.

## Bloque 1 — Historial Cliente Completo
- `VentaRepository.java`: Checked query method `findByClienteIdOrTelefonoOrderByFechaDesc` to find sales by either cliente ID or phone.
- `EntregaRepository.java`: Checked query method `findByClienteIdOrTelefonoOrderByFechaCreacionDesc` to find service orders by either cliente ID or phone.
- `DevolucionGarantiaRepository.java`: Modified query method `findByClienteIdOrTelefonoOrderByFechaDesc` to match the exact requested JPQL query structure (filtering on `venta.clienteTelefono` only).
- `ClienteService.java`: Modified `getPerfilCompleto()` method to fetch cliente entity, extract `telefono`, and pass it unconditionally to the three repository queries.

## Bloque 3 — Configuración Empresa en BD
- `src/services/configuracionService.ts` (NEW): Created service to wrap GET/PUT calls to `/api/configuracion` endpoint.
- `src/types/index.ts`: Appended the strict interface `ConfiguracionNegocio` at the end of the types list.
- `src/pages/Configuracion.tsx`: Imported the new service/types, added `iva` to the form state, fetched it on mount via `configuracionService.getAll()`, mapped fields (COMPANY_NAME, etc.), added the "IVA (%)" numeric input before the save button, saved the full payload via `configuracionService.saveAll()`, and cleaned up the `localStorage` checks/warning message.

## Correcciones de Usabilidad & Facturación (Reportadas por el usuario)

- **Mano de Obra en Factura de Venta (`Ventas.tsx`)**: Síntesis dinámica en `FacturaView` — si el total guardado excede la suma de repuestos, la diferencia se muestra como línea `SERV-01 — Mano de Obra / Servicio Técnico` con IVA desglosado.
- **Input de Cantidad en Ventas (`Ventas.tsx`)**: El campo permitía solo flechas arriba/abajo. Se corrigió para aceptar entrada libre de teclado, con validación en la confirmación.
- **Doble Conteo en P&L de Reportes (`EntregaRepository.java`)**:
  - **Causa**: `sumTotalFinalizadas()` sumaba `totalOrden` (repuestos + mano de obra), pero los repuestos ya se contabilizan en `totalVentasPOS` vía la `Venta` generada al confirmar la orden.
  - **Corrección**: ambas queries ahora suman solo `e.manoObra`. El rango temporal usa `fechaEntrega` en vez de `fechaCreacion`.
  - **Frontend (`Reportes.tsx`)**: Labels actualizados a "🔧 Mano de obra (servicios)" para reflejar el cálculo correcto.

## Verification Results
- `./mvnw compile`: **PASS** (0 errores en todos los cambios de repositorio y servicio)
- `./mvnw test`: **PASS** (1 test run, 0 failures, 0 errors)
- `npm run build`: **PASS** (140 modules transformed, 0 errores TypeScript, build en 4.66s)

## Open Questions Pending User Answer
- ¿ReporteController.java también debe usar IVA dinámico?
- ¿Javadoc completo en todos los servicios/controladores, o solo en los modificados?
