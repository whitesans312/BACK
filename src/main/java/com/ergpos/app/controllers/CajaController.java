package com.ergpos.app.controllers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.Caja;
import com.ergpos.app.model.MovimientoCaja;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.repositories.CajaRepository;
import com.ergpos.app.repositories.MovimientoCajaRepository;
import com.ergpos.app.services.AuditoriaService;
import com.ergpos.app.services.CajaService;

@RestController
@RequestMapping("/api/cajas")
public class CajaController {

    private final CajaService cajaService;
    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final AuditoriaService auditoriaService;

    public CajaController(CajaService cajaService, CajaRepository cajaRepository,
                         MovimientoCajaRepository movimientoCajaRepository,
                         AuditoriaService auditoriaService) {
        this.cajaService = cajaService;
        this.cajaRepository = cajaRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * GET /api/cajas/abiertas
     * Retorna todas las cajas abiertas actualmente
     */
    @GetMapping("/abiertas")
    public ResponseEntity<List<Caja>> getCajasAbiertas() {
        List<Caja> cajas = cajaService.getCajasAbiertas();
        return ResponseEntity.ok(cajas);
    }

    /**
     * GET /api/cajas/actual
     * Retorna la caja abierta del usuario actual
     */
    @GetMapping("/actual")
    public ResponseEntity<Caja> getCajaActual(@RequestAttribute Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        Caja caja = cajaService.getCajaActualByUsuario(usuario.getId());
        if (caja == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(caja);
    }

    /**
     * GET /api/cajas/{id}
     * Retorna una caja por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Caja> getCaja(@PathVariable UUID id) {
        return cajaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/cajas/{id}/resumen
     * Retorna resumen de una caja con totales de movimientos
     */
    @GetMapping("/{id}/resumen")
    public ResponseEntity<Map<String, Object>> getCajaResumen(@PathVariable UUID id) {
        Map<String, Object> resumen = cajaService.getCajaResumen(id);
        return ResponseEntity.ok(resumen);
    }

    /**
     * POST /api/cajas/abrir
     * Abre una nueva caja
     */
    @PostMapping("/abrir")
    public ResponseEntity<Caja> abrirCaja(
            @RequestAttribute Usuario usuario,
            @RequestBody Map<String, BigDecimal> body) {
        BigDecimal montoInicial = body.getOrDefault("montoInicial", BigDecimal.ZERO);
        Caja caja = cajaService.abrirCaja(usuario, montoInicial);
        auditoriaService.registrarActual("ABRIR_CAJA", "CAJAS", caja.getId(), 
            "Caja abierta con monto inicial: $" + montoInicial);
        return ResponseEntity.ok(caja);
    }

    /**
     * PATCH /api/cajas/{id}/cerrar
     * Cierra una caja existente
     */
    @PatchMapping("/{id}/cerrar")
    public ResponseEntity<Caja> cerrarCaja(
            @PathVariable UUID id,
            @RequestAttribute Usuario usuario,
            @RequestBody Map<String, BigDecimal> body) {
        BigDecimal montoFinal = body.get("montoFinal");
        if (montoFinal == null) {
            return ResponseEntity.badRequest().build();
        }
        Caja caja = cajaService.cerrarCaja(id, montoFinal);
        auditoriaService.registrarActual("CERRAR_CAJA", "CAJAS", id,
            "Caja cerrada. Monto final: $" + montoFinal + ". Saldo: $" + caja.getSaldo());
        return ResponseEntity.ok(caja);
    }

    /**
     * POST /api/cajas/{cajaId}/movimientos
     * Registra un movimiento en la caja
     */
    @PostMapping("/{cajaId}/movimientos")
    public ResponseEntity<MovimientoCaja> registrarMovimiento(
            @PathVariable UUID cajaId,
            @RequestAttribute Usuario usuario,
            @RequestBody Map<String, Object> body) {
        String tipo = (String) body.get("tipo");
        String concepto = (String) body.get("concepto");
        String referencia = (String) body.get("referencia");

        if (tipo == null || concepto == null || body.get("monto") == null) {
            return ResponseEntity.badRequest().build();
        }
        BigDecimal monto = new BigDecimal(body.get("monto").toString());

        MovimientoCaja movimiento = cajaService.registrarMovimiento(cajaId, tipo, concepto, monto, referencia, usuario);
        auditoriaService.registrarActual("REGISTRAR_MOVIMIENTO_CAJA", "CAJAS", cajaId,
            "Movimiento: " + tipo + " - " + concepto + " ($" + monto + ")");
        return ResponseEntity.ok(movimiento);
    }

    /**
     * GET /api/cajas/{cajaId}/movimientos
     * Lista movimientos de una caja
     */
    @GetMapping("/{cajaId}/movimientos")
    public ResponseEntity<List<MovimientoCaja>> getMovimientos(
            @PathVariable UUID cajaId,
            @RequestParam(defaultValue = "100") int limit) {
        var pageable = org.springframework.data.domain.PageRequest.of(0, Math.min(limit, 1000));
        List<MovimientoCaja> movimientos = movimientoCajaRepository.findByCajaIdOrderByFechaDesc(cajaId, pageable);
        return ResponseEntity.ok(movimientos);
    }
}
