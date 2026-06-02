package com.ergpos.app.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ergpos.app.model.DevolucionGarantia;
import com.ergpos.app.model.DevolucionGarantiaItem;
import com.ergpos.app.model.Entrega;
import com.ergpos.app.model.EntregaItem;
import com.ergpos.app.model.MovimientoInventario;
import com.ergpos.app.model.Producto;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.model.Venta;
import com.ergpos.app.model.VentaItem;
import com.ergpos.app.repositories.DevolucionGarantiaItemRepository;
import com.ergpos.app.repositories.DevolucionGarantiaRepository;
import com.ergpos.app.repositories.EntregaRepository;
import com.ergpos.app.repositories.MovimientoInventarioRepository;
import com.ergpos.app.repositories.ProductoRepository;
import com.ergpos.app.repositories.UsuarioRepository;
import com.ergpos.app.repositories.VentaRepository;

@Service
public class DevolucionGarantiaService {

    private static final String TIPO_DEVOLUCION = "DEVOLUCION";
    private static final String TIPO_GARANTIA = "GARANTIA";
    private static final String ESTADO_COMPLETADA = "COMPLETADA";
    private static final String ESTADO_FINALIZADO = "FINALIZADO";
    private static final String ESTADO_ANULADA = "ANULADA";

    private final DevolucionGarantiaRepository devolucionRepository;
    private final DevolucionGarantiaItemRepository itemRepository;
    private final VentaRepository ventaRepository;
    private final EntregaRepository entregaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final AuditoriaService auditoriaService;

