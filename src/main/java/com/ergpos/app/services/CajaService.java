package com.ergpos.app.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ergpos.app.model.Caja;
import com.ergpos.app.model.MovimientoCaja;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.repositories.CajaRepository;
import com.ergpos.app.repositories.MovimientoCajaRepository;

@Service
public class CajaService {

    private static final Set<String> TIPOS_MOVIMIENTO_VALIDOS = Set.of(
            "INGRESO",
            "PAGO_VENTA",
            "PAGO_ORDEN",
            "EGRESO",
            "COMPRA",
            "AJUSTE",
            "DEVOLUCION"
    );

    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;

    public CajaService(CajaRepository cajaRepository, MovimientoCajaRepository movimientoCajaRepository) {
        this.cajaRepository = cajaRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
    }

    /**
     * Abre una nueva caja para un usuario
     */
    public Caja abrirCaja(Usuario usuario, BigDecimal montoInicial) {
        if (usuario == null) {
            throw new RuntimeException("Usuario requerido para abrir caja");
        }
        if (montoInicial == null) {
            montoInicial = BigDecimal.ZERO;
        }
        if (montoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("El monto inicial no puede ser negativo");
        }
        Caja cajaAbierta = cajaRepository.findCajaAbiertaByUsuario(usuario.getId());
        if (cajaAbierta != null) {
            throw new RuntimeException("El usuario ya tiene una caja abierta");
        }

        Caja caja = new Caja();
        caja.setUsuario(usuario);
        caja.setMontoInicial(montoInicial);
        caja.setMontoFinal(null);
        caja.setEstado("ABIERTA");
        return cajaRepository.save(caja);
    }

    /**
     * Cierra una caja existente
     */
    public Caja cerrarCaja(UUID cajaId, BigDecimal montoFinal) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new RuntimeException("Caja no encontrada"));
        if (!"ABIERTA".equals(caja.getEstado())) {
            throw new RuntimeException("Solo se puede cerrar una caja abierta");
        }
        if (montoFinal == null || montoFinal.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("El monto final debe ser mayor o igual a cero");
        }
        caja.setMontoFinal(montoFinal);
        caja.setEstado("CERRADA");
        caja.setFechaCierre(LocalDateTime.now());
        return cajaRepository.save(caja);
    }

    /**
     * Registra un movimiento en una caja
     */
    public MovimientoCaja registrarMovimiento(UUID cajaId, String tipo, String concepto, 
                                             BigDecimal monto, String referencia, Usuario usuario) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new RuntimeException("Caja no encontrada"));
        
        if (!"ABIERTA".equals(caja.getEstado())) {
            throw new RuntimeException("La caja no está abierta");
        }

        if (tipo == null || !TIPOS_MOVIMIENTO_VALIDOS.contains(tipo)) {
            throw new RuntimeException("Tipo de movimiento de caja no valido: " + tipo);
        }
        if (concepto == null || concepto.isBlank()) {
            throw new RuntimeException("El concepto es obligatorio");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El monto debe ser mayor a cero");
        }

        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setCaja(caja);
        movimiento.setTipo(tipo);
        movimiento.setConcepto(concepto);
        movimiento.setMonto(monto);
        movimiento.setReferencia(referencia);
        movimiento.setUsuario(usuario);
        
        return movimientoCajaRepository.save(movimiento);
    }

    /**
     * Obtiene la caja actual abierta para un usuario
     */
    public Caja getCajaActualByUsuario(UUID usuarioId) {
        return cajaRepository.findCajaAbiertaByUsuario(usuarioId);
    }

    /**
     * Obtiene todas las cajas abiertas
     */
    public List<Caja> getCajasAbiertas() {
        return cajaRepository.findCajasAbiertas();
    }

    /**
     * Obtiene detalles de una caja con resumen de movimientos
     */
    public Map<String, Object> getCajaResumen(UUID cajaId) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new RuntimeException("Caja no encontrada"));

        BigDecimal ingresos = movimientoCajaRepository.sumIngresosByCaja(cajaId);
        BigDecimal egresos = movimientoCajaRepository.sumEgresosByCaja(cajaId);
        ingresos = ingresos != null ? ingresos : BigDecimal.ZERO;
        egresos = egresos != null ? egresos : BigDecimal.ZERO;
        BigDecimal saldoCalculado = caja.getMontoInicial()
                .add(ingresos)
                .subtract(egresos);

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("id", caja.getId());
        resumen.put("usuario", caja.getUsuario().getNombre());
        resumen.put("montoInicial", caja.getMontoInicial());
        resumen.put("montoFinal", caja.getMontoFinal());
        resumen.put("saldo", caja.getMontoFinal() != null ? caja.getSaldo() : saldoCalculado);
        resumen.put("saldoCalculado", saldoCalculado);
        resumen.put("ingresos", ingresos);
        resumen.put("egresos", egresos);
        resumen.put("estado", caja.getEstado());
        resumen.put("fechaApertura", caja.getFechaApertura());
        resumen.put("fechaCierre", caja.getFechaCierre());

        return resumen;
    }
}
