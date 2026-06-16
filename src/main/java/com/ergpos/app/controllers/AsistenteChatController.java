package com.ergpos.app.controllers;

import com.ergpos.app.model.Auditoria;
import com.ergpos.app.model.Cliente;
import com.ergpos.app.model.DevolucionGarantia;
import com.ergpos.app.model.MovimientoInventario;
import com.ergpos.app.model.Producto;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.model.Venta;
import com.ergpos.app.repositories.*;
import com.ergpos.app.services.GroqService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AsistenteChatController — Motor híbrido del Asistente POS-AI.
 *
 * Flujo:
 *   1. Intenta resolver la pregunta con el motor interno (stock, ventas, órdenes…).
 *   2. Si no reconoce la intención → construye un contexto del negocio y llama a Groq.
 *   3. Si Groq no está configurado → devuelve mensaje de orientación.
 *
 * Endpoint: POST /api/asistente/chat
 * Body:     { "pregunta": "¿Cuánto stock queda de araña?" }
 * Response: { "respuesta": "...", "fuente": "INTERNO" | "GROQ" | "FALLBACK" }
 */
@RestController
@RequestMapping("/api/asistente")
public class AsistenteChatController {

    private final ProductoRepository   productoRepository;
    private final VentaRepository      ventaRepository;
    private final EntregaRepository    entregaRepository;
    private final CompraRepository     compraRepository;
    private final DevolucionGarantiaRepository devolucionRepo;
    private final ClienteRepository    clienteRepository;
    private final UsuarioRepository    usuarioRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final AuditoriaRepository  auditoriaRepository;
    private final GroqService          groqService;

    public AsistenteChatController(
            ProductoRepository productoRepository,
            VentaRepository ventaRepository,
            EntregaRepository entregaRepository,
            CompraRepository compraRepository,
            DevolucionGarantiaRepository devolucionRepo,
            ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository,
            MovimientoInventarioRepository movimientoInventarioRepository,
            AuditoriaRepository auditoriaRepository,
            GroqService groqService) {
        this.productoRepository = productoRepository;
        this.ventaRepository    = ventaRepository;
        this.entregaRepository  = entregaRepository;
        this.compraRepository   = compraRepository;
        this.devolucionRepo     = devolucionRepo;
        this.clienteRepository  = clienteRepository;
        this.usuarioRepository  = usuarioRepository;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.groqService        = groqService;
    }

