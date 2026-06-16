package com.ergpos.app.controllers;

import com.ergpos.app.model.Caja;
import com.ergpos.app.model.Cliente;
import com.ergpos.app.model.Producto;
import com.ergpos.app.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AnaliticaController — Endpoints para analítica avanzada, predicción de stock,
 * segmentación de clientes, rendimiento de técnicos y proyección financiera.
 */
@RestController
@RequestMapping("/api/analitica")
public class AnaliticaController {

    @PersistenceContext
    private EntityManager em;

    // ── 1. Predicción de Agotamiento de Stock ──────────────────────────────────
    @GetMapping("/prediccion-stock")
    public ResponseEntity<List<Map<String, Object>>> getPrediccionStock() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        // Obtener productos activos
        List<Producto> productos = em.createQuery(
                "SELECT p FROM Producto p WHERE p.activo = true", Producto.class)
                .getResultList();

        // Obtener consumos por producto en Ventas (últimos 30 días)
        List<Object[]> ventasConsumo = em.createQuery(
                "SELECT vi.producto.id, SUM(vi.cantidad) " +
                "FROM VentaItem vi " +
                "WHERE vi.venta.estado = 'COMPLETADA' AND vi.venta.fecha >= :since " +
                "GROUP BY vi.producto.id", Object[].class)
                .setParameter("since", since)
                .getResultList();

        // Obtener consumos por producto en Servicios/Entregas (últimos 30 días)
        List<Object[]> entregasConsumo = em.createQuery(
                "SELECT ei.producto.id, SUM(ei.cantidad) " +
                "FROM EntregaItem ei " +
                "WHERE ei.entrega.estado = 'FINALIZADO' AND ei.entrega.fechaEntrega >= :since " +
                "GROUP BY ei.producto.id", Object[].class)
                .setParameter("since", since)
                .getResultList();

        // Mapear consumos
        Map<UUID, Long> consumoMap = new HashMap<>();
        for (Object[] row : ventasConsumo) {
            UUID prodId = (UUID) row[0];
            Long qty = (Long) row[1];
            consumoMap.put(prodId, consumoMap.getOrDefault(prodId, 0L) + qty);
        }
        for (Object[] row : entregasConsumo) {
            UUID prodId = (UUID) row[0];
            Long qty = ((Number) row[1]).longValue(); // SUM can be Long or Integer depending on DB dialect
            consumoMap.put(prodId, consumoMap.getOrDefault(prodId, 0L) + qty);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Producto prod : productos) {
            long totalConsumido = consumoMap.getOrDefault(prod.getId(), 0L);
            double consumoDiario = totalConsumido / 30.0;
            double diasRestantes = 999.0; // Valor seguro por defecto si no hay consumo
            if (consumoDiario > 0) {
                diasRestantes = prod.getStock() / consumoDiario;
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", prod.getId());
            map.put("nombre", prod.getNombre());
            map.put("codigo", prod.getCodigo());
            map.put("stock", prod.getStock());
            map.put("stockMinimo", prod.getStockMinimo());
            map.put("consumo30Dias", totalConsumido);
            map.put("consumoDiarioPromedio", Math.round(consumoDiario * 100.0) / 100.0);
            map.put("diasRestantes", diasRestantes == 999.0 ? "Sin consumo reciente" : Math.round(diasRestantes * 10.0) / 10.0);
            map.put("diasRestantesValor", diasRestantes); // Para ordenamiento en el frontend
            result.add(map);
        }

        // Ordenar: primero los de menos stock restante
        result.sort((a, b) -> Double.compare(
                (Double) a.get("diasRestantesValor"),
                (Double) b.get("diasRestantesValor")
        ));

        return ResponseEntity.ok(result);
    }

