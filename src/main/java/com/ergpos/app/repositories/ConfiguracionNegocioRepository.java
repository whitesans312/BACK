package com.ergpos.app.repositories;

import com.ergpos.app.model.ConfiguracionNegocio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConfiguracionNegocioRepository extends JpaRepository<ConfiguracionNegocio, String> {
    List<ConfiguracionNegocio> findByCategoria(String categoria);
}