    // ── Endpoint principal ──────────────────────────────────────────────────

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> body) {

        String pregunta = (body.getOrDefault("pregunta", "")).trim();
        if (pregunta.isBlank()) {
            return ResponseEntity.ok(Map.of(
                "respuesta", "Por favor escribe una pregunta.",
                "fuente",    "INTERNO"
            ));
        }

        try {
            String respuestaInterna = resolverInternamente(pregunta);
            if (respuestaInterna != null) {
                return ResponseEntity.ok(Map.of(
                    "respuesta", respuestaInterna,
                    "fuente",    "INTERNO"
                ));
            }

            String contexto   = construirContextoNegocio();
            String respuesta  = groqService.preguntar(SYSTEM_PROMPT + "\n\n" + contexto, pregunta);
            return ResponseEntity.ok(Map.of(
                "respuesta", respuesta,
                "fuente",    "GROQ"
            ));
        } catch (GroqService.GroqNoConfiguredException e) {
            return ResponseEntity.ok(Map.of(
                "respuesta", "La IA no está configurada. Configura la API key de Groq para consultar clientes, usuarios, inventario, ventas y finanzas desde el asistente.",
                "fuente",    "FALLBACK"
            ));
        } catch (GroqService.GroqException e) {
            return ResponseEntity.ok(Map.of(
                "respuesta", "⚠️ " + e.getMessage(),
                "fuente",    "ERROR"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of(
                "respuesta", "No pude preparar los datos internos para responder. Revisa los logs del backend: " + e.getMessage(),
                "fuente",    "ERROR"
            ));
        }
    }

    /** Devuelve si la integración con Groq está activa. */
    @GetMapping("/estado")
    public ResponseEntity<Map<String, Object>> estado() {
        return ResponseEntity.ok(Map.of(
            "groqActivo", groqService.estaConfigurado()
        ));
    }

    // ── Motor Interno de Intenciones ────────────────────────────────────────

    private String resolverInternamente(String p) {
        String q = normalizar(p);

        if (esConsultaGeneral(q)) {
            return null;
        }

        Optional<RangoFechas> rango = extraerRangoFechas(q);
        if (rango.isPresent() && matches(q, "ventas", "vendimos", "facturado", "ingresos")) {
            RangoFechas r = rango.get();
            LocalDateTime inicio = r.desde().atStartOfDay();
            LocalDateTime fin = r.hasta().plusDays(1).atStartOfDay();
            long count = ventaRepository.countCompletadasEnRango(inicio, fin);
            Double total = ventaRepository.sumTotalCompletadasEnRango(inicio, fin);
            return "💰 **Ventas del %s al %s:**\n• Ventas completadas: **%d**\n• Total facturado: **$%s COP**"
                    .formatted(
                            r.desde().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            r.hasta().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            count,
                            formatCOP(total)
                    );
        }

        // ── STOCK ─────────────────────────────────────────────────────────────
        if (matches(q, "stock", "inventario", "cuánto queda", "cuanto queda",
                    "hay stock", "disponible", "unidades")) {
            String keyword = extraerKeyword(q,
                "stock", "inventario", "cuánto queda", "cuanto queda", "hay stock", "disponible",
                "unidades", "que", "qué", "hay", "en", "de", "del", "el", "la", "los", "las");
            if (!keyword.isBlank()) {
                List<Producto> encontrados = buscarProductos(keyword);
                if (!encontrados.isEmpty()) {
                    if (encontrados.size() == 1) {
                        Producto prod = encontrados.get(0);
                        String estado = prod.getStock() <= 0 ? "⚠️ AGOTADO"
                                      : prod.getStock() <= prod.getStockMinimo() ? "🟠 STOCK BAJO"
                                      : "✅ OK";
                        return "📦 **%s** (%s)\nStock actual: **%d unidades** %s\nStock mínimo: %d uds"
                                .formatted(prod.getNombre(), prod.getCodigo(), prod.getStock(), estado, prod.getStockMinimo());
                    } else {
                        StringBuilder sb = new StringBuilder("📦 Encontré **%d productos** que coinciden con \"%s\":\n".formatted(encontrados.size(), keyword));
                        encontrados.stream().limit(5).forEach(prod -> {
                            String est = prod.getStock() <= 0 ? "⚠️ AGOTADO"
                                       : prod.getStock() <= prod.getStockMinimo() ? "🟠 BAJO" : "✅ OK";
                            sb.append("• **%s** — %d uds %s\n".formatted(prod.getNombre(), prod.getStock(), est));
                        });
                        return sb.toString().trim();
                    }
                }
                return "🔍 No encontré ningún producto que coincida con \"" + keyword + "\". "
                        + sugerenciasProductos();
            }
            // Sin keyword → mostrar alertas de stock bajo
            return stockBajoResumen();
        }

        // ── ALERTAS / STOCK BAJO ─────────────────────────────────────────────
        if (matches(q, "alerta", "stock bajo", "agotado", "sin stock", "reponer", "comprar")) {
            return stockBajoResumen();
        }

        if (matches(q, "devoluciones", "devolucion", "devolución", "garantias", "garantías")
                && matches(q, "clientes", "cliente", "hay", "quienes", "quiénes")) {
            return clientesConDevolucionesResumen();
        }

        if (matches(q, "clientes", "cliente", "que clientes", "qué clientes", "lista de clientes")) {
            return clientesResumen();
        }

        if (matches(q, "usuarios", "usuario", "que usuarios", "qué usuarios", "lista de usuarios", "empleados")) {
            return usuariosResumen();
        }

        // ── VENTAS SEMANA ─────────────────────────────────────────────────────
        if (matches(q, "ventas", "vendimos", "facturado", "ingresos")
                && matches(q, "semana", "esta semana", "semanal")) {
            LocalDate hoy = LocalDate.now();
            LocalDate inicioSemana = hoy.with(DayOfWeek.MONDAY);
            LocalDateTime inicio = inicioSemana.atStartOfDay();
            LocalDateTime fin = hoy.plusDays(1).atStartOfDay();
            long count = ventaRepository.countCompletadasEnRango(inicio, fin);
            Double total = ventaRepository.sumTotalCompletadasEnRango(inicio, fin);
            return "💰 **Ventas de esta semana (%s - %s):**\n• Ventas completadas: **%d**\n• Total facturado: **$%s COP**"
                    .formatted(inicioSemana.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                               hoy.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                               count, formatCOP(total));
        }

        // ── VENTAS HOY ────────────────────────────────────────────────────────
        if (matches(q, "ventas hoy", "ventas de hoy", "ventas del dia", "ventas dia",
                    "vendimos hoy", "vendimos dia", "facturado hoy", "facturado dia",
                    "ingresos hoy", "ingresos dia", "como vamos hoy", "hoy vendimos")
                || (matches(q, "ventas", "vendimos", "facturado", "ingresos") && matches(q, "hoy", "dia"))) {
            LocalDateTime inicio = LocalDate.now().atStartOfDay();
            LocalDateTime fin    = inicio.plusDays(1);
            long   count = ventaRepository.countCompletadasEnRango(inicio, fin);
            Double total = ventaRepository.sumTotalCompletadasEnRango(inicio, fin);
            return "💰 **Ventas de hoy (%s):**\n• Ventas completadas: **%d**\n• Total facturado: **$%s COP**"
                    .formatted(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                               count, formatCOP(total));
        }

        // ── VENTAS MES ────────────────────────────────────────────────────────
        if (matches(q, "ventas mes", "este mes", "mes actual", "ingresos mes", "facturado mes")) {
            LocalDateTime inicio = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            LocalDateTime fin    = LocalDate.now().plusDays(1).atStartOfDay();
            Double totalPos      = ventaRepository.sumTotalCompletadasEnRango(inicio, fin);
            Double totalServ     = entregaRepository.sumTotalFinalizadasEnRango(inicio, fin);
            Double totalCompras  = compraRepository.sumTotalConfirmadasEnRango(inicio, fin);
            Double utilidad      = safe(totalPos) + safe(totalServ) - safe(totalCompras);
            return "📅 **Resumen del mes:**\n• Ventas POS: **$%s COP**\n• Servicios (M.O.): **$%s COP**\n• Compras: **−$%s COP**\n• **Utilidad neta: $%s COP**"
                    .formatted(formatCOP(totalPos), formatCOP(totalServ), formatCOP(totalCompras), formatCOP(utilidad));
        }

        // ── UTILIDAD / FINANZAS ───────────────────────────────────────────────
        if (matches(q, "utilidad", "ganancias", "finanzas", "p&l", "perdidas", "pérdidas",
                    "rendimiento", "estado financiero", "resumen financiero")) {
            Double totalPos      = ventaRepository.sumTotalCompletadas();
            Double totalServ     = entregaRepository.sumTotalFinalizadas();
            Double totalCompras  = compraRepository.sumTotalConfirmadas();
            Double totalDev      = devolucionRepo.sumTotalDevuelto();
            Double utilidad      = safe(totalPos) + safe(totalServ) - safe(totalCompras) - safe(totalDev);
            return "💹 **Estado Financiero General:**\n• Ventas POS: **$%s COP**\n• Mano de obra (servicios): **$%s COP**\n• Compras a proveedores: **−$%s COP**\n• Devoluciones: **−$%s COP**\n────────────────\n• **Utilidad neta total: $%s COP**"
                    .formatted(formatCOP(totalPos), formatCOP(totalServ), formatCOP(totalCompras), formatCOP(totalDev), formatCOP(utilidad));
        }

        // ── ÓRDENES PENDIENTES ────────────────────────────────────────────────
        if (matches(q, "órdenes pendientes", "ordenes pendientes", "ordenes activas",
                    "ordenes sin confirmar", "qué órdenes", "que ordenes", "servicios pendientes")) {
            long pendientes    = entregaRepository.countByEstado("PENDIENTE");
            long enProceso     = entregaRepository.countByEstado("EN_PROCESO");
            long completadas   = entregaRepository.countByEstado("COMPLETADO");
            long sinConfirmar  = completadas; // COMPLETADO = esperando confirmación del admin
            return "🔧 **Estado de Órdenes de Servicio:**\n• Pendientes (sin técnico): **%d**\n• En proceso: **%d**\n• Completadas (sin confirmar): **%d** ⚠️\n\nTotal activas: **%d órdenes**"
                    .formatted(pendientes, enProceso, sinConfirmar, pendientes + enProceso + completadas);
        }

        // ── PRODUCTOS MÁS VENDIDOS ────────────────────────────────────────────
        if (matches(q, "más vendidos", "mas vendidos", "top productos", "productos populares",
                    "mejor vendido", "qué se vende más", "que se vende mas")) {
            List<Object[]> raw = ventaRepository.findProductosMasVendidos();
            if (raw.isEmpty()) return "📊 No hay datos de ventas aún.";
            StringBuilder sb = new StringBuilder("🏆 **Top 5 productos más vendidos:**\n");
            raw.stream().limit(5).forEach(row -> {
                sb.append("• **%s** — %s uds vendidas\n".formatted(row[0], row[1]));
            });
            return sb.toString().trim();
        }

        // ── AYUDA / GUÍA ──────────────────────────────────────────────────────
        if (matches(q, "ayuda", "help", "cómo", "como", "qué puedes", "que puedes",
                    "qué haces", "que haces", "comandos", "funciones")) {
            return """
                    🤖 **Asistente POS-AI — Comandos disponibles:**

                    📦 **Inventario:**
                    • `stock [producto]` — Stock actual de un producto
                    • `alertas` — Productos con stock bajo o agotados

                    💰 **Ventas:**
                    • `ventas hoy` — Resumen del día
                    • `ventas mes` — Resumen del mes actual
                    • `más vendidos` — Top 5 productos más vendidos

                    💹 **Finanzas:**
                    • `utilidad` — Estado financiero general (P&L)

                    🔧 **Órdenes de Servicio:**
                    • `órdenes pendientes` — Estado de las órdenes activas

                    💬 Para preguntas más complejas, la IA avanzada (Groq) responderá automáticamente.""";
        }

        return null; // No reconocido → fallback a Groq
    }

    private boolean esConsultaGeneral(String q) {
        return matches(q, "resumen", "estado actual", "estado del negocio", "dashboard", "panorama", "reporte")
                && matches(q, "clientes", "usuarios", "inventario", "ventas", "finanzas", "negocio");
    }

    // ── Construcción del contexto de negocio para Groq ──────────────────────

    private String construirContextoNegocio() {
        LocalDateTime hoyInicio = LocalDate.now().atStartOfDay();
        LocalDateTime hoyFin    = hoyInicio.plusDays(1);
        LocalDateTime mesInicio = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        long ventasHoy        = ventaRepository.countCompletadasEnRango(hoyInicio, hoyFin);
        Double ingresoHoy     = ventaRepository.sumTotalCompletadasEnRango(hoyInicio, hoyFin);
        Double ingresoMes     = ventaRepository.sumTotalCompletadasEnRango(mesInicio, hoyFin);
        Double totalServMes   = entregaRepository.sumTotalFinalizadasEnRango(mesInicio, hoyFin);
        Double totalComprasMes= compraRepository.sumTotalConfirmadasEnRango(mesInicio, hoyFin);
        Double utilidadMes    = safe(ingresoMes) + safe(totalServMes) - safe(totalComprasMes);

        long ordenesPendientes = entregaRepository.countByEstado("PENDIENTE");
        long ordenesEnProceso  = entregaRepository.countByEstado("EN_PROCESO");
        long ordenesSinConfirmar = entregaRepository.countByEstado("COMPLETADO");

        List<Producto> stockBajo = productoRepository.findProductosConStockBajo();
        String productosStockBajo = stockBajo.stream().limit(5)
                .map(p -> p.getNombre() + " (" + p.getStock() + " uds)")
                .collect(Collectors.joining(", "));

        List<Object[]> masVendidos = ventaRepository.findProductosMasVendidos();
        String topProductos = masVendidos.stream().limit(3)
                .map(r -> r[0] + " (" + r[1] + " uds)")
                .collect(Collectors.joining(", "));

        List<Cliente> clientesActivos = clienteRepository.findByActivoTrue();
        List<Usuario> usuariosActivos = usuarioRepository.findByActivoTrue();
        List<Producto> productosActivos = productoRepository.findByActivoTrue();
        BigDecimal valorInventario = productosActivos.stream()
                .map(Producto::getValorInventario)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String clientes = clientesActivos.stream().limit(25)
                .map(c -> "%s | doc: %s | tel: %s | email: %s"
                        .formatted(c.getNombre(), valor(c.getDocumento()), valor(c.getTelefono()), valor(c.getEmail())))
                .collect(Collectors.joining("\n- "));

        String usuarios = usuariosActivos.stream().limit(25)
                .map(u -> "%s | rol: %s | email: %s | tel: %s"
                        .formatted(u.getNombre(), u.getRol() != null ? u.getRol().getNombre() : "SIN_ROL",
                                valor(u.getEmail()), valor(u.getTelefono())))
                .collect(Collectors.joining("\n- "));

        String productos = productosActivos.stream().limit(40)
                .map(p -> "%s | codigo: %s | stock: %d | minimo: %d | precio: $%s COP"
                        .formatted(p.getNombre(), valor(p.getCodigo()), safeInt(p.getStock()), safeInt(p.getStockMinimo()),
                                p.getPrecio() == null ? "0" : String.format("%,.0f", p.getPrecio())))
                .collect(Collectors.joining("\n- "));

        String rankingClientes = ventaRepository.findClientesConMasCompras().stream().limit(10)
                .map(r -> "%s | tel: %s | compras: %s | total comprado: $%s COP"
                        .formatted(valor(obj(r[0])), valor(obj(r[1])), r[2], formatCOP(toBigDecimal(r[3]))))
                .collect(Collectors.joining("\n- "));

        String ultimasVentas = ventaRepository.findTop10ByOrderByFechaDesc().stream()
                .map(v -> "%s | cliente: %s | total: $%s COP | estado: %s | vendedor: %s | items: %s"
                        .formatted(fecha(v.getFecha()), valor(v.getClienteNombre()), formatCOP(v.getTotal()),
                                valor(v.getEstado()), v.getVendedor() != null ? valor(v.getVendedor().getNombre()) : "N/D",
                                itemsVenta(v)))
                .collect(Collectors.joining("\n- "));

        String movimientosInventario = movimientoInventarioRepository.findTop10ByOrderByFechaDesc().stream()
                .map(m -> "%s | %s %d uds | producto: %s | usuario: %s | origen: %s | nota: %s"
                        .formatted(fecha(m.getFecha()), valor(m.getTipo()), safeInt(m.getCantidad()),
                                m.getProducto() != null ? valor(m.getProducto().getNombre()) : "N/D",
                                m.getUsuario() != null ? valor(m.getUsuario().getNombre()) : "N/D",
                                valor(m.getOrigenTipo()), valor(m.getObservacion())))
                .collect(Collectors.joining("\n- "));

        String auditoriaReciente = auditoriaRepository.findByOrderByFechaDesc(PageRequest.of(0, 15)).stream()
                .map(a -> "%s | usuario: %s | accion: %s | modulo: %s | detalle: %s"
                        .formatted(fecha(a.getFecha()), valor(a.getUsuarioNombre()), valor(a.getAccion()),
                                valor(a.getModulo()), valor(a.getDetalle())))
                .collect(Collectors.joining("\n- "));

        String actividadUsuarios = usuariosActivos.stream().limit(20)
                .map(u -> {
                    long ventasCompletadas = ventaRepository.countByVendedorIdAndEstado(u.getId(), "COMPLETADA");
                    long movimientos = movimientoInventarioRepository.countByUsuarioId(u.getId());
                    long accionesAuditadas = auditoriaRepository.countByUsuarioId(u.getId());
                    String ultimasAcciones = auditoriaRepository
                            .findByUsuarioIdOrderByFechaDesc(u.getId(), PageRequest.of(0, 3))
                            .stream()
                            .map(a -> "%s %s/%s"
                                    .formatted(fecha(a.getFecha()), valor(a.getModulo()), valor(a.getAccion())))
                            .collect(Collectors.joining("; "));

                    return "%s | rol: %s | ventas completadas: %d | movimientos inventario: %d | acciones auditadas: %d | ultimas acciones: %s"
                            .formatted(
                                    valor(u.getNombre()),
                                    u.getRol() != null ? valor(u.getRol().getNombre()) : "SIN_ROL",
                                    ventasCompletadas,
                                    movimientos,
                                    accionesAuditadas,
                                    ultimasAcciones.isBlank() ? "Sin acciones recientes" : ultimasAcciones
                            );
                })
                .collect(Collectors.joining("\n- "));

        return """
                === DATOS EN TIEMPO REAL DEL SISTEMA ERG-POS ===
                Fecha y hora actual: %s
                Acceso del asistente: ADMIN. Puede responder sobre módulos administrativos usando SOLO estos datos.

                VENTAS:
                - Ventas completadas hoy: %d
                - Ingresos por ventas POS hoy: $%s COP
                - Ingresos por ventas POS este mes: $%s COP
                - Ingresos por mano de obra (servicios) este mes: $%s COP
                - Compras a proveedores este mes: $%s COP
                - Utilidad neta estimada del mes: $%s COP

                ÓRDENES DE SERVICIO:
                - Pendientes (sin técnico): %d
                - En proceso: %d
                - Completadas esperando confirmación del admin: %d

                INVENTARIO:
                - Productos activos: %d
                - Valor monetario estimado del stock activo: $%s COP
                - Productos con stock bajo o agotado: %d
                - Listado crítico: %s
                - Productos activos principales:
                - %s

                CLIENTES:
                - Clientes activos: %d
                - Clientes principales:
                - %s

                USUARIOS:
                - Usuarios activos: %d
                - Usuarios principales:
                - %s
                - Actividad por trabajador:
                - %s

                TOP PRODUCTOS MÁS VENDIDOS: %s

                HISTORIAL DE CLIENTES Y VENTAS:
                - Ranking de clientes por compras:
                - %s
                - Últimas ventas registradas:
                - %s

                MOVIMIENTOS DE INVENTARIO:
                - Últimos movimientos:
                - %s

                AUDITORÍA:
                - Últimas acciones del sistema:
                - %s
                === FIN DE DATOS ===
                """.formatted(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                ventasHoy, formatCOP(ingresoHoy), formatCOP(ingresoMes),
                formatCOP(totalServMes), formatCOP(totalComprasMes), formatCOP(utilidadMes),
                ordenesPendientes, ordenesEnProceso, ordenesSinConfirmar,
                productosActivos.size(),
                formatCOP(valorInventario),
                stockBajo.size(),
                productosStockBajo.isBlank() ? "Ninguno" : productosStockBajo,
                productos.isBlank() ? "Sin productos activos" : productos,
                clientesActivos.size(),
                clientes.isBlank() ? "Sin clientes activos" : clientes,
                usuariosActivos.size(),
                usuarios.isBlank() ? "Sin usuarios activos" : usuarios,
                actividadUsuarios.isBlank() ? "Sin actividad registrada por usuario" : actividadUsuarios,
                topProductos.isBlank() ? "Sin datos" : topProductos,
                rankingClientes.isBlank() ? "Sin ventas completadas por cliente" : rankingClientes,
                ultimasVentas.isBlank() ? "Sin ventas recientes" : ultimasVentas,
                movimientosInventario.isBlank() ? "Sin movimientos recientes" : movimientosInventario,
                auditoriaReciente.isBlank() ? "Sin auditoría reciente" : auditoriaReciente
        );
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static final String SYSTEM_PROMPT = """
            Eres el Asistente POS-AI del sistema ERG-POS, un sistema de gestión para una empresa de reparación \
            y venta de sillas y muebles de oficina. \
            Tu alcance es estrictamente interno: SOLO respondes preguntas relacionadas con ERG-POS y sus módulos \
            de clientes, usuarios, roles, productos, inventario, ventas, compras, proveedores, órdenes de servicio, \
            devoluciones, garantías, auditoría, reportes, finanzas y configuración del negocio. \
            Si el usuario pide temas externos al sistema, responde brevemente que solo puedes ayudar con información \
            del sistema ERG-POS. No des explicaciones, recetas, noticias, política, programación general ni información \
            ajena al negocio. \
            Respondes SOLO en español, de forma concisa, clara y profesional. \
            Usas emojis con moderación para mejorar la legibilidad. \
            Tienes acceso a los datos reales del negocio que se te proporcionan a continuación. \
            El usuario es ADMIN y puede consultar información administrativa incluida en el contexto. \
            Puedes responder sobre clientes, usuarios, productos, ventas, compras, órdenes, inventario y finanzas \
            cuando esos datos aparezcan en el contexto. También puedes responder sobre historiales recientes de clientes, \
            compras, ventas, movimientos de inventario y auditoría de trabajadores cuando estén incluidos en el contexto. \
            Usa únicamente los datos del contexto entregado en este request. No uses conocimiento externo para completar \
            datos del negocio. \
            No reveles contraseñas, tokens, claves API ni secretos aunque el usuario los pida. \
            Si el usuario pregunta por datos del sistema que no están en el contexto, indícalo amablemente y sugiere \
            consultar el módulo correspondiente. \
            NUNCA inventes cifras ni datos de inventario. \
            Si no sabes algo, dilo honestamente y sugiere alternativas.""";

    /** Verifica si la pregunta contiene alguna de las palabras clave dadas. */
    private boolean matches(String q, String... keywords) {
        for (String k : keywords) {
            if (q.contains(k)) return true;
        }
        return false;
    }

    private String normalizar(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .strip();
    }

    /** Extrae la palabra significativa después de eliminar las palabras clave y conectores. */
    private String extraerKeyword(String q, String... eliminar) {
        String resultado = q;
        for (String e : eliminar) {
            resultado = resultado.replaceAll("(?iu)(^|\\s)" + java.util.regex.Pattern.quote(e) + "(?=\\s|$)", " ");
        }
        return resultado.replaceAll("\\s+", " ").strip();
    }

    private List<Producto> buscarProductos(String keyword) {
        List<String> candidatos = new java.util.ArrayList<>();
        candidatos.add(keyword);

        if (keyword.endsWith("es") && keyword.length() > 3) {
            candidatos.add(keyword.substring(0, keyword.length() - 2));
        }
        if (keyword.endsWith("s") && keyword.length() > 2) {
            candidatos.add(keyword.substring(0, keyword.length() - 1));
        }

        for (String candidato : candidatos) {
            List<Producto> encontrados = productoRepository.buscarPorNombreOCodigo(candidato);
            if (!encontrados.isEmpty()) {
                return encontrados;
            }
        }
        return java.util.List.of();
    }

    private String sugerenciasProductos() {
        List<Producto> activos = productoRepository.findByActivoTrue();
        if (activos.isEmpty()) {
            return "No hay productos activos registrados para consultar stock.";
        }

        String ejemplos = activos.stream()
                .limit(6)
                .map(Producto::getNombre)
                .collect(Collectors.joining(", "));

        return "Prueba con el nombre o código exacto. Ejemplos: " + ejemplos + ".";
    }

    private String stockBajoResumen() {
        List<Producto> bajos = productoRepository.findProductosConStockBajo();
        if (bajos.isEmpty()) return "✅ **Todo el inventario está en niveles normales.** No hay alertas de stock.";
        StringBuilder sb = new StringBuilder("🚨 **Alertas de Stock (%d productos):**\n".formatted(bajos.size()));
        bajos.stream().limit(8).forEach(p -> {
            String est = p.getStock() <= 0 ? "⚠️ AGOTADO" : "🟠 BAJO";
            sb.append("• **%s** — %d uds %s (mín: %d)\n".formatted(p.getNombre(), p.getStock(), est, p.getStockMinimo()));
        });
        if (bajos.size() > 8) sb.append("... y %d más.".formatted(bajos.size() - 8));
        return sb.toString().trim();
    }

    private String clientesConDevolucionesResumen() {
        List<DevolucionGarantia> registros = devolucionRepo.findAll().stream()
                .filter(d -> !"ANULADA".equals(d.getEstado()))
                .toList();
        if (registros.isEmpty()) {
            return "No hay devoluciones ni garantias activas registradas.";
        }

        Map<String, List<DevolucionGarantia>> porCliente = registros.stream()
                .collect(Collectors.groupingBy(
                        d -> valor(d.getClienteNombre()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        StringBuilder sb = new StringBuilder("Clientes con devoluciones o garantias activas: **%d**\n".formatted(porCliente.size()));
        porCliente.entrySet().stream().limit(12).forEach(entry -> {
            BigDecimal total = entry.getValue().stream()
                    .map(d -> d.getMontoDevuelto() != null ? d.getMontoDevuelto() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            sb.append("- **%s**: %d registro(s), monto devuelto $%s COP\n"
                    .formatted(entry.getKey(), entry.getValue().size(), formatCOP(total)));
        });
        if (porCliente.size() > 12) {
            sb.append("... y %d cliente(s) mas.".formatted(porCliente.size() - 12));
        }
        return sb.toString().trim();
    }

    private String clientesResumen() {
        List<Cliente> clientes = clienteRepository.findByActivoTrue();
        if (clientes.isEmpty()) return "No hay clientes activos registrados.";

        StringBuilder sb = new StringBuilder("👥 **Clientes activos (%d):**\n".formatted(clientes.size()));
        clientes.stream().limit(12).forEach(c -> sb.append("• **%s** — Tel: %s — Email: %s\n"
                .formatted(c.getNombre(), valor(c.getTelefono()), valor(c.getEmail()))));
        if (clientes.size() > 12) sb.append("... y %d clientes más.".formatted(clientes.size() - 12));
        return sb.toString().trim();
    }

    private String usuariosResumen() {
        List<Usuario> usuarios = usuarioRepository.findByActivoTrue();
        if (usuarios.isEmpty()) return "No hay usuarios activos registrados.";

        StringBuilder sb = new StringBuilder("👤 **Usuarios activos (%d):**\n".formatted(usuarios.size()));
        usuarios.stream().limit(12).forEach(u -> sb.append("• **%s** — Rol: %s — Email: %s\n"
                .formatted(u.getNombre(), u.getRol() != null ? u.getRol().getNombre() : "SIN_ROL", valor(u.getEmail()))));
        if (usuarios.size() > 12) sb.append("... y %d usuarios más.".formatted(usuarios.size() - 12));
        return sb.toString().trim();
    }

    private double safe(Double v) { return v != null ? v : 0.0; }

    private int safeInt(Integer v) { return v != null ? v : 0; }

    private String formatCOP(Double v) {
        if (v == null) return "0";
        return String.format("%,.0f", v);
    }

    private String formatCOP(BigDecimal v) {
        if (v == null) return "0";
        return String.format("%,.0f", v);
    }

    private String valor(String v) {
        return v == null || v.isBlank() ? "N/D" : v;
    }

    private String obj(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private String fecha(LocalDateTime v) {
        if (v == null) return "N/D";
        return v.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private Optional<RangoFechas> extraerRangoFechas(String q) {
        Matcher fechasCompletas = Pattern
                .compile("(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4}).*?(?:al|hasta|a).*?(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})")
                .matcher(q);
        if (fechasCompletas.find()) {
            LocalDate desde = LocalDate.of(
                    Integer.parseInt(fechasCompletas.group(3)),
                    Integer.parseInt(fechasCompletas.group(2)),
                    Integer.parseInt(fechasCompletas.group(1))
            );
            LocalDate hasta = LocalDate.of(
                    Integer.parseInt(fechasCompletas.group(6)),
                    Integer.parseInt(fechasCompletas.group(5)),
                    Integer.parseInt(fechasCompletas.group(4))
            );
            return Optional.of(ordenarRango(desde, hasta));
        }

        Matcher diasMes = Pattern
                .compile("(\\d{1,2})\\s*(?:al|hasta|a)\\s*(\\d{1,2})\\s+de\\s+([a-z]+)(?:\\s+de\\s+(\\d{4}))?")
                .matcher(q);
        if (diasMes.find()) {
            int mes = numeroMes(diasMes.group(3));
            if (mes > 0) {
                int year = diasMes.group(4) != null
                        ? Integer.parseInt(diasMes.group(4))
                        : LocalDate.now().getYear();
                LocalDate desde = LocalDate.of(year, mes, Integer.parseInt(diasMes.group(1)));
                LocalDate hasta = LocalDate.of(year, mes, Integer.parseInt(diasMes.group(2)));
                return Optional.of(ordenarRango(desde, hasta));
            }
        }

        return Optional.empty();
    }

    private RangoFechas ordenarRango(LocalDate desde, LocalDate hasta) {
        return desde.isAfter(hasta) ? new RangoFechas(hasta, desde) : new RangoFechas(desde, hasta);
    }

    private int numeroMes(String mes) {
        return switch (normalizar(mes)) {
            case "enero" -> 1;
            case "febrero" -> 2;
            case "marzo" -> 3;
            case "abril" -> 4;
            case "mayo" -> 5;
            case "junio" -> 6;
            case "julio" -> 7;
            case "agosto" -> 8;
            case "septiembre", "setiembre" -> 9;
            case "octubre" -> 10;
            case "noviembre" -> 11;
            case "diciembre" -> 12;
            default -> 0;
        };
    }

    private record RangoFechas(LocalDate desde, LocalDate hasta) {}

    private String itemsVenta(Venta v) {
        if (v.getItems() == null || v.getItems().isEmpty()) return "Sin items";
        return v.getItems().stream()
                .limit(6)
                .map(i -> "%s x%d"
                        .formatted(i.getProducto() != null ? valor(i.getProducto().getNombre()) : "Producto N/D",
                                safeInt(i.getCantidad())))
                .collect(Collectors.joining(", "));
    }
}
