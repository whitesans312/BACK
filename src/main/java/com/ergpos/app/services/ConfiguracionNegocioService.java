package com.ergpos.app.services;

import com.ergpos.app.model.ConfiguracionNegocio;
import com.ergpos.app.repositories.ConfiguracionNegocioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
        config.setUpdatedAt(LocalDateTime.now());
        return repo.save(config);
    }

    @Transactional
    public List<ConfiguracionNegocio> saveAll(List<ConfiguracionNegocio> configs) {
        LocalDateTime now = LocalDateTime.now();
        for (ConfiguracionNegocio config : configs) {
            config.setUpdatedAt(now);
        }
        return repo.saveAll(configs);
    }
}
