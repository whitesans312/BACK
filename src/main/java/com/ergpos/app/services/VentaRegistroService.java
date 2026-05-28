package com.ergpos.app.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ergpos.app.model.FacturaVenta;
import com.ergpos.app.model.Venta;
import com.ergpos.app.repositories.FacturaVentaRepository;
import com.ergpos.app.repositories.VentaRepository;

@Service
public class VentaRegistroService {

    private final VentaRepository        ventaRepository;
    private final FacturaVentaRepository facturaVentaRepository;
    private final ConfiguracionNegocioService configService;

    public VentaRegistroService(VentaRepository ventaRepository,
                                 FacturaVentaRepository facturaVentaRepository,
                                 ConfiguracionNegocioService configService) {
        this.ventaRepository        = ventaRepository;
        this.facturaVentaRepository = facturaVentaRepository;
        this.configService          = configService;
    }

    @Transactional
    public Venta guardarConFactura(Venta venta) {
        Venta guardada = ventaRepository.save(venta);

        BigDecimal total    = guardada.getTotal() != null
                ? guardada.getTotal() : BigDecimal.ZERO;
        BigDecimal taxDivisor = configService.getTaxDivisor();
        BigDecimal subtotal = total.divide(taxDivisor, 2, RoundingMode.HALF_UP);
        BigDecimal impuesto = total.subtract(subtotal).setScale(2, RoundingMode.HALF_UP);

        // numero lo genera la secuencia seq_facturas_venta en Supabase (FV-0001...)
        FacturaVenta factura = new FacturaVenta();
        factura.setVenta(guardada);
        factura.setSubtotal(subtotal);
        factura.setImpuesto(impuesto);
        factura.setTotal(total);
        facturaVentaRepository.save(factura);

        return guardada;
    }
}