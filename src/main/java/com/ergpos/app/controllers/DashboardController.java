package com.ergpos.app.controllers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.Auditoria;
import com.ergpos.app.repositories.EntregaRepository;
import com.ergpos.app.repositories.ProductoRepository;
import com.ergpos.app.repositories.VentaRepository;
import com.ergpos.app.services.AuditoriaService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final EntregaRepository   entregaRepository;
    private final ProductoRepository  productoRepository;
    private final VentaRepository     ventaRepository;
    private final AuditoriaService    auditoriaService;

    public DashboardController(EntregaRepository entregaRepository,
                               ProductoRepository productoRepository,
                               VentaRepository ventaRepository,
                               AuditoriaService auditoriaService) {
        this.entregaRepository  = entregaRepository;
        this.productoRepository = productoRepository;
        this.ventaRepository    = ventaRepository;
        this.auditoriaService   = auditoriaService;
    }

    /**
     * GET /api/dashboard/resumen
     * Retorna en un solo request todos los datos que necesita el Dashboard:
     *  - alertas (contadores de cosas urgentes)
     *  - KPIs del día
     *  - últimas 8 acciones del log de auditoría
     */
    @GetMapping("/resumen")
    public Map<String, Object> getResumen() {

        LocalDateTime hoyInicio = LocalDate.now().atStartOfDay();
        LocalDateTime hoyFin    = hoyInicio.plusDays(1);

        // ── Alertas ────────────────────────────────────────────────
        long ordenesCompletadasSinConfirmar =
                entregaRepository.countByEstado("COMPLETADO");

        // Excluye CANCELADAS: solo órdenes activas con pago pendiente/parcial son alerta real
        long ordenesPagoPendiente =
                entregaRepository.countByEstadoPagoInAndEstadoNot(
                        List.of("PENDIENTE", "ANTICIPO", "PARCIAL"), "CANCELADO");

        // Solo las finalizadas con pago pendiente (las más urgentes)
        long ordenesFinalizadasConDeuda =
                entregaRepository.countByEstadoAndEstadoPagoIn(
                        "FINALIZADO", List.of("PENDIENTE", "ANTICIPO", "PARCIAL"));

        long productosStockBajo =
                productoRepository.findProductosConStockBajo().size();

        // ── KPIs del día ───────────────────────────────────────────
        // Solo ventas COMPLETADAS hoy (excluye CANCELADAS)
        long ventasHoy = ventaRepository.countCompletadasEnRango(hoyInicio, hoyFin);

        Double totalVentasHoy = ventaRepository
                .sumTotalCompletadasEnRango(hoyInicio, hoyFin);

        // ── Últimas acciones de auditoría ──────────────────────────
        List<Auditoria> ultimasAcciones = auditoriaService.findRecientes(8);

        // ── Respuesta ──────────────────────────────────────────────
        Map<String, Object> resp = new HashMap<>();

        resp.put("alertas", Map.of(
            "ordenesCompletadasSinConfirmar", ordenesCompletadasSinConfirmar,
            "ordenesPagoPendiente",           ordenesPagoPendiente,
            "ordenesFinalizadasConDeuda",     ordenesFinalizadasConDeuda,
            "productosStockBajo",             productosStockBajo
        ));

        resp.put("kpis", Map.of(
            "ventasHoy",      ventasHoy,
            "totalVentasHoy", totalVentasHoy != null ? totalVentasHoy : 0.0
        ));

        resp.put("ultimasAcciones", ultimasAcciones);

        return resp;
    }
}
