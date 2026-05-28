package com.ergpos.app.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ergpos.app.model.Entrega;
import com.ergpos.app.model.EntregaItem;
import com.ergpos.app.model.FacturaOrden;
import com.ergpos.app.model.MovimientoInventario;
import com.ergpos.app.model.PagoOrden;
import com.ergpos.app.model.Producto;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.model.Venta;
import com.ergpos.app.model.VentaItem;
import com.ergpos.app.model.Cliente;
import com.ergpos.app.repositories.ClienteRepository;
import com.ergpos.app.repositories.EntregaRepository;
import com.ergpos.app.repositories.FacturaOrdenRepository;
import com.ergpos.app.repositories.MovimientoInventarioRepository;
import com.ergpos.app.repositories.PagoOrdenRepository;
import com.ergpos.app.repositories.ProductoRepository;
import com.ergpos.app.repositories.UsuarioRepository;

@Service
public class EntregaService {

    private static final String ESTADO_PENDIENTE  = "PENDIENTE";
    private static final String ESTADO_EN_PROCESO = "EN_PROCESO";
    private static final String ESTADO_COMPLETADO = "COMPLETADO";
    private static final String ESTADO_FINALIZADO = "FINALIZADO";
    private static final String ESTADO_CANCELADO  = "CANCELADO";

    private static final String PAGO_PENDIENTE = "PENDIENTE";
    private static final String PAGO_ANTICIPO  = "ANTICIPO";
    private static final String PAGO_PARCIAL   = "PARCIAL";
    private static final String PAGO_COMPLETO  = "COMPLETO";

    private final EntregaRepository              entregaRepository;
    private final ProductoRepository             productoRepository;
    private final PagoOrdenRepository            pagoOrdenRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final FacturaOrdenRepository         facturaOrdenRepository;
    private final UsuarioRepository              usuarioRepository;
    private final VentaRegistroService           ventaRegistroService;
    private final AuditoriaService               auditoriaService;
    private final ClienteRepository              clienteRepository;
    private final ConfiguracionNegocioService    configService;

    public EntregaService(EntregaRepository entregaRepository,
                          ProductoRepository productoRepository,
                          PagoOrdenRepository pagoOrdenRepository,
                          MovimientoInventarioRepository movimientoRepository,
                          FacturaOrdenRepository facturaOrdenRepository,
                          UsuarioRepository usuarioRepository,
                          VentaRegistroService ventaRegistroService,
                          AuditoriaService auditoriaService,
                          ClienteRepository clienteRepository,
                          ConfiguracionNegocioService configService) {
        this.entregaRepository    = entregaRepository;
        this.productoRepository   = productoRepository;
        this.pagoOrdenRepository  = pagoOrdenRepository;
        this.movimientoRepository = movimientoRepository;
        this.facturaOrdenRepository = facturaOrdenRepository;
        this.usuarioRepository    = usuarioRepository;
        this.ventaRegistroService = ventaRegistroService;
        this.auditoriaService     = auditoriaService;
        this.clienteRepository    = clienteRepository;
        this.configService        = configService;
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    public List<Entrega> findAll()                     { return entregaRepository.findAll(); }
    public List<Entrega> findByEstado(String estado)   { return entregaRepository.findByEstado(estado); }
    public List<Entrega> findByTecnico(UUID tecnicoId) { return entregaRepository.findByTecnicoId(tecnicoId); }
    public Optional<Entrega> findById(UUID id)         { return entregaRepository.findById(id); }

    public List<Entrega> findConPagoPendiente() {
        return entregaRepository.findByEstadoPagoIn(
                List.of(PAGO_PENDIENTE, PAGO_ANTICIPO, PAGO_PARCIAL));
    }

    public List<Entrega> findPorConfirmar() {
        return entregaRepository.findByEstado(ESTADO_COMPLETADO);
    }

    public long contarPorConfirmar() {
        return entregaRepository.countByEstado(ESTADO_COMPLETADO);
    }

    public long contarPagoPendiente() {
        return entregaRepository.countByEstadoPagoInAndEstadoNot(
                List.of(PAGO_PENDIENTE, PAGO_ANTICIPO, PAGO_PARCIAL), ESTADO_CANCELADO);
    }

    // ── Guardar orden nueva ───────────────────────────────────────────────────

    @Transactional
    public Entrega save(Entrega entrega) {
        resolverCliente(entrega);

        for (EntregaItem item : entrega.getItems()) {
            item.setEntrega(entrega);
            Producto prodReal = productoRepository.findById(item.getProducto().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + item.getProducto().getId()));
            item.setProducto(prodReal);
            if (item.getPrecioUnitario() == null
                    || item.getPrecioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                item.setPrecioUnitario(prodReal.getPrecio());
            }
        }
        recalcularTotal(entrega);
        Entrega guardada = entregaRepository.save(entrega);

        // ── Auditoría ────────────────────────────────────────────
        UUID creadorId = entrega.getCreadoPor() != null ? entrega.getCreadoPor().getId() : null;
        String creadorNombre = entrega.getCreadoPor() != null ? entrega.getCreadoPor().getNombre() : "Sistema";
        auditoriaService.registrar(
            creadorId, creadorNombre,
            "CREAR_ORDEN", "ORDENES",
            guardada.getId(),
            "Orden #" + guardada.getId().toString().substring(0, 8).toUpperCase()
            + " para " + guardada.getClienteNombre()
            + " — " + guardada.getTipo()
        );
        return guardada;
    }

