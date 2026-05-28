package com.ergpos.app.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ergpos.app.model.Cliente;
import com.ergpos.app.model.Entrega;
import com.ergpos.app.model.Venta;
import com.ergpos.app.model.DevolucionGarantia;
import com.ergpos.app.repositories.ClienteRepository;
import com.ergpos.app.repositories.EntregaRepository;
import com.ergpos.app.repositories.VentaRepository;
import com.ergpos.app.repositories.DevolucionGarantiaRepository;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final EntregaRepository entregaRepository;
    private final DevolucionGarantiaRepository devolucionGarantiaRepository;

    public ClienteService(ClienteRepository clienteRepository,
                          VentaRepository ventaRepository,
                          EntregaRepository entregaRepository,
                          DevolucionGarantiaRepository devolucionGarantiaRepository) {
        this.clienteRepository = clienteRepository;
        this.ventaRepository = ventaRepository;
        this.entregaRepository = entregaRepository;
        this.devolucionGarantiaRepository = devolucionGarantiaRepository;
    }

    // ── Consultas ───────────────────────────────────────────

    public List<Cliente> findAll() {
        return clienteRepository.findAll();
    }

    public List<Cliente> findActivos() {
        return clienteRepository.findByActivoTrue();
    }

    public List<Cliente> buscar(String q) {
        return clienteRepository.buscar(q);
    }

    public Optional<Cliente> findById(UUID id) {
        return clienteRepository.findById(id);
    }

    public Map<String, Object> getPerfilCompleto(UUID clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));

        String telefono = cliente.getTelefono();

        List<Venta> ventas = ventaRepository.findByClienteIdOrTelefonoOrderByFechaDesc(clienteId, telefono);

        List<Entrega> ordenes = entregaRepository.findByClienteIdOrTelefonoOrderByFechaCreacionDesc(clienteId, telefono);

        List<DevolucionGarantia> devoluciones = devolucionGarantiaRepository.findByClienteIdOrTelefonoOrderByFechaDesc(clienteId, telefono);

        BigDecimal totalVentas = ventas.stream()
                .filter(v -> "COMPLETADA".equals(v.getEstado()))
                .map(v -> v.getTotal() != null ? v.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOrdenes = ordenes.stream()
                .filter(o -> !"CANCELADO".equals(o.getEstado()))
                .map(o -> o.getTotalOrden() != null ? o.getTotalOrden() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalComprado = totalVentas.add(totalOrdenes);

        long ventasCompletadas = ventas.stream().filter(v -> "COMPLETADA".equals(v.getEstado())).count();
        long ordenesFinalizadas = ordenes.stream().filter(o -> "FINALIZADO".equals(o.getEstado())).count();
        long ordenesActivas = ordenes.stream()
                .filter(o -> !"FINALIZADO".equals(o.getEstado()) && !"CANCELADO".equals(o.getEstado()))
                .count();

        Object ultimaActividad = null;
        if (!ventas.isEmpty() && (ordenes.isEmpty() ||
                ventas.get(0).getFecha().isAfter(ordenes.get(0).getFechaCreacion()))) {
            ultimaActividad = ventas.get(0).getFecha();
        } else if (!ordenes.isEmpty()) {
            ultimaActividad = ordenes.get(0).getFechaCreacion();
        }

        return Map.of(
            "cliente", cliente,
            "kpis", Map.of(
                "totalComprado", totalComprado,
                "totalVentas", totalVentas,
                "totalOrdenes", totalOrdenes,
                "cantidadVentas", ventas.size(),
                "ventasCompletadas", ventasCompletadas,
                "cantidadOrdenes", ordenes.size(),
                "ordenesFinalizadas", ordenesFinalizadas,
                "ordenesActivas", ordenesActivas,
                "frecuenciaTotal", ventas.size() + ordenes.size(),
                "ultimaActividad", ultimaActividad != null ? ultimaActividad : ""
            ),
            "ventas", ventas,
            "ordenes", ordenes,
            "devoluciones", devoluciones
        );
    }

    public Optional<Cliente> findByTelefono(String telefono) {
        return clienteRepository.findByTelefono(telefono);
    }

    // ── Guardar (CREATE / UPDATE) ───────────────────────────

    public Cliente save(Cliente cliente) {

        // ── Normalización ─────────────────────────────
        if (cliente.getEmail() != null) {
            cliente.setEmail(cliente.getEmail().trim().toLowerCase());
        }

        if (cliente.getTelefono() != null) {
            cliente.setTelefono(cliente.getTelefono().trim());
        }

        // ── Validación de contacto ───────────────────
        if (!cliente.tieneContacto()) {
            throw new IllegalArgumentException("El cliente debe tener al menos teléfono o email");
        }

        // ── Validar duplicado por teléfono ───────────
        if (cliente.getTelefono() != null && !cliente.getTelefono().isBlank()) {

            Optional<Cliente> existente = clienteRepository.findByTelefono(cliente.getTelefono());

            if (existente.isPresent() &&
                (cliente.getId() == null || !existente.get().getId().equals(cliente.getId()))) {

                throw new IllegalArgumentException("Ya existe un cliente con ese teléfono");
            }
        }

        // ── Validar duplicado por email ──────────────
        if (cliente.getEmail() != null && !cliente.getEmail().isBlank()) {

            Optional<Cliente> existente = clienteRepository.findByEmail(cliente.getEmail());

            if (existente.isPresent() &&
                (cliente.getId() == null || !existente.get().getId().equals(cliente.getId()))) {

                throw new IllegalArgumentException("Ya existe un cliente con ese email");
            }
        }

        return clienteRepository.save(cliente);
    }

    // ── Obtener o crear cliente automáticamente ─────────────

    public Cliente obtenerOCrear(String telefono, String nombre) {

        if (telefono == null || telefono.isBlank()) {
            throw new IllegalArgumentException("El teléfono es obligatorio para crear o buscar cliente");
        }

        return clienteRepository.findByTelefono(telefono)
            .orElseGet(() -> {
                Cliente nuevo = new Cliente();
                nuevo.setTelefono(telefono);
                nuevo.setNombre(nombre != null && !nombre.isBlank() ? nombre : "Cliente");
                return clienteRepository.save(nuevo);
            });
    }

    // ── Validaciones auxiliares ─────────────────────────────

    public boolean existsByTelefono(String telefono) {
        return telefono != null && !telefono.isBlank()
               && clienteRepository.existsByTelefono(telefono);
    }

    public boolean existsByEmail(String email) {
        return email != null && !email.isBlank()
               && clienteRepository.existsByEmail(email);
    }

    // ── Soft delete ─────────────────────────────────────────

    public void softDelete(UUID id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));

        cliente.setActivo(false);
        clienteRepository.save(cliente);
    }

    // ── Activar / desactivar ───────────────────────────────

    public Cliente toggleActivo(UUID id, boolean activo) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));

        cliente.setActivo(activo);
        return clienteRepository.save(cliente);
    }
}
