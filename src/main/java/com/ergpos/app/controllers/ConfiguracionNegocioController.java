package com.ergpos.app.controllers;

import com.ergpos.app.model.ConfiguracionNegocio;
import com.ergpos.app.services.AuditoriaService;
import com.ergpos.app.services.ConfiguracionNegocioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configuracion")
public class ConfiguracionNegocioController {

    private final ConfiguracionNegocioService service;
    private final AuditoriaService auditoriaService;

    public ConfiguracionNegocioController(ConfiguracionNegocioService service, AuditoriaService auditoriaService) {
        this.service = service;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<ConfiguracionNegocio> getAll() {
        return service.findAll();
    }

    @PutMapping
    public ResponseEntity<List<ConfiguracionNegocio>> updateAll(@RequestBody List<ConfiguracionNegocio> configs) {
        List<ConfiguracionNegocio> guardadas = service.saveAll(configs);
        
        auditoriaService.registrarActual(
            "EDITAR_CONFIGURACION", "CONFIGURACION",
            null,
            "Se actualizaron " + configs.size() + " parametros de configuracion de negocio."
        );
        
        return ResponseEntity.ok(guardadas);
    }
}