    // ── Actualizar orden existente ────────────────────────────────────────────

    @Transactional
    public Entrega update(Entrega existing, List<EntregaItem> nuevosItems) {
        resolverCliente(existing);

        existing.getItems().clear();
        for (EntregaItem item : nuevosItems) {
            item.setId(null);
            item.setEntrega(existing);
            if (item.getProducto() != null && item.getProducto().getId() != null) {
                Producto prodReal = productoRepository.findById(item.getProducto().getId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Producto no encontrado: " + item.getProducto().getId()));
                item.setProducto(prodReal);
                if (item.getPrecioUnitario() == null
                        || item.getPrecioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                    item.setPrecioUnitario(prodReal.getPrecio());
                }
            }
        }
        existing.getItems().addAll(nuevosItems);
        recalcularTotal(existing);
        Entrega actualizada = entregaRepository.save(existing);
        auditoriaService.registrarActual(
            "EDITAR_ORDEN", "ORDENES",
            actualizada.getId(),
            "Orden editada #" + actualizada.getId().toString().substring(0, 8).toUpperCase()
            + " - cliente: " + actualizada.getClienteNombre()
        );
        return actualizada;
    }

    // ── Cambiar estado ────────────────────────────────────────────────────────

    @Transactional
    public Entrega cambiarEstado(UUID id, String nuevoEstado, String notasTecnico) {
        Entrega entrega = entregaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada: " + id));

        if (ESTADO_FINALIZADO.equals(entrega.getEstado()) && ESTADO_CANCELADO.equals(nuevoEstado)) {
            throw new IllegalStateException(
                    "Una orden finalizada no se cancela directamente. Registra una devolucion o garantia.");
        }

        if (ESTADO_FINALIZADO.equals(nuevoEstado)) {
            throw new IllegalStateException(
                    "Para finalizar una orden usa el endpoint /confirmar");
        }

        validarTransicion(entrega.getEstado(), nuevoEstado);
        entrega.setEstado(nuevoEstado);

        if (notasTecnico != null && !notasTecnico.isBlank()) {
            entrega.setNotasTecnico(notasTecnico);
        }

        if (ESTADO_COMPLETADO.equals(nuevoEstado)) {
            entrega.setFechaCompletado(LocalDateTime.now());
            Entrega guardada = entregaRepository.save(entrega);
            generarFacturaBorrador(guardada);
            auditoriaService.registrar(
                null, "Sistema",
                "CAMBIAR_ESTADO", "ORDENES",
                guardada.getId(),
                "Orden #" + guardada.getId().toString().substring(0, 8).toUpperCase()
                + " → COMPLETADO (cliente: " + guardada.getClienteNombre() + ")"
            );
            return guardada;
        }

        Entrega guardada2 = entregaRepository.save(entrega);
        auditoriaService.registrar(
            null, "Sistema",
            "CAMBIAR_ESTADO", "ORDENES",
            guardada2.getId(),
            "Orden #" + guardada2.getId().toString().substring(0, 8).toUpperCase()
            + " → " + nuevoEstado + " (cliente: " + guardada2.getClienteNombre() + ")"
        );
        return guardada2;
    }

    // ── Confirmar servicio → FINALIZADO ───────────────────────────────────────

    @Transactional
    public Entrega confirmarServicio(UUID entregaId, UUID adminId) {
        Entrega entrega = entregaRepository.findById(entregaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Orden no encontrada: " + entregaId));

        if (!ESTADO_COMPLETADO.equals(entrega.getEstado())) {
            throw new IllegalStateException(
                    "Solo se pueden finalizar órdenes en estado COMPLETADO. " +
                    "Estado actual: " + entrega.getEstado());
        }

        // PASO 1: Validar stock de todos los ítems antes de tocar nada
        for (EntregaItem item : entrega.getItems()) {
            Producto producto = productoRepository.findById(item.getProducto().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + item.getProducto().getId()));
            int requerido = item.getCantidad() != null ? item.getCantidad() : 1;
            if (producto.getStock() < requerido) {
                throw new IllegalStateException(
                        "Stock insuficiente para \"" + producto.getNombre() + "\". " +
                        "Disponible: " + producto.getStock() + ", requerido: " + requerido + ". " +
                        "Ingresa la compra al proveedor antes de finalizar.");
            }
        }

        // PASO 2: Referencia del admin
        Usuario admin = adminId != null
                ? usuarioRepository.getReferenceById(adminId)
                : null;

        String refOrden = entregaId.toString().substring(0, 8).toUpperCase();

        // PASO 3: Movimientos SALIDA — trigger de Supabase descuenta el stock
        for (EntregaItem item : entrega.getItems()) {
            Producto producto = productoRepository.findById(item.getProducto().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + item.getProducto().getId()));
            int cantidad = item.getCantidad() != null ? item.getCantidad() : 1;

            MovimientoInventario salida = new MovimientoInventario();
            salida.setProducto(producto);
            salida.setTipo("SALIDA");
            salida.setCantidad(cantidad);
            salida.setUsuario(admin);
            salida.setObservacion("Servicio — " + entrega.getClienteNombre()
                    + " — Orden #" + refOrden);
            salida.setOrigenTipo("ENTREGA");
            salida.setOrigenId(entregaId);
            salida.setFecha(LocalDateTime.now());
            movimientoRepository.save(salida);
        }

        // PASO 4: Venta oficial + factura FV — via VentaRegistroService
        Venta venta = new Venta();
        venta.setClienteNombre(entrega.getClienteNombre());
        venta.setClienteTelefono(entrega.getClienteTelefono());
        venta.setCliente(entrega.getCliente());
        venta.setVendedor(admin);
        venta.setEstado("COMPLETADA");
        venta.setTotal(entrega.getTotalOrden());
        venta.setFecha(LocalDateTime.now());

        for (EntregaItem item : entrega.getItems()) {
            VentaItem vi = new VentaItem();
            vi.setVenta(venta);
            vi.setProducto(item.getProducto());
            vi.setCantidad(item.getCantidad());
            vi.setPrecioUnitario(item.getPrecioUnitario());
            venta.getItems().add(vi);
        }
        ventaRegistroService.guardarConFactura(venta);

        // PASO 5: Finalizar orden
        entrega.setEstado(ESTADO_FINALIZADO);
        entrega.setFechaEntrega(LocalDateTime.now());
        Entrega finalizada = entregaRepository.save(entrega);

        // ── Auditoría ────────────────────────────────────────────
        String adminNombre = admin != null ? admin.getNombre() : "Admin";
        auditoriaService.registrar(
            adminId, adminNombre,
            "CONFIRMAR_ORDEN", "ORDENES",
            finalizada.getId(),
            "Orden #" + entregaId.toString().substring(0, 8).toUpperCase()
            + " FINALIZADA — cliente: " + finalizada.getClienteNombre()
            + " — total: $" + finalizada.getTotalOrden()
        );
        return finalizada;
    }

