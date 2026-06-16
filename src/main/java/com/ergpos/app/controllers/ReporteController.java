package com.ergpos.app.controllers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.MovimientoInventario;
import com.ergpos.app.model.Venta;
import com.ergpos.app.repositories.EntregaRepository;
import com.ergpos.app.repositories.MovimientoInventarioRepository;
import com.ergpos.app.repositories.ProductoRepository;
import com.ergpos.app.repositories.UsuarioRepository;
import com.ergpos.app.repositories.VentaRepository;
import com.ergpos.app.repositories.CompraRepository;
import com.ergpos.app.repositories.DevolucionGarantiaRepository;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final VentaRepository ventaRepository;
    private final EntregaRepository entregaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final CompraRepository compraRepository;
    private final DevolucionGarantiaRepository devolucionGarantiaRepository;

    public ReporteController(VentaRepository ventaRepository,
                             EntregaRepository entregaRepository,
                             ProductoRepository productoRepository,
                             UsuarioRepository usuarioRepository,
                             MovimientoInventarioRepository movimientoRepository,
                             CompraRepository compraRepository,
                             DevolucionGarantiaRepository devolucionGarantiaRepository) {
        this.ventaRepository = ventaRepository;
        this.entregaRepository = entregaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.compraRepository = compraRepository;
        this.devolucionGarantiaRepository = devolucionGarantiaRepository;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {
        Map<String, Object> data = new HashMap<>();

        LocalDateTime hoyInicio = LocalDate.now().atStartOfDay();
        LocalDateTime hoyFin = hoyInicio.plusDays(1);
        LocalDateTime mesInicio = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // Ventas
        long totalVentas = ventaRepository.count();
        long ventasCompletadas = ventaRepository.countByEstado("COMPLETADA");
        Double ingresoTotal = ventaRepository.sumTotalCompletadas();
        Double ingresosHoy = ventaRepository.sumTotalCompletadasEnRango(hoyInicio, hoyFin);
        Double ingresosMes = ventaRepository.sumTotalCompletadasEnRango(mesInicio, hoyFin);

        // Cambio 10 — estados corregidos para coincidir con el modelo de Entrega
        long entregasPendientes   = entregaRepository.countByEstado("PENDIENTE");
        long entregasEnCamino     = entregaRepository.countByEstado("EN_PROCESO");
        long entregasCompletadas  = entregaRepository.countByEstado("COMPLETADO");
        long entregasFinalizadas  = entregaRepository.countByEstado("FINALIZADO");

        // Inventario
        long totalProductos = productoRepository.count();
        long productosActivos = productoRepository.findByActivoTrue().size();
        long stockBajo = productoRepository.findProductosConStockBajo().size();

        // Usuarios
        long totalUsuarios = usuarioRepository.count();
        long usuariosActivos = usuarioRepository.findByActivoTrue().size();

        // Últimas ventas
        List<Venta> ultimasVentas = ventaRepository.findTop10ByOrderByFechaDesc();

        // Últimos movimientos
        List<MovimientoInventario> ultimosMovimientos = movimientoRepository.findTop10ByOrderByFechaDesc();

        // Ventas últimos 7 días (para gráfico)
        List<Map<String, Object>> ventasPorDia = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime diaInicio = LocalDate.now().minusDays(i).atStartOfDay();
            LocalDateTime diaFin = diaInicio.plusDays(1);
            Double totalDia = ventaRepository.sumTotalCompletadasEnRango(diaInicio, diaFin);
            long countDia = ventaRepository.findByFechaBetween(diaInicio, diaFin).size();
            Map<String, Object> dia = new HashMap<>();
            dia.put("fecha", LocalDate.now().minusDays(i).toString());
            dia.put("total", totalDia != null ? totalDia : 0.0);
            dia.put("count", countDia);
            ventasPorDia.add(dia);
        }

        // Cómputo de Utilidad Neta (P&L)
        Double totalVentasPOS = (ingresoTotal != null) ? ingresoTotal : 0.0;
        Double totalIngresosServicios = entregaRepository.sumTotalFinalizadas();
        totalIngresosServicios = (totalIngresosServicios != null) ? totalIngresosServicios : 0.0;
        Double totalDevoluciones = devolucionGarantiaRepository.sumTotalDevuelto();
        totalDevoluciones = (totalDevoluciones != null) ? totalDevoluciones : 0.0;
        Double totalCompras = compraRepository.sumTotalConfirmadas();
        totalCompras = (totalCompras != null) ? totalCompras : 0.0;

        Double utilidadNetaTotal = (totalVentasPOS + totalIngresosServicios) - totalDevoluciones - totalCompras;

        // Rango de hoy
        Double ingresosHoyVentas = (ingresosHoy != null) ? ingresosHoy : 0.0;
        Double ingresosHoyServicios = entregaRepository.sumTotalFinalizadasEnRango(hoyInicio, hoyFin);
        ingresosHoyServicios = (ingresosHoyServicios != null) ? ingresosHoyServicios : 0.0;
        Double devolucionesHoy = devolucionGarantiaRepository.sumTotalDevueltoEnRango(hoyInicio, hoyFin);
        devolucionesHoy = (devolucionesHoy != null) ? devolucionesHoy : 0.0;
        Double comprasHoy = compraRepository.sumTotalConfirmadasEnRango(hoyInicio, hoyFin);
        comprasHoy = (comprasHoy != null) ? comprasHoy : 0.0;

        Double utilidadNetaHoy = (ingresosHoyVentas + ingresosHoyServicios) - devolucionesHoy - comprasHoy;

        // Rango de mes
        Double ingresosMesVentas = (ingresosMes != null) ? ingresosMes : 0.0;
        Double ingresosMesServicios = entregaRepository.sumTotalFinalizadasEnRango(mesInicio, hoyFin);
        ingresosMesServicios = (ingresosMesServicios != null) ? ingresosMesServicios : 0.0;
        Double devolucionesMes = devolucionGarantiaRepository.sumTotalDevueltoEnRango(mesInicio, hoyFin);
        devolucionesMes = (devolucionesMes != null) ? devolucionesMes : 0.0;
        Double comprasMes = compraRepository.sumTotalConfirmadasEnRango(mesInicio, hoyFin);
        comprasMes = (comprasMes != null) ? comprasMes : 0.0;

        Double utilidadNetaMes = (ingresosMesVentas + ingresosMesServicios) - devolucionesMes - comprasMes;

        // Obtener productos más vendidos (Top 5)
        List<Object[]> masVendidosRaw = ventaRepository.findProductosMasVendidos();
        List<Map<String, Object>> productosMasVendidos = new ArrayList<>();
        int count = 0;
        for (Object[] row : masVendidosRaw) {
            if (count >= 5) break;
            Map<String, Object> prod = new HashMap<>();
            prod.put("producto", row[0]);
            prod.put("cantidad", row[1]);
            productosMasVendidos.add(prod);
            count++;
        }

        data.put("ventas", Map.of(
            "total",        totalVentas,
            "completadas",  ventasCompletadas,
            "ingresoTotal", totalVentasPOS,
            "ingresosHoy",  ingresosHoyVentas,
            "ingresosMes",  ingresosMesVentas
        ));

        Map<String, Object> finanzasMap = new HashMap<>();
        finanzasMap.put("utilidadNetaTotal",    utilidadNetaTotal);
        finanzasMap.put("utilidadNetaHoy",      utilidadNetaHoy);
        finanzasMap.put("utilidadNetaMes",      utilidadNetaMes);
        finanzasMap.put("totalVentasPOS",       totalVentasPOS);
        finanzasMap.put("totalIngresosServicios", totalIngresosServicios);
        finanzasMap.put("totalDevoluciones",    totalDevoluciones);
        finanzasMap.put("totalCompras",         totalCompras);
        finanzasMap.put("ingresosMesVentas",    ingresosMesVentas);
        finanzasMap.put("ingresosMesServicios", ingresosMesServicios);
        finanzasMap.put("devolucionesMes",      devolucionesMes);
        finanzasMap.put("comprasMes",           comprasMes);
        data.put("finanzas", finanzasMap);


        // Cambio 11 — claves corregidas y "finalizadas" agregado
        data.put("entregas", Map.of(
            "pendientes",   entregasPendientes,
            "enProceso",    entregasEnCamino,
            "completadas",  entregasCompletadas,
            "finalizadas",  entregasFinalizadas
        ));

        data.put("inventario", Map.of(
            "total",    totalProductos,
            "activos",  productosActivos,
            "stockBajo", stockBajo
        ));

        data.put("usuarios", Map.of(
            "total",   totalUsuarios,
            "activos", usuariosActivos
        ));

        data.put("ultimasVentas", ultimasVentas);
        data.put("ultimosMovimientos", ultimosMovimientos);
        data.put("ventasPorDia", ventasPorDia);
        data.put("productosMasVendidos", productosMasVendidos);

        return data;
    }

    /**
     * GET /api/reportes/ventas-por-vendedor
     * Retorna resumen de ventas segmentadas por vendedor
     */
    @GetMapping("/ventas-por-vendedor")
    public List<Map<String, Object>> getVentasPorVendedor() {
        List<Map<String, Object>> vendedoresData = ventaRepository.findAll().stream()
            .filter(v -> v.getVendedor() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                Venta::getVendedor,
                java.util.stream.Collectors.toList()
            ))
            .entrySet()
            .stream()
            .map(entry -> {
                var vendedor = entry.getKey();
                var ventas = entry.getValue();
                long total = ventas.size();
                long completadas = ventas.stream().filter(v -> "COMPLETADA".equals(v.getEstado())).count();
                Double ingreso = ventas.stream()
                    .filter(v -> "COMPLETADA".equals(v.getEstado()))
                    .map(Venta::getTotal)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .doubleValue();
                
                Map<String, Object> map = new HashMap<>();
                map.put("vendedorId", vendedor.getId());
                map.put("vendedor", vendedor.getNombre());
                map.put("email", vendedor.getEmail());
                map.put("totalVentas", total);
                map.put("ventasCompletadas", completadas);
                map.put("ingresoTotal", ingreso);
                map.put("tasaConversion", total > 0 ? Math.round((completadas * 100.0 / total) * 10.0) / 10.0 : 0.0);
                return map;
            })
            .sorted((a, b) -> Double.compare((Double) b.get("ingresoTotal"), (Double) a.get("ingresoTotal")))
            .collect(java.util.stream.Collectors.toList());

        return vendedoresData;
    }

    /**
     * GET /api/reportes/rentabilidad-categoria
     * Retorna análisis de rentabilidad por categoría de producto
     */
    @GetMapping("/rentabilidad-categoria")
    public List<Map<String, Object>> getRentabilidadCategoria() {
        List<Map<String, Object>> categoriasData = productoRepository.findAll().stream()
            .filter(p -> p.getCategoria() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                p -> p.getCategoria(),
                java.util.stream.Collectors.toList()
            ))
            .entrySet()
            .stream()
            .map(entry -> {
                var categoria = entry.getKey();
                var productos = entry.getValue();
                
                // Contar items vendidos en esta categoría
                long itemsVendidos = 0;
                long cantidadTotal = 0;
                Double ingresoTotal = 0.0;
                
                for (var p : productos) {
                    var ventasItems = ventaRepository.findAll().stream()
                        .flatMap(v -> v.getItems().stream())
                        .filter(vi -> vi.getProducto().getId().equals(p.getId()))
                        .filter(vi -> "COMPLETADA".equals(vi.getVenta().getEstado()))
                        .collect(java.util.stream.Collectors.toList());
                    
                    itemsVendidos += ventasItems.size();
                    cantidadTotal += ventasItems.stream().mapToLong(vi -> vi.getCantidad()).sum();
                    ingresoTotal += ventasItems.stream()
                        .map(vi -> vi.getSubtotal())
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .doubleValue();
                }

                Double margenPromedio = 40.0; // Margen estimado
                Double costoEstimado = ingresoTotal * (1.0 - margenPromedio / 100.0);
                Double gananciaEstimada = ingresoTotal * (margenPromedio / 100.0);

                Map<String, Object> map = new HashMap<>();
                map.put("categoriaId", categoria.getId());
                map.put("categoria", categoria.getNombre());
                map.put("productosActivos", productos.size());
                map.put("itemsVendidos", itemsVendidos);
                map.put("cantidadTotal", cantidadTotal);
                map.put("ingresoTotal", Math.round(ingresoTotal * 100.0) / 100.0);
                map.put("costoEstimado", Math.round(costoEstimado * 100.0) / 100.0);
                map.put("gananciaEstimada", Math.round(gananciaEstimada * 100.0) / 100.0);
                map.put("margenPorcentaje", margenPromedio);
                
                return map;
            })
            .sorted((a, b) -> Double.compare((Double) b.get("ingresoTotal"), (Double) a.get("ingresoTotal")))
            .collect(java.util.stream.Collectors.toList());

        return categoriasData;
    }
}
