package com.ergpos.app.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ergpos.app.model.Cliente;
import com.ergpos.app.model.FacturaVenta;
import com.ergpos.app.model.MovimientoInventario;
import com.ergpos.app.model.Producto;
import com.ergpos.app.model.Venta;
import com.ergpos.app.model.VentaItem;
import com.ergpos.app.repositories.ClienteRepository;
import com.ergpos.app.repositories.FacturaVentaRepository;
import com.ergpos.app.repositories.MovimientoInventarioRepository;
import com.ergpos.app.repositories.VentaRepository;

/**
 * Servicio de negocio para el módulo de Ventas POS.
 * <p>
 * Gestiona el ciclo de vida de las ventas al mostrador: registro, validación de stock,
 * generación automática de factura con cálculo de IVA dinámico, descuento de inventario
 * mediante movimientos de SALIDA y auditoría de eventos.
 * </p>
 *
 * @author ERG-POS Dev Team
 * @since 1.0
 */
@Service
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoService productoService;
    private final MovimientoInventarioRepository movimientoRepository;
    private final FacturaVentaRepository facturaVentaRepository;
    private final AuditoriaService auditoriaService;
    private final ClienteRepository clienteRepository;
    private final ConfiguracionNegocioService configService;

    public VentaService(VentaRepository ventaRepository,
                        ProductoService productoService,
                        MovimientoInventarioRepository movimientoRepository,
                        FacturaVentaRepository facturaVentaRepository,
                        AuditoriaService auditoriaService,
                        ClienteRepository clienteRepository,
                        ConfiguracionNegocioService configService) {
        this.ventaRepository        = ventaRepository;
        this.productoService        = productoService;
        this.movimientoRepository   = movimientoRepository;
        this.facturaVentaRepository = facturaVentaRepository;
        this.auditoriaService       = auditoriaService;
        this.clienteRepository      = clienteRepository;
        this.configService          = configService;
    }

    /** Retorna todas las ventas ordenadas de más reciente a más antigua. */
    public List<Venta> findAll()                     { return ventaRepository.findAllByOrderByFechaDesc(); }
    /** Busca una venta por su UUID. */
    public Optional<Venta> findById(UUID id)         { return ventaRepository.findById(id); }
    /** Filtra ventas por estado (COMPLETADA | CANCELADA | PENDIENTE). */
    public List<Venta> findByEstado(String estado)   { return ventaRepository.findByEstado(estado); }
    /** Retorna las 10 ventas más recientes para el dashboard. */
    public List<Venta> findRecientes()               { return ventaRepository.findTop10ByOrderByFechaDesc(); }

    /**
     * Registra una nueva venta en el sistema de forma transaccional.
     * <ol>
     *   <li>Valida la disponibilidad de stock para cada ítem.</li>
     *   <li>Guarda la venta y genera movimientos de SALIDA de inventario.</li>
     *   <li>Genera automáticamente la factura de venta con IVA dinámico.</li>
     *   <li>Registra el evento en auditoría.</li>
     * </ol>
     *
     * @param venta la venta a registrar con sus ítems
     * @return la {@link Venta} persistida con su ID, total y factura asociada
     * @throws IllegalArgumentException si un producto no existe
     * @throws IllegalStateException    si el stock es insuficiente para algún ítem
     */
    @Transactional
    public Venta save(Venta venta) {
        resolverCliente(venta);

        // PASO 1: Validar stock y preparar ítems
        for (VentaItem item : venta.getItems()) {
            Producto producto = productoService.findById(item.getProducto().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + item.getProducto().getId()));

            if (producto.getStock() < item.getCantidad()) {
                throw new IllegalStateException("Stock insuficiente para: " + producto.getNombre()
                        + ". Disponible: " + producto.getStock());
            }

            // Respetar precio del frontend; fallback al precio de BD
            if (item.getPrecioUnitario() == null
                    || item.getPrecioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                item.setPrecioUnitario(producto.getPrecio());
            }

            item.setVenta(venta);
        }

        // PASO 2: Guardar venta primero para obtener el ID
        venta.calcularTotal();
        Venta ventaGuardada = ventaRepository.save(venta);

        // PASO 3: Crear movimientos SALIDA con origenId correcto
        for (VentaItem item : ventaGuardada.getItems()) {
            Producto producto = productoService.findById(item.getProducto().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + item.getProducto().getId()));

            MovimientoInventario mov = new MovimientoInventario();
            mov.setProducto(producto);
            mov.setUsuario(ventaGuardada.getVendedor());
            mov.setTipo("SALIDA");
            mov.setCantidad(item.getCantidad());
            mov.setObservacion("Venta a: " + ventaGuardada.getClienteNombre());
            mov.setOrigenTipo("VENTA");
            mov.setOrigenId(ventaGuardada.getId());
            movimientoRepository.save(mov);
        }

        // PASO 4: Generar factura automáticamente
        // numero lo genera la secuencia seq_facturas_venta en Supabase (FV-0001...)
        FacturaVenta factura = new FacturaVenta();
        factura.setVenta(ventaGuardada);

        BigDecimal totalFinal   = ventaGuardada.getTotal();
        BigDecimal taxDivisor   = configService.getTaxDivisor();
        BigDecimal baseGravable = totalFinal.divide(taxDivisor, 2, RoundingMode.HALF_UP);
        BigDecimal iva          = totalFinal.subtract(baseGravable).setScale(2, RoundingMode.HALF_UP);

        factura.setSubtotal(baseGravable);
        factura.setImpuesto(iva);
        factura.setTotal(totalFinal);

        facturaVentaRepository.save(factura);

        // ── Auditoría ────────────────────────────────────────────
        java.util.UUID vendedorId = ventaGuardada.getVendedor() != null ? ventaGuardada.getVendedor().getId() : null;
        String vendedorNombre = ventaGuardada.getVendedor() != null ? ventaGuardada.getVendedor().getNombre() : "Sistema";
        auditoriaService.registrar(
            vendedorId, vendedorNombre,
            "CREAR_VENTA", "VENTAS",
            ventaGuardada.getId(),
            "Venta $" + ventaGuardada.getTotal() + " a " + ventaGuardada.getClienteNombre()
        );
        return ventaGuardada;
    }

    private void resolverCliente(Venta venta) {
        if (venta.getCliente() != null && venta.getCliente().getId() != null) {
            Cliente cliente = clienteRepository.findById(venta.getCliente().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cliente no encontrado: " + venta.getCliente().getId()));
            venta.setCliente(cliente);
            venta.setClienteNombre(cliente.getNombre());
            venta.setClienteTelefono(cliente.getTelefono());
            return;
        }

        String telefono = venta.getClienteTelefono();
        if (telefono != null && !telefono.isBlank()) {
            clienteRepository.findByTelefono(telefono.trim()).ifPresent(cliente -> {
                venta.setCliente(cliente);
                venta.setClienteNombre(cliente.getNombre());
                venta.setClienteTelefono(cliente.getTelefono());
            });
        }
    }

    /**
     * Cancela una venta existente si aún no ha sido completada.
     *
     * @param id UUID de la venta a cancelar
     * @return la {@link Venta} actualizada con estado CANCELADA
     * @throws IllegalArgumentException si la venta no existe
     * @throws IllegalStateException    si ya estaba cancelada o si está completada
     */
    @Transactional
    public Venta cancelar(UUID id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));

        if ("CANCELADA".equals(venta.getEstado())) {
            throw new IllegalStateException("La venta ya está cancelada");
        }

        if ("COMPLETADA".equals(venta.getEstado())) {
            throw new IllegalStateException(
                    "Una venta completada no se cancela directamente. Registra una devolucion o garantia.");
        }

        venta.setEstado("CANCELADA");
        Venta cancelada = ventaRepository.save(venta);

        // ── Auditoría ────────────────────────────────────────────
        java.util.UUID vendedorId2 = cancelada.getVendedor() != null ? cancelada.getVendedor().getId() : null;
        String vendedorNombre2 = cancelada.getVendedor() != null ? cancelada.getVendedor().getNombre() : "Sistema";
        auditoriaService.registrar(
            vendedorId2, vendedorNombre2,
            "CANCELAR_VENTA", "VENTAS",
            cancelada.getId(),
            "Venta cancelada — cliente: " + cancelada.getClienteNombre()
            + " — total: $" + cancelada.getTotal()
        );
        return cancelada;
    }



    // ── Estadísticas ──────────────────────────────────────────────────────────

    /**
     * Calcula un resumen general de ventas para el dashboard.
     * <p>Incluye totales, completadas, canceladas, ingresos del día y del mes actual.</p>
     *
     * @return {@link Map} con las claves: totalVentas, completadas, canceladas,
     *         ingresoTotal, ingresosHoy, ingresosMes
     */
    public Map<String, Object> getResumenGeneral() {
        long totalVentas    = ventaRepository.count();
        long completadas    = ventaRepository.countByEstado("COMPLETADA");
        long canceladas     = ventaRepository.countByEstado("CANCELADA");
        Double ingresoTotal = ventaRepository.sumTotalCompletadas();

        LocalDateTime hoyInicio = LocalDate.now().atStartOfDay();
        LocalDateTime hoyFin    = hoyInicio.plusDays(1);
        Double ingresosHoy = ventaRepository.sumTotalCompletadasEnRango(hoyInicio, hoyFin);

        LocalDateTime mesInicio = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Double ingresosMes = ventaRepository.sumTotalCompletadasEnRango(mesInicio, hoyFin);

        return Map.of(
            "totalVentas",  totalVentas,
            "completadas",  completadas,
            "canceladas",   canceladas,
            "ingresoTotal", ingresoTotal != null ? ingresoTotal : 0.0,
            "ingresosHoy",  ingresosHoy  != null ? ingresosHoy  : 0.0,
            "ingresosMes",  ingresosMes  != null ? ingresosMes  : 0.0
        );
    }
}