    // ── Registrar pago ────────────────────────────────────────────────────────

    @Transactional
    public Entrega registrarPago(UUID entregaId, BigDecimal monto,
                                  String notas, UUID usuarioId) {
        Entrega entrega = entregaRepository.findById(entregaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Orden no encontrada: " + entregaId));

        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser mayor a cero");
        }

        PagoOrden pago = new PagoOrden();
        pago.setEntrega(entrega);
        pago.setMonto(monto);
        pago.setNotas(notas);
        pago.setFecha(LocalDateTime.now());
        if (usuarioId != null) {
            pago.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        }
        pagoOrdenRepository.save(pago);

        BigDecimal totalPagado = pagoOrdenRepository.sumMontoByEntregaId(entregaId);
        if (totalPagado == null) totalPagado = BigDecimal.ZERO;
        entrega.setAnticipoRecibido(totalPagado);

        BigDecimal totalOrden = entrega.getTotalOrden() != null
                ? entrega.getTotalOrden() : BigDecimal.ZERO;

        if (totalOrden.compareTo(BigDecimal.ZERO) == 0) {
            entrega.setEstadoPago(PAGO_COMPLETO);
        } else if (totalPagado.compareTo(totalOrden) >= 0) {
            entrega.setEstadoPago(PAGO_COMPLETO);
        } else {
            long cantidadPagos = pagoOrdenRepository.countByEntregaId(entregaId);
            entrega.setEstadoPago(cantidadPagos <= 1 ? PAGO_ANTICIPO : PAGO_PARCIAL);
        }

        Entrega actualizada = entregaRepository.save(entrega);

        // ── Auditoría ────────────────────────────────────────────
        auditoriaService.registrar(
            usuarioId, null,
            "REGISTRAR_PAGO", "ORDENES",
            entregaId,
            "Pago $" + monto + " en orden #"
            + entregaId.toString().substring(0, 8).toUpperCase()
            + " — cliente: " + actualizada.getClienteNombre()
            + " — estado pago: " + actualizada.getEstadoPago()
        );
        return actualizada;
    }