    // ── 2. Segmentación de Clientes VIP ────────────────────────────────────────
    @GetMapping("/clientes-vip")
    public ResponseEntity<List<Map<String, Object>>> getClientesVip() {
        // Obtener todos los clientes
        List<Cliente> clientes = em.createQuery(
                "SELECT c FROM Cliente c", Cliente.class)
                .getResultList();

        // Obtener totales por cliente en Ventas Completadas
        List<Object[]> ventasTotales = em.createQuery(
                "SELECT v.cliente.id, SUM(v.total), COUNT(v) " +
                "FROM Venta v " +
                "WHERE v.estado = 'COMPLETADA' AND v.cliente.id IS NOT NULL " +
                "GROUP BY v.cliente.id", Object[].class)
                .getResultList();

        // Obtener totales por cliente en Servicios/Entregas Completadas
        List<Object[]> entregasTotales = em.createQuery(
                "SELECT e.cliente.id, SUM(e.totalOrden), COUNT(e) " +
                "FROM Entrega e " +
                "WHERE e.estado = 'FINALIZADO' AND e.cliente.id IS NOT NULL " +
                "GROUP BY e.cliente.id", Object[].class)
                .getResultList();

        Map<UUID, BigDecimal> gastoMap = new HashMap<>();
        Map<UUID, Long> transaccionesMap = new HashMap<>();

        for (Object[] row : ventasTotales) {
            UUID cliId = (UUID) row[0];
            BigDecimal total = row[1] != null ? BigDecimal.valueOf(((Number) row[1]).doubleValue()) : BigDecimal.ZERO;
            Long count = (Long) row[2];
            gastoMap.put(cliId, total);
            transaccionesMap.put(cliId, count);
        }

        for (Object[] row : entregasTotales) {
            UUID cliId = (UUID) row[0];
            BigDecimal total = row[1] != null ? BigDecimal.valueOf(((Number) row[1]).doubleValue()) : BigDecimal.ZERO;
            Long count = (Long) row[2];
            gastoMap.put(cliId, gastoMap.getOrDefault(cliId, BigDecimal.ZERO).add(total));
            transaccionesMap.put(cliId, transaccionesMap.getOrDefault(cliId, 0L) + count);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Cliente cli : clientes) {
            BigDecimal totalGastado = gastoMap.getOrDefault(cli.getId(), BigDecimal.ZERO);
            long transacciones = transaccionesMap.getOrDefault(cli.getId(), 0L);

            // Segmentar cliente
            String segmento = "Regular";
            if (totalGastado.compareTo(BigDecimal.valueOf(1000000)) >= 0) {
                segmento = "💎 VIP";
            } else if (transacciones >= 4) {
                segmento = "🔥 Frecuente";
            } else if (transacciones > 0) {
                segmento = "Esporádico";
            } else {
                segmento = "Nuevo / Sin compras";
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", cli.getId());
            map.put("nombre", cli.getNombre());
            map.put("identificacion", cli.getDocumento());
            map.put("telefono", cli.getTelefono());
            map.put("email", cli.getEmail());
            map.put("totalGastado", totalGastado.setScale(2, RoundingMode.HALF_UP));
            map.put("totalTransacciones", transacciones);
            map.put("segmento", segmento);
            result.add(map);
        }

        // Ordenar por gasto descendente
        result.sort((a, b) -> ((BigDecimal) b.get("totalGastado")).compareTo((BigDecimal) a.get("totalGastado")));

        return ResponseEntity.ok(result);
    }

    // ── 3. Rendimiento de Técnicos ─────────────────────────────────────────────
    @GetMapping("/tecnicos-performance")
    public ResponseEntity<List<Map<String, Object>>> getTecnicosPerformance() {
        // Obtener técnicos y administradores que realicen soporte técnico
        List<Usuario> tecnicos = em.createQuery(
                "SELECT u FROM Usuario u JOIN u.rol r WHERE r.nombre IN ('TECNICO', 'ADMIN') AND u.activo = true", Usuario.class)
                .getResultList();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Usuario tec : tecnicos) {
            // Ordenes completadas
            List<Object[]> completadasData = em.createQuery(
                    "SELECT COUNT(e), SUM(e.manoObra) " +
                    "FROM Entrega e " +
                    "WHERE e.tecnico.id = :tecId AND e.estado = 'FINALIZADO'", Object[].class)
                    .setParameter("tecId", tec.getId())
                    .getResultList();

            long completadas = 0;
            BigDecimal manoObra = BigDecimal.ZERO;
            if (!completadasData.isEmpty() && completadasData.get(0)[0] != null) {
                completadas = (Long) completadasData.get(0)[0];
                manoObra = completadasData.get(0)[1] != null ?
                        BigDecimal.valueOf(((Number) completadasData.get(0)[1]).doubleValue()) : BigDecimal.ZERO;
            }

            // Ordenes activas (PENDIENTE / EN_PROCESO)
            long activas = em.createQuery(
                    "SELECT COUNT(e) FROM Entrega e WHERE e.tecnico.id = :tecId AND e.estado IN ('PENDIENTE', 'EN_PROCESO')", Long.class)
                    .setParameter("tecId", tec.getId())
                    .getSingleResult();

            // Tiempo promedio de resolución (diferencia entre fecha_creacion y fecha_completado)
            List<Object[]> tiempos = em.createQuery(
                    "SELECT e.fechaCreacion, e.fechaCompletado " +
                    "FROM Entrega e " +
                    "WHERE e.tecnico.id = :tecId AND e.estado = 'FINALIZADO' AND e.fechaCompletado IS NOT NULL", Object[].class)
                    .setParameter("tecId", tec.getId())
                    .getResultList();

            double promedioHoras = 0;
            if (!tiempos.isEmpty()) {
                long totalMinutos = 0;
                long validCount = 0;
                for (Object[] row : tiempos) {
                    LocalDateTime creado = (LocalDateTime) row[0];
                    LocalDateTime completado = (LocalDateTime) row[1];
                    if (creado != null && completado != null) {
                        totalMinutos += Duration.between(creado, completado).toMinutes();
                        validCount++;
                    }
                }
                if (validCount > 0) {
                    promedioHoras = (totalMinutos / 60.0) / validCount;
                }
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", tec.getId());
            map.put("nombre", tec.getNombre());
            map.put("email", tec.getEmail());
            map.put("ordenesCompletadas", completadas);
            map.put("ordenesActivas", activas);
            map.put("ingresosManoObra", manoObra.setScale(2, RoundingMode.HALF_UP));
            map.put("tiempoResolucionHoras", Math.round(promedioHoras * 10.0) / 10.0);
            result.add(map);
        }

        // Ordenar por órdenes completadas descendente
        result.sort((a, b) -> Long.compare((Long) b.get("ordenesCompletadas"), (Long) a.get("ordenesCompletadas")));

        return ResponseEntity.ok(result);
    }

    // ── 4. Proyección Financiera (Forecast) ───────────────────────────────────
    @GetMapping("/proyeccion")
    public ResponseEntity<Map<String, Object>> getProyeccion() {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> historicoList = new ArrayList<>();

        // Calcular los totales de las últimas 4 semanas
        double[] semanales = new double[4];
        for (int i = 0; i < 4; i++) {
            LocalDateTime inicioSemana = now.minusWeeks(4 - i).with(java.time.DayOfWeek.MONDAY).toLocalDate().atStartOfDay();
            LocalDateTime finSemana = inicioSemana.plusDays(7);

            // Ventas POS en esta semana
            Double totalVentas = em.createQuery(
                    "SELECT COALESCE(SUM(v.total), 0.0) FROM Venta v WHERE v.estado = 'COMPLETADA' AND v.fecha >= :inicio AND v.fecha < :fin", Double.class)
                    .setParameter("inicio", inicioSemana)
                    .setParameter("fin", finSemana)
                    .getSingleResult();

            // Mano de obra en esta semana
            Double totalManoObra = em.createQuery(
                    "SELECT COALESCE(SUM(e.manoObra), 0.0) FROM Entrega e WHERE e.estado = 'FINALIZADO' AND e.fechaEntrega >= :inicio AND e.fechaEntrega < :fin", Double.class)
                    .setParameter("inicio", inicioSemana)
                    .setParameter("fin", finSemana)
                    .getSingleResult();

            double totalSemana = totalVentas + totalManoObra;
            semanales[i] = totalSemana;

            Map<String, Object> hMap = new HashMap<>();
            hMap.put("nombre", "Semana -" + (3 - i));
            hMap.put("total", totalSemana);
            historicoList.add(hMap);
        }

        // Proyección de las siguientes 4 semanas usando regresión lineal simple (y = mx + c)
        // x: 0, 1, 2, 3
        double n = 4;
        double sumX = 6; // 0 + 1 + 2 + 3
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 14; // 0 + 1 + 4 + 9

        for (int i = 0; i < 4; i++) {
            sumY += semanales[i];
            sumXY += i * semanales[i];
        }

        double denominador = (n * sumXX - sumX * sumX);
        double m = (denominador == 0) ? 0 : (n * sumXY - sumX * sumY) / denominador;
        double c = (sumY - m * sumX) / n;

        // Proyectar siguientes 4 semanas (x: 4, 5, 6, 7)
        List<Map<String, Object>> proyeccionList = new ArrayList<>();
        double totalProyectadoMes = 0;
        for (int i = 0; i < 4; i++) {
            int x = 4 + i;
            double proyectado = m * x + c;
            if (proyectado < 0) proyectado = 0; // Prevenir proyecciones negativas
            totalProyectadoMes += proyectado;

            Map<String, Object> pMap = new HashMap<>();
            pMap.put("nombre", "Semana +" + (i + 1));
            pMap.put("total", Math.round(proyectado * 100.0) / 100.0);
            proyeccionList.add(pMap);
        }

        // Tasa de crecimiento promedio
        double crecimiento = 0.0;
        if (semanales[0] > 0) {
            crecimiento = ((semanales[3] - semanales[0]) / semanales[0]) * 100.0;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("historico", historicoList);
        result.put("proyeccion", proyeccionList);
        result.put("crecimientoPromedioPorcentaje", Math.round(crecimiento * 10.0) / 10.0);
        result.put("estimadoMesProximo", Math.round(totalProyectadoMes * 100.0) / 100.0);

        return ResponseEntity.ok(result);
    }

    // ── 5. Rendimiento de Técnicos (Alias para consistencia) ────────────────────
    @GetMapping("/rendimiento-tecnicos")
    public ResponseEntity<List<Map<String, Object>>> getRendimientoTecnicos() {
        return getTecnicosPerformance();
    }

    // ── 6. Flujo de Caja (Cash Flow) – Últimos 30 días + Proyección ────────────
    @GetMapping("/flujo-caja")
    public ResponseEntity<Map<String, Object>> getFlujoCaja() {
        LocalDateTime desde = LocalDateTime.now().minusDays(30);
        LocalDateTime hasta = LocalDateTime.now();

        // Obtener ingresos y egresos diarios desde movimientos_caja
        List<Object[]> movimientosData = em.createQuery(
                "SELECT FUNCTION('DATE', mc.fecha), mc.tipo, SUM(mc.monto) " +
                "FROM MovimientoCaja mc " +
                "WHERE mc.fecha >= :desde AND mc.fecha <= :hasta " +
                "GROUP BY FUNCTION('DATE', mc.fecha), mc.tipo " +
                "ORDER BY FUNCTION('DATE', mc.fecha) DESC", Object[].class)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();

        // Agrupar por día
        Map<String, Map<String, Object>> diarios = new HashMap<>();
        for (Object[] row : movimientosData) {
            java.time.LocalDate fecha = (java.time.LocalDate) row[0];
            String tipo = (String) row[1];
            BigDecimal monto = row[2] != null ? BigDecimal.valueOf(((Number) row[2]).doubleValue()) : BigDecimal.ZERO;

            String key = fecha.toString();
            diarios.putIfAbsent(key, new HashMap<>());
            Map<String, Object> dayData = diarios.get(key);

            if (tipo.matches("INGRESO|PAGO_VENTA|PAGO_ORDEN")) {
                BigDecimal ingresos = (BigDecimal) dayData.getOrDefault("ingresos", BigDecimal.ZERO);
                dayData.put("ingresos", ingresos.add(monto));
            } else if (tipo.matches("EGRESO|COMPRA|DEVOLUCION")) {
                BigDecimal egresos = (BigDecimal) dayData.getOrDefault("egresos", BigDecimal.ZERO);
                dayData.put("egresos", egresos.add(monto));
            }

            dayData.put("fecha", fecha);
        }

        // Calcular netos y acumulados
        List<Map<String, Object>> flujoDiario = new ArrayList<>();
        BigDecimal saldoAcumulado = BigDecimal.ZERO;
        List<String> fechasOrdenadas = new ArrayList<>(diarios.keySet());
        Collections.sort(fechasOrdenadas);

        for (String fecha : fechasOrdenadas) {
            Map<String, Object> data = diarios.get(fecha);
            BigDecimal ing = (BigDecimal) data.getOrDefault("ingresos", BigDecimal.ZERO);
            BigDecimal egr = (BigDecimal) data.getOrDefault("egresos", BigDecimal.ZERO);
            BigDecimal neto = ing.subtract(egr);
            saldoAcumulado = saldoAcumulado.add(neto);

            Map<String, Object> dayMap = new HashMap<>();
            dayMap.put("fecha", data.get("fecha"));
            dayMap.put("ingresos", ing.setScale(2, RoundingMode.HALF_UP));
            dayMap.put("egresos", egr.setScale(2, RoundingMode.HALF_UP));
            dayMap.put("neto", neto.setScale(2, RoundingMode.HALF_UP));
            dayMap.put("saldoAcumulado", saldoAcumulado.setScale(2, RoundingMode.HALF_UP));
            flujoDiario.add(dayMap);
        }

        // Calcular totales
        BigDecimal totalIngresos = flujoDiario.stream()
                .map(m -> (BigDecimal) m.get("ingresos"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEgresos = flujoDiario.stream()
                .map(m -> (BigDecimal) m.get("egresos"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal neto = totalIngresos.subtract(totalEgresos);

        // Obtener saldo actual de cajas abiertas
        BigDecimal saldoActual = calcularSaldoActualCajasAbiertas().setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> result = new HashMap<>();
        result.put("periodo", "Últimos 30 días");
        result.put("desde", desde.toLocalDate());
        result.put("hasta", hasta.toLocalDate());
        result.put("flujoDiario", flujoDiario);
        result.put("totalIngresos", totalIngresos.setScale(2, RoundingMode.HALF_UP));
        result.put("totalEgresos", totalEgresos.setScale(2, RoundingMode.HALF_UP));
        result.put("netoTotal", neto.setScale(2, RoundingMode.HALF_UP));
        result.put("saldoActual", saldoActual);

        return ResponseEntity.ok(result);
    }

    // ── 7. Productos sin movimiento (no vendidos en X días) ────────────────────
    private BigDecimal calcularSaldoActualCajasAbiertas() {
        List<Caja> cajasAbiertas = em.createQuery(
                "SELECT c FROM Caja c WHERE c.estado = 'ABIERTA'", Caja.class)
                .getResultList();

        BigDecimal saldo = BigDecimal.ZERO;
        for (Caja caja : cajasAbiertas) {
            saldo = saldo.add(caja.getMontoInicial() != null ? caja.getMontoInicial() : BigDecimal.ZERO);

            List<Object[]> movimientos = em.createQuery(
                    "SELECT mc.tipo, SUM(mc.monto) " +
                    "FROM MovimientoCaja mc " +
                    "WHERE mc.caja.id = :cajaId " +
                    "GROUP BY mc.tipo", Object[].class)
                    .setParameter("cajaId", caja.getId())
                    .getResultList();

            for (Object[] row : movimientos) {
                String tipo = (String) row[0];
                BigDecimal monto = row[1] != null
                        ? BigDecimal.valueOf(((Number) row[1]).doubleValue())
                        : BigDecimal.ZERO;

                if (tipo.matches("INGRESO|PAGO_VENTA|PAGO_ORDEN")) {
                    saldo = saldo.add(monto);
                } else if (tipo.matches("EGRESO|COMPRA|DEVOLUCION")) {
                    saldo = saldo.subtract(monto);
                }
            }
        }

        return saldo;
    }

    @GetMapping("/sin-movimiento")
    public ResponseEntity<List<Map<String, Object>>> getProductosSinMovimiento(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "30") int dias) {

        LocalDateTime desde = LocalDateTime.now().minusDays(dias);

        // Obtener productos activos
        List<Producto> productos = em.createQuery(
                "SELECT p FROM Producto p WHERE p.activo = true", Producto.class)
                .getResultList();

        // Productos que SÍ tuvieron movimiento en el período
        Set<UUID> conMovimiento = new HashSet<>();

        List<UUID> conVenta = em.createQuery(
                "SELECT DISTINCT vi.producto.id FROM VentaItem vi " +
                "WHERE vi.venta.fecha >= :desde", UUID.class)
                .setParameter("desde", desde)
                .getResultList();
        conMovimiento.addAll(conVenta);

        List<UUID> conEntrega = em.createQuery(
                "SELECT DISTINCT ei.producto.id FROM EntregaItem ei " +
                "WHERE ei.entrega.fechaEntrega >= :desde", UUID.class)
                .setParameter("desde", desde)
                .getResultList();
        conMovimiento.addAll(conEntrega);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Producto prod : productos) {
            if (!conMovimiento.contains(prod.getId())) {
                // Obtener última venta
                List<LocalDateTime> ultimaVenta = em.createQuery(
                        "SELECT MAX(v.fecha) FROM VentaItem vi " +
                        "JOIN vi.venta v WHERE vi.producto.id = :prodId", LocalDateTime.class)
                        .setParameter("prodId", prod.getId())
                        .setMaxResults(1)
                        .getResultList();

                LocalDateTime ultimaFecha = ultimaVenta.isEmpty() || ultimaVenta.get(0) == null
                        ? null
                        : ultimaVenta.get(0);

                Map<String, Object> map = new HashMap<>();
                map.put("id", prod.getId());
                map.put("nombre", prod.getNombre());
                map.put("codigo", prod.getCodigo());
                map.put("stock", prod.getStock());
                map.put("categoria", prod.getCategoria() != null ? prod.getCategoria().getNombre() : "Sin categoría");
                map.put("ultimaVenta", ultimaFecha);
                map.put("diasSinMovimiento", ultimaFecha == null ? "Sin historial" : 
                    Duration.between(ultimaFecha, LocalDateTime.now()).toDays() + " días");
                result.add(map);
            }
        }

        // Ordenar por últimaFecha (nulos primero)
        result.sort((a, b) -> {
            LocalDateTime fA = (LocalDateTime) a.get("ultimaVenta");
            LocalDateTime fB = (LocalDateTime) b.get("ultimaVenta");
            if (fA == null && fB == null) return 0;
            if (fA == null) return -1;
            if (fB == null) return 1;
            return fA.compareTo(fB);
        });

        return ResponseEntity.ok(result);
    }
}
