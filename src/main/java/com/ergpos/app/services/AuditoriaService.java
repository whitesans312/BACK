package com.ergpos.app.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ergpos.app.model.Auditoria;
import com.ergpos.app.model.Usuario;
import com.ergpos.app.repositories.AuditoriaRepository;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    public void registrar(UUID usuarioId, String usuarioNombre,
                          String accion, String modulo,
                          UUID entidadId, String detalle) {
        try {
            Usuario actual = getUsuarioActual();
            UUID finalUsuarioId = usuarioId != null
                    ? usuarioId
                    : actual != null ? actual.getId() : null;

            Auditoria a = new Auditoria(
                    finalUsuarioId,
                    normalizarUsuarioNombre(usuarioNombre, actual),
                    accion,
                    modulo,
                    entidadId,
                    detalle
            );
            a.setEntidadTipo(inferirEntidadTipo(modulo));
            a.setResultado("EXITOSO");
            a.setSeveridad("INFO");
            completarContextoHttp(a);

            auditoriaRepository.save(a);
        } catch (Exception e) {
            // La auditoria nunca debe interrumpir el flujo principal.
            System.err.println("[AUDITORIA] Error al registrar: " + e.getMessage());
        }
    }

    public void registrarActual(String accion, String modulo,
                                UUID entidadId, String detalle) {
        Usuario usuario = getUsuarioActual();
        registrar(
            usuario != null ? usuario.getId() : null,
            usuario != null ? usuario.getNombre() : "Sistema",
            accion, modulo, entidadId, detalle
        );
    }

    private Usuario getUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Usuario usuario)) {
            return null;
        }
        return usuario;
    }

    private String normalizarUsuarioNombre(String usuarioNombre, Usuario actual) {
        if (usuarioNombre != null && !usuarioNombre.isBlank()) {
            return usuarioNombre;
        }
        if (actual != null && actual.getNombre() != null && !actual.getNombre().isBlank()) {
            return actual.getNombre();
        }
        return "Sistema";
    }

    private String inferirEntidadTipo(String modulo) {
        if (modulo == null) return null;
        return switch (modulo) {
            case "ORDENES" -> "ENTREGA";
            case "VENTAS" -> "VENTA";
            case "COMPRAS" -> "COMPRA";
            case "INVENTARIO" -> "MOVIMIENTO_INVENTARIO";
            case "DEVOLUCIONES" -> "DEVOLUCION_GARANTIA";
            case "CLIENTES" -> "CLIENTE";
            case "USUARIOS", "SEGURIDAD" -> "USUARIO";
            case "PRODUCTOS" -> "PRODUCTO";
            case "PROVEEDORES" -> "PROVEEDOR";
            case "CATEGORIAS" -> "CATEGORIA";
            case "CONFIGURACION" -> "CONFIGURACION_NEGOCIO";
            default -> modulo;
        };
    }

    private void completarContextoHttp(Auditoria auditoria) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }

        HttpServletRequest request = attrs.getRequest();
        auditoria.setIpOrigen(extraerIpCliente(request));
        auditoria.setUserAgent(limitar(request.getHeader("User-Agent"), 255));
        auditoria.setMetodoHttp(limitar(request.getMethod(), 10));
        auditoria.setRuta(limitar(request.getRequestURI(), 255));
    }

    private String extraerIpCliente(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return limitar(forwarded.split(",")[0].trim(), 45);
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return limitar(realIp.trim(), 45);
        }

        return limitar(request.getRemoteAddr(), 45);
    }

    private String limitar(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public List<Auditoria> findRecientes(int limit) {
        return auditoriaRepository.findByOrderByFechaDesc(PageRequest.of(0, limit));
    }

    public List<Auditoria> findAll(int limit) {
        return auditoriaRepository.findByOrderByFechaDesc(PageRequest.of(0, limit));
    }

    public List<Auditoria> findByModulo(String modulo, int limit) {
        return auditoriaRepository.findByModuloOrderByFechaDesc(modulo, PageRequest.of(0, limit));
    }

    public List<Auditoria> findByUsuario(UUID usuarioId, int limit) {
        return auditoriaRepository.findByUsuarioIdOrderByFechaDesc(usuarioId, PageRequest.of(0, limit));
    }

    public List<Auditoria> findByAccion(String accion, int limit) {
        return auditoriaRepository.findByAccionOrderByFechaDesc(accion, PageRequest.of(0, limit));
    }

    public List<Auditoria> findConFiltros(String modulo, UUID usuarioId, String accion,
                                          String entidadTipo, String resultado, String severidad,
                                          LocalDate fechaDesde, LocalDate fechaHasta,
                                          int limit) {
        String m = (modulo != null && !modulo.isBlank()) ? modulo : null;
        String a = (accion != null && !accion.isBlank()) ? accion : null;
        String e = (entidadTipo != null && !entidadTipo.isBlank()) ? entidadTipo : null;
        String r = (resultado != null && !resultado.isBlank()) ? resultado : null;
        String s = (severidad != null && !severidad.isBlank()) ? severidad : null;
        LocalDateTime desde = fechaDesde != null ? fechaDesde.atStartOfDay() : null;
        LocalDateTime hasta = fechaHasta != null ? fechaHasta.plusDays(1).atStartOfDay().minusNanos(1) : null;
        int safeLimit = Math.max(1, Math.min(limit, 1000));

        Specification<Auditoria> spec = (root, query, cb) -> cb.conjunction();
        if (m != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("modulo"), m));
        }
        if (a != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("accion"), a));
        }
        if (usuarioId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("usuarioId"), usuarioId));
        }
        if (e != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entidadTipo"), e));
        }
        if (r != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("resultado"), r));
        }
        if (s != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("severidad"), s));
        }
        if (desde != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fecha"), desde));
        }
        if (hasta != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fecha"), hasta));
        }

        return auditoriaRepository.findAll(
                spec,
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "fecha"))
        ).getContent();
    }
}