    // ── Configurar anticipo ───────────────────────────────────────────────────

    @Transactional
    public Entrega configurarAnticipo(UUID entregaId, BigDecimal porcentaje) {
        if (porcentaje == null
                || porcentaje.compareTo(BigDecimal.ZERO) < 0
                || porcentaje.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("El porcentaje debe estar entre 0 y 100");
        }
        Entrega entrega = entregaRepository.findById(entregaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Orden no encontrada: " + entregaId));
        entrega.setAnticipoPorcentaje(porcentaje);
        Entrega actualizada = entregaRepository.save(entrega);
        auditoriaService.registrarActual(
            "CONFIGURAR_ANTICIPO", "ORDENES",
            actualizada.getId(),
            "Anticipo configurado en orden #"
            + entregaId.toString().substring(0, 8).toUpperCase()
            + " - porcentaje: " + porcentaje + "%"
        );
        return actualizada;
    }

    // ── Estadísticas ──────────────────────────────────────────────────────────

    public Map<String, Long> contarPorEstado() {
        return Map.of(
            ESTADO_PENDIENTE,  entregaRepository.countByEstado(ESTADO_PENDIENTE),
            ESTADO_EN_PROCESO, entregaRepository.countByEstado(ESTADO_EN_PROCESO),
            ESTADO_COMPLETADO, entregaRepository.countByEstado(ESTADO_COMPLETADO),
            ESTADO_FINALIZADO, entregaRepository.countByEstado(ESTADO_FINALIZADO),
            ESTADO_CANCELADO,  entregaRepository.countByEstado(ESTADO_CANCELADO)
        );
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private void resolverCliente(Entrega entrega) {
        if (entrega.getCliente() != null && entrega.getCliente().getId() != null) {
            Cliente cliente = clienteRepository.findById(entrega.getCliente().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cliente no encontrado: " + entrega.getCliente().getId()));
            entrega.setCliente(cliente);
            entrega.setClienteNombre(cliente.getNombre());
            entrega.setClienteTelefono(cliente.getTelefono());
            return;
        }

        String telefono = entrega.getClienteTelefono();
        if (telefono != null && !telefono.isBlank()) {
            clienteRepository.findByTelefono(telefono.trim()).ifPresent(cliente -> {
                entrega.setCliente(cliente);
                entrega.setClienteNombre(cliente.getNombre());
                entrega.setClienteTelefono(cliente.getTelefono());
            });
        }
    }

    private void recalcularTotal(Entrega entrega) {
        BigDecimal totalRepuestos = entrega.getItems().stream()
                .map(i -> {
                    BigDecimal pu  = i.getPrecioUnitario() != null
                            ? i.getPrecioUnitario() : BigDecimal.ZERO;
                    BigDecimal qty = BigDecimal.valueOf(
                            i.getCantidad() != null ? i.getCantidad() : 0);
                    return pu.multiply(qty);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal manoObra = entrega.getManoObra() != null
                ? entrega.getManoObra() : BigDecimal.ZERO;
        entrega.setTotalOrden(totalRepuestos.add(manoObra));
    }

    private void generarFacturaBorrador(Entrega entrega) {
        if (facturaOrdenRepository.existsByEntregaId(entrega.getId())) return;

        BigDecimal total = entrega.getTotalOrden() != null
                ? entrega.getTotalOrden() : BigDecimal.ZERO;
        BigDecimal taxDivisor = configService.getTaxDivisor();
        BigDecimal subtotal = total.divide(taxDivisor, 2, RoundingMode.HALF_UP);
        BigDecimal impuesto = total.subtract(subtotal).setScale(2, RoundingMode.HALF_UP);

        FacturaOrden factura = new FacturaOrden();
        factura.setEntrega(entrega);
        factura.setSubtotal(subtotal);
        factura.setImpuesto(impuesto);
        factura.setTotal(total);
        // numero lo genera la secuencia seq_facturas_orden en Supabase

        facturaOrdenRepository.save(factura);
    }

    private void validarTransicion(String estadoActual, String nuevoEstado) {
        boolean valida = switch (estadoActual) {
            case "PENDIENTE"   -> List.of("EN_PROCESO", "CANCELADO").contains(nuevoEstado);
            case "EN_PROCESO"  -> List.of("COMPLETADO", "CANCELADO").contains(nuevoEstado);
            case "COMPLETADO"  -> List.of("CANCELADO").contains(nuevoEstado);
            case "FINALIZADO"  -> List.of("CANCELADO").contains(nuevoEstado);
            default            -> false;
        };
        if (!valida) {
            throw new IllegalStateException(
                    "Transición no permitida: " + estadoActual + " → " + nuevoEstado);
        }
    }
}
