package com.ergpos.app.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ergpos.app.model.Compra;
import com.ergpos.app.model.CompraItem;
import com.ergpos.app.model.MovimientoInventario;
import com.ergpos.app.model.Producto;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.repositories.CompraRepository;
import com.ergpos.app.repositories.MovimientoInventarioRepository;
import com.ergpos.app.repositories.ProductoRepository;

/**
 * Servicio de negocio para el módulo de Órdenes de Compra a Proveedor.
 * <p>
 * Gestiona el ciclo completo: creación de borradores (PENDIENTE), confirmación
 * (que genera movimientos de ENTRADA y actualiza el stock vía trigger de BD) y
 * cancelación de compras pendientes. Registra auditoría en cada transición de estado.
 * </p>
 *
 * @author ERG-POS Dev Team
 * @since 1.0
 */
@Service
public class CompraService {

    private final CompraRepository               compraRepo;
    private final ProductoRepository             productoRepo;
    private final MovimientoInventarioRepository movimientoRepo;
    private final AuditoriaService               auditoriaService;

    public CompraService(CompraRepository compraRepo,
                         ProductoRepository productoRepo,
                         MovimientoInventarioRepository movimientoRepo,
                         AuditoriaService auditoriaService) {
        this.compraRepo       = compraRepo;
        this.productoRepo     = productoRepo;
        this.movimientoRepo   = movimientoRepo;
        this.auditoriaService = auditoriaService;
    }

    public List<Compra> findAll()                   { return compraRepo.findAll(); }
    public List<Compra> findRecientes()             { return compraRepo.findTop10ByOrderByFechaDesc(); }
    public Optional<Compra> findById(UUID id)       { return compraRepo.findById(id); }
    public List<Compra> findByEstado(String estado) { return compraRepo.findByEstado(estado); }

    /**
     * Persiste una nueva orden de compra en estado PENDIENTE (borrador).
     * <p>No modifica el stock hasta que la compra sea confirmada.</p>
     *
     * @param compra la orden de compra a guardar con sus ítems y proveedor
     * @return la {@link Compra} persistida con total calculado
     * @throws IllegalArgumentException si un producto referenciado no existe
     */
    // Guardar borrador (sin confirmar stock)
    @Transactional
    public Compra save(Compra compra) {
        for (CompraItem item : compra.getItems()) {
            item.setCompra(compra);
            if (item.getPrecioUnitario() == null) {
                Producto p = productoRepo.findById(item.getProducto().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
                item.setPrecioUnitario(p.getPrecio());
            }
        }
        compra.calcularTotal();
        Compra guardada = compraRepo.save(compra);

        java.util.UUID compradorId = guardada.getComprador() != null ? guardada.getComprador().getId() : null;
        String compradorNom = guardada.getComprador() != null ? guardada.getComprador().getNombre() : "Sistema";
        auditoriaService.registrar(
            compradorId, compradorNom,
            "CREAR_COMPRA", "COMPRAS",
            guardada.getId(),
            "Compra a " + guardada.getProveedor().getNombre()
            + " — total: $" + guardada.getTotal()
        );
        return guardada;
    }

    /**
     * Confirma una orden de compra PENDIENTE, registrando movimientos de ENTRADA
     * de inventario para cada ítem. El trigger de base de datos ({@code fn_actualizar_stock})
     * actualiza atómicamente el stock del producto.
     *
     * @param compraId UUID de la compra a confirmar
     * @param usuario  usuario que ejecuta la confirmación (para auditoría)
     * @return la {@link Compra} en estado CONFIRMADA
     * @throws IllegalArgumentException si la compra no existe o un producto no se encuentra
     * @throws IllegalStateException    si la compra no está en estado PENDIENTE
     */
    // Confirmar compra: registra movimientos ENTRADA (el trigger actualiza el stock)
    @Transactional
    public Compra confirmar(UUID compraId, Usuario usuario) {
        Compra compra = compraRepo.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada"));

        if (!"PENDIENTE".equals(compra.getEstado())) {
            throw new IllegalStateException("Solo se pueden confirmar compras en estado PENDIENTE");
        }

        for (CompraItem item : compra.getItems()) {
            Producto producto = productoRepo.findById(item.getProducto().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + item.getProducto().getId()));

            MovimientoInventario mov = new MovimientoInventario();
            mov.setProducto(producto);
            mov.setUsuario(usuario);
            mov.setTipo("ENTRADA");
            mov.setCantidad(item.getCantidad());
            mov.setProveedor(compra.getProveedor());
            mov.setObservacion("Compra #" + (compra.getNumeroFactura() != null
                    ? compra.getNumeroFactura() : compraId.toString().substring(0, 8)));
            mov.setOrigenTipo("COMPRA");
            mov.setOrigenId(compraId);
            movimientoRepo.save(mov);
        }

        compra.setEstado("CONFIRMADA");
        Compra confirmada = compraRepo.save(compra);

        java.util.UUID usuarioId = usuario != null ? usuario.getId() : null;
        String usuarioNom = usuario != null ? usuario.getNombre() : "Sistema";
        auditoriaService.registrar(
            usuarioId, usuarioNom,
            "CONFIRMAR_COMPRA", "COMPRAS",
            confirmada.getId(),
            "Compra confirmada — proveedor: " + confirmada.getProveedor().getNombre()
            + " — total: $" + confirmada.getTotal()
        );
        return confirmada;
    }

    /**
     * Cancela una orden de compra si aún está en estado PENDIENTE.
     *
     * @param compraId UUID de la compra a cancelar
     * @return la {@link Compra} en estado CANCELADA
     * @throws IllegalArgumentException si la compra no existe
     * @throws IllegalStateException    si la compra ya estaba CONFIRMADA
     */
    // Cancelar compra (solo si estaba PENDIENTE)
    @Transactional
    public Compra cancelar(UUID compraId) {
        Compra compra = compraRepo.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada"));

        if ("CONFIRMADA".equals(compra.getEstado())) {
            throw new IllegalStateException("No se puede cancelar una compra ya confirmada");
        }

        compra.setEstado("CANCELADA");
        Compra cancelada = compraRepo.save(compra);
        auditoriaService.registrarActual(
            "CANCELAR_COMPRA", "COMPRAS",
            cancelada.getId(),
            "Compra cancelada — proveedor: " + cancelada.getProveedor().getNombre()
        );
        return cancelada;
    }
}
