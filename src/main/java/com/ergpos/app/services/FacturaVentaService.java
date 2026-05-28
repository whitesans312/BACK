package com.ergpos.app.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ergpos.app.model.FacturaVenta;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.model.Venta;
import com.ergpos.app.repositories.FacturaVentaRepository;
import com.ergpos.app.repositories.VentaRepository;

@Service
public class FacturaVentaService {

    private final FacturaVentaRepository facturaRepo;
    private final VentaRepository ventaRepo;
    private final AuditoriaService auditoriaService;
    private final ConfiguracionNegocioService configService;

    public FacturaVentaService(FacturaVentaRepository facturaRepo,
                                VentaRepository ventaRepo,
                                AuditoriaService auditoriaService,
                                ConfiguracionNegocioService configService) {
        this.facturaRepo = facturaRepo;
        this.ventaRepo   = ventaRepo;
        this.auditoriaService = auditoriaService;
        this.configService = configService;
    }

    public List<FacturaVenta> findAll()             { return facturaRepo.findAll(); }
    public Optional<FacturaVenta> findById(UUID id) { return facturaRepo.findById(id); }
    public Optional<FacturaVenta> findByVentaId(UUID ventaId) {
        return facturaRepo.findByVentaId(ventaId);
    }



    @Transactional
    public FacturaVenta generarFactura(UUID ventaId, Usuario usuario) {
        if (facturaRepo.findByVentaId(ventaId).isPresent()) {
            throw new IllegalStateException("Ya existe una factura para esta venta");
        }

        Venta venta = ventaRepo.findById(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));

        if (!"COMPLETADA".equals(venta.getEstado())) {
            throw new IllegalStateException("Solo se genera factura para ventas COMPLETADAS");
        }

        // numero lo genera la secuencia seq_facturas_venta en Supabase
        FacturaVenta factura = new FacturaVenta();
        factura.setVenta(venta);

        // Precios finales con IVA incluido → se descomponen para la factura
        BigDecimal totalFinal   = venta.getTotal();
        BigDecimal taxDivisor   = configService.getTaxDivisor();
        BigDecimal baseGravable = totalFinal.divide(taxDivisor, 2, RoundingMode.HALF_UP);
        BigDecimal iva          = totalFinal.subtract(baseGravable).setScale(2, RoundingMode.HALF_UP);

        factura.setSubtotal(baseGravable);
        factura.setImpuesto(iva);
        factura.setTotal(totalFinal);

        FacturaVenta guardada = facturaRepo.save(factura);
        auditoriaService.registrar(
            usuario != null ? usuario.getId() : null,
            usuario != null ? usuario.getNombre() : "Sistema",
            "GENERAR_FACTURA_VENTA", "VENTAS",
            guardada.getId(),
            "Factura generada para venta #" + ventaId.toString().substring(0, 8).toUpperCase()
        );
        return guardada;
    }
}