    public DevolucionGarantiaService(
            DevolucionGarantiaRepository devolucionRepository,
            DevolucionGarantiaItemRepository itemRepository,
            VentaRepository ventaRepository,
            EntregaRepository entregaRepository,
            ProductoRepository productoRepository,
            UsuarioRepository usuarioRepository,
            MovimientoInventarioRepository movimientoRepository,
            AuditoriaService auditoriaService) {
        this.devolucionRepository = devolucionRepository;
        this.itemRepository = itemRepository;
        this.ventaRepository = ventaRepository;
        this.entregaRepository = entregaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<DevolucionGarantia> findAll() {
        return devolucionRepository.findAll();
    }

    public List<DevolucionGarantia> findRecientes() {
        return devolucionRepository.findTop20ByOrderByFechaDesc();
    }

    public Optional<DevolucionGarantia> findById(UUID id) {
        return devolucionRepository.findById(id);
    }

    public List<DevolucionGarantia> findByVenta(UUID ventaId) {
        return devolucionRepository.findByVentaIdOrderByFechaDesc(ventaId);
    }

    public List<DevolucionGarantia> findByEntrega(UUID entregaId) {
        return devolucionRepository.findByEntregaIdOrderByFechaDesc(entregaId);
    }

    public boolean tieneDevolucionActivaPorVenta(UUID ventaId) {
        return devolucionRepository.countActivasByVentaId(ventaId) > 0;
    }

    public boolean tieneDevolucionActivaPorEntrega(UUID entregaId) {
        return devolucionRepository.countActivasByEntregaId(entregaId) > 0;
    }

    @Transactional
    public DevolucionGarantia registrar(DevolucionGarantia solicitud) {
        String tipo = normalizarTipo(solicitud.getTipo());
        solicitud.setTipo(tipo);
        solicitud.setEstado("REGISTRADA");

        if (solicitud.getVenta() == null && solicitud.getEntrega() == null) {
            throw new IllegalArgumentException("Debes asociar una venta o una orden finalizada");
        }
        if (solicitud.getVenta() != null && solicitud.getEntrega() != null) {
            throw new IllegalArgumentException("Asocia solo una venta o solo una orden, no ambas");
        }

        Venta venta = null;
        Entrega entrega = null;
        if (solicitud.getVenta() != null) {
            venta = ventaRepository.findById(solicitud.getVenta().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
            if (!ESTADO_COMPLETADA.equals(venta.getEstado())) {
                throw new IllegalStateException("Solo se registran devoluciones sobre ventas completadas");
            }
            solicitud.setVenta(venta);
            solicitud.setCliente(venta.getCliente());
            solicitud.setClienteNombre(venta.getClienteNombre());
        } else {
            entrega = entregaRepository.findById(solicitud.getEntrega().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada"));
            if (!ESTADO_FINALIZADO.equals(entrega.getEstado())) {
                throw new IllegalStateException("Solo se registran devoluciones o garantias sobre ordenes finalizadas");
            }
            solicitud.setEntrega(entrega);
            solicitud.setCliente(entrega.getCliente());
            solicitud.setClienteNombre(entrega.getClienteNombre());
        }

        boolean tieneItems = solicitud.getItems() != null && !solicitud.getItems().isEmpty();
        if (!tieneItems) {
            if (entrega != null && entrega.getManoObra() != null && entrega.getManoObra().compareTo(BigDecimal.ZERO) > 0) {
                // Permitir devolución si es una orden de servicio con mano de obra y sin repuestos físicos
            } else {
                throw new IllegalArgumentException("Agrega al menos un producto a devolver o cubrir por garantia");
            }
        }

        Usuario usuario = null;
        if (solicitud.getRegistradoPor() != null && solicitud.getRegistradoPor().getId() != null) {
            usuario = usuarioRepository.findById(solicitud.getRegistradoPor().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            solicitud.setRegistradoPor(usuario);
        }

        BigDecimal totalItems = BigDecimal.ZERO;
        if (tieneItems) {
            for (DevolucionGarantiaItem item : solicitud.getItems()) {
                item.setId(null);
                item.setDevolucion(solicitud);
                Producto producto = productoRepository.findById(item.getProducto().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
                int cantidad = item.getCantidad() != null ? item.getCantidad() : 0;
                if (cantidad <= 0) {
                    throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
                }

                BigDecimal precioOrigen = venta != null
                        ? validarCantidadVenta(venta, producto.getId(), cantidad)
                        : validarCantidadEntrega(entrega, producto.getId(), cantidad);

                item.setProducto(producto);
                if (item.getPrecioUnitario() == null || item.getPrecioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                    item.setPrecioUnitario(precioOrigen);
                }
                totalItems = totalItems.add(item.getSubtotal());
            }
        }

        BigDecimal limiteMaximo = totalItems;
        if (entrega != null && entrega.getManoObra() != null) {
            BigDecimal manoObraDisponible = entrega.getManoObra().subtract(calcularManoObraDevuelta(entrega));
            if (manoObraDisponible.compareTo(BigDecimal.ZERO) < 0) {
                manoObraDisponible = BigDecimal.ZERO;
            }
            limiteMaximo = limiteMaximo.add(manoObraDisponible);
        }

        if (!TIPO_DEVOLUCION.equals(tipo)) {
            solicitud.setAccionDinero("SIN_REEMBOLSO");
            solicitud.setMontoDevuelto(BigDecimal.ZERO);
        } else if (solicitud.getMontoDevuelto() == null
                || solicitud.getMontoDevuelto().compareTo(BigDecimal.ZERO) < 0) {
            solicitud.setMontoDevuelto(BigDecimal.ZERO);
        } else if (solicitud.getMontoDevuelto().compareTo(limiteMaximo) > 0) {
            throw new IllegalArgumentException("El monto devuelto no puede superar el saldo disponible para devolucion (productos y mano de obra). Disponible: " + limiteMaximo);
        }

        DevolucionGarantia guardada = devolucionRepository.save(solicitud);
        if (tieneItems) {
            crearEntradasInventario(guardada, usuario);
        }

        String ref = guardada.getId().toString().substring(0, 8).toUpperCase();
        auditoriaService.registrar(
                usuario != null ? usuario.getId() : null,
                usuario != null ? usuario.getNombre() : "Sistema",
                TIPO_DEVOLUCION.equals(tipo) ? "REGISTRAR_DEVOLUCION" : "REGISTRAR_GARANTIA",
                "DEVOLUCIONES",
                guardada.getId(),
                tipo + " #" + ref + " - cliente: " + guardada.getClienteNombre()
                        + " - monto: $" + guardada.getMontoDevuelto());

        return guardada;
    }

    @Transactional
    public DevolucionGarantia anular(UUID id, String motivo) {
        DevolucionGarantia devolucion = devolucionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registro no encontrado"));
        if (ESTADO_ANULADA.equals(devolucion.getEstado())) {
            throw new IllegalStateException("El registro ya esta anulado");
        }
        devolucion.setEstado(ESTADO_ANULADA);
        devolucion.setNotas((devolucion.getNotas() == null ? "" : devolucion.getNotas() + "\n")
                + "ANULADA: " + (motivo == null ? "Sin motivo" : motivo));
        DevolucionGarantia anulada = devolucionRepository.save(devolucion);

        for (DevolucionGarantiaItem item : anulada.getItems()) {
            MovimientoInventario salida = new MovimientoInventario();
            salida.setProducto(item.getProducto());
            salida.setTipo("SALIDA");
            salida.setCantidad(item.getCantidad());
            salida.setUsuario(anulada.getRegistradoPor());
            salida.setObservacion("Anulacion de " + anulada.getTipo() + " - " + motivo);
            salida.setOrigenTipo("DEVOLUCION");
            salida.setOrigenId(anulada.getId());
            movimientoRepository.save(salida);
        }

        auditoriaService.registrarActual(
                "ANULAR_DEVOLUCION", "DEVOLUCIONES", anulada.getId(),
                "Registro anulado: " + anulada.getTipo() + " - " + motivo);
        return anulada;
    }

    private String normalizarTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) return TIPO_DEVOLUCION;
        String value = tipo.trim().toUpperCase();
        if (!TIPO_DEVOLUCION.equals(value) && !TIPO_GARANTIA.equals(value)) {
            throw new IllegalArgumentException("Tipo invalido. Usa DEVOLUCION o GARANTIA");
        }
        return value;
    }

    private BigDecimal validarCantidadVenta(Venta venta, UUID productoId, int cantidad) {
        VentaItem origen = venta.getItems().stream()
                .filter(i -> i.getProducto() != null && productoId.equals(i.getProducto().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El producto no pertenece a la venta"));
        Long devueltoVal = itemRepository.sumCantidadByVentaAndProducto(venta.getId(), productoId);
        long devuelto = devueltoVal != null ? devueltoVal : 0L;
        long disponible = origen.getCantidad() - devuelto;
        if (cantidad > disponible) {
            throw new IllegalStateException("Cantidad mayor a la disponible para devolver. Disponible: " + disponible);
        }
        return origen.getPrecioUnitario() != null ? origen.getPrecioUnitario() : BigDecimal.ZERO;
    }

    private BigDecimal validarCantidadEntrega(Entrega entrega, UUID productoId, int cantidad) {
        EntregaItem origen = entrega.getItems().stream()
                .filter(i -> i.getProducto() != null && productoId.equals(i.getProducto().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El producto no pertenece a la orden"));
        Long devueltoVal = itemRepository.sumCantidadByEntregaAndProducto(entrega.getId(), productoId);
        long devuelto = devueltoVal != null ? devueltoVal : 0L;
        long disponible = origen.getCantidad() - devuelto;
        if (cantidad > disponible) {
            throw new IllegalStateException("Cantidad mayor a la disponible para devolver. Disponible: " + disponible);
        }
        return origen.getPrecioUnitario() != null ? origen.getPrecioUnitario() : BigDecimal.ZERO;
    }

    private BigDecimal calcularManoObraDevuelta(Entrega entrega) {
        return devolucionRepository.findByEntregaIdOrderByFechaDesc(entrega.getId()).stream()
                .filter(d -> !ESTADO_ANULADA.equals(d.getEstado()))
                .map(d -> {
                    BigDecimal monto = d.getMontoDevuelto() != null ? d.getMontoDevuelto() : BigDecimal.ZERO;
                    BigDecimal totalProductos = d.getItems() != null
                            ? d.getItems().stream()
                                    .map(DevolucionGarantiaItem::getSubtotal)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                            : BigDecimal.ZERO;
                    BigDecimal manoObra = monto.subtract(totalProductos);
                    return manoObra.compareTo(BigDecimal.ZERO) > 0 ? manoObra : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void crearEntradasInventario(DevolucionGarantia devolucion, Usuario usuario) {
        for (DevolucionGarantiaItem item : devolucion.getItems()) {
            MovimientoInventario entrada = new MovimientoInventario();
            entrada.setProducto(item.getProducto());
            entrada.setTipo("ENTRADA");
            entrada.setCantidad(item.getCantidad());
            entrada.setUsuario(usuario);
            entrada.setObservacion(devolucion.getTipo() + " - " + devolucion.getRazon());
            entrada.setOrigenTipo("DEVOLUCION");
            entrada.setOrigenId(devolucion.getId());
            movimientoRepository.save(entrada);
        }
    }
}
