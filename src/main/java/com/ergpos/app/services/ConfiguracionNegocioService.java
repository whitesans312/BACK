package com.ergpos.app.services;

import com.ergpos.app.model.ConfiguracionNegocio;
import com.ergpos.app.repositories.ConfiguracionNegocioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ConfiguracionNegocioService {

    private final ConfiguracionNegocioRepository repo;

    public ConfiguracionNegocioService(ConfiguracionNegocioRepository repo) {
        this.repo = repo;
    }

    public List<ConfiguracionNegocio> findAll() {
        return repo.findAll();
    }

    public List<ConfiguracionNegocio> findByCategoria(String categoria) {
        return repo.findByCategoria(categoria);
    }

    public Map<String, List<ConfiguracionNegocio>> findAllGroupedByCategoria() {
        Map<String, List<ConfiguracionNegocio>> grouped = new LinkedHashMap<>();
        for (ConfiguracionNegocio config : repo.findAll()) {
            grouped.computeIfAbsent(config.getCategoria(), key -> new ArrayList<>()).add(config);
        }
        return grouped;
    }

    public Optional<ConfiguracionNegocio> findByClave(String clave) {
        return repo.findById(clave);
    }

    public BigDecimal getTaxDivisor() {
        return repo.findById("TAX_PERCENTAGE")
            .map(c -> {
                BigDecimal pct = new BigDecimal(c.getValor());
                return BigDecimal.ONE.add(pct.divide(BigDecimal.valueOf(100)));
            })
            .orElse(new BigDecimal("1.19"));
    }

    /**
     * Retorna el valor de una clave de configuración como String.
     * Lanza IllegalArgumentException si la clave no existe.
     */
    public String getValor(String clave) {
        return repo.findById(clave)
                .map(ConfiguracionNegocio::getValor)
                .orElseThrow(() -> new IllegalArgumentException("Clave de configuración no encontrada: " + clave));
    }

    @Transactional
    public ConfiguracionNegocio save(ConfiguracionNegocio config) {
        validar(config);
        config.setUpdatedAt(LocalDateTime.now());
        return repo.save(config);
    }

    @Transactional
    public List<ConfiguracionNegocio> saveAll(List<ConfiguracionNegocio> configs) {
        LocalDateTime now = LocalDateTime.now();
        for (ConfiguracionNegocio config : configs) {
            validar(config);
            config.setUpdatedAt(now);
        }
        return repo.saveAll(configs);
    }

    @Transactional
    public void deleteByClave(String clave) {
        repo.deleteById(clave);
    }

    @Transactional
    public List<ConfiguracionNegocio> aplicarPlantilla(String tipoNegocio) {
        return saveAll(getPlantilla(tipoNegocio));
    }

    public List<ConfiguracionNegocio> getPlantilla(String tipoNegocio) {
        String tipo = tipoNegocio == null ? "" : tipoNegocio.trim().toUpperCase();
        List<ConfiguracionNegocio> configs = new ArrayList<>();

        if ("RETAIL".equals(tipo)) {
            configs.add(config("BUSINESS_TYPE", "RETAIL", "EMPRESA", "Tipo de negocio"));
            configs.add(config("TAX_PERCENTAGE", "19", "FISCAL", "Porcentaje de impuesto"));
            configs.add(config("STOCK_MIN_DEFAULT", "5", "INVENTARIO", "Stock minimo por defecto"));
            configs.add(config("ALLOW_NEGATIVE_STOCK", "false", "INVENTARIO", "Permitir stock negativo"));
            configs.add(config("DEFAULT_PAYMENT_STATUS", "COMPLETO", "VENTAS", "Estado de pago por defecto"));
        } else if ("SERVICIOS".equals(tipo) || "SERVICIOS_PROFESIONALES".equals(tipo)) {
            configs.add(config("BUSINESS_TYPE", "SERVICIOS", "EMPRESA", "Tipo de negocio"));
            configs.add(config("TAX_PERCENTAGE", "19", "FISCAL", "Porcentaje de impuesto"));
            configs.add(config("STOCK_MIN_DEFAULT", "0", "INVENTARIO", "Stock minimo por defecto"));
            configs.add(config("DEFAULT_ORDER_TYPE", "INSTALACION", "VENTAS", "Tipo de orden por defecto"));
            configs.add(config("REQUIRE_ADVANCE_PAYMENT", "false", "VENTAS", "Exigir anticipo"));
        } else {
            configs.add(config("BUSINESS_TYPE", "REPARACION_TECNICA", "EMPRESA", "Tipo de negocio"));
            configs.add(config("TAX_PERCENTAGE", "19", "FISCAL", "Porcentaje de impuesto"));
            configs.add(config("STOCK_MIN_DEFAULT", "5", "INVENTARIO", "Stock minimo por defecto"));
            configs.add(config("DEFAULT_ORDER_TYPE", "REPARACION", "VENTAS", "Tipo de orden por defecto"));
            configs.add(config("REQUIRE_ADVANCE_PAYMENT", "true", "VENTAS", "Exigir anticipo"));
            configs.add(config("AUDIT_RETENTION_DAYS", "365", "SEGURIDAD", "Dias de retencion de auditoria"));
        }

        return configs;
    }

    private ConfiguracionNegocio config(String clave, String valor, String categoria, String descripcion) {
        ConfiguracionNegocio config = new ConfiguracionNegocio();
        config.setClave(clave);
        config.setValor(valor);
        config.setCategoria(categoria);
        config.setDescripcion(descripcion);
        return config;
    }

    private void validar(ConfiguracionNegocio config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuracion requerida");
        }
        if (config.getClave() == null || config.getClave().isBlank()) {
            throw new IllegalArgumentException("La clave es obligatoria");
        }
        if (config.getValor() == null) {
            throw new IllegalArgumentException("El valor es obligatorio");
        }
        if (config.getCategoria() == null || config.getCategoria().isBlank()) {
            throw new IllegalArgumentException("La categoria es obligatoria");
        }
        config.setClave(config.getClave().trim().toUpperCase());
        config.setCategoria(config.getCategoria().trim().toUpperCase());
    }
}
