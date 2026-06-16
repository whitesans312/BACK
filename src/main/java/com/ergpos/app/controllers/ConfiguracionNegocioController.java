package com.ergpos.app.controllers;

import com.ergpos.app.model.ConfiguracionNegocio;
import com.ergpos.app.services.AuditoriaService;
import com.ergpos.app.services.ConfiguracionNegocioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/agrupada")
    public Map<String, List<ConfiguracionNegocio>> getAgrupada() {
        return service.findAllGroupedByCategoria();
    }

    @GetMapping("/categoria/{categoria}")
    public List<ConfiguracionNegocio> getByCategoria(@PathVariable String categoria) {
        return service.findByCategoria(categoria.toUpperCase());
    }

    @GetMapping("/{clave}")
    public ResponseEntity<ConfiguracionNegocio> getByClave(@PathVariable String clave) {
        return service.findByClave(clave.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ConfiguracionNegocio> create(@RequestBody ConfiguracionNegocio config) {
        ConfiguracionNegocio guardada = service.save(config);
        auditoriaService.registrarActual(
            "CREAR_CONFIGURACION", "CONFIGURACION",
            null,
            "Se creo parametro de configuracion: " + guardada.getClave()
        );
        return ResponseEntity.ok(guardada);
    }

    @PutMapping("/{clave}")
    public ResponseEntity<ConfiguracionNegocio> updateOne(
            @PathVariable String clave,
            @RequestBody ConfiguracionNegocio config) {
        config.setClave(clave);
        ConfiguracionNegocio guardada = service.save(config);
        auditoriaService.registrarActual(
            "EDITAR_CONFIGURACION", "CONFIGURACION",
            null,
            "Se actualizo parametro de configuracion: " + guardada.getClave()
        );
        return ResponseEntity.ok(guardada);
    }

    @DeleteMapping("/{clave}")
    public ResponseEntity<Void> delete(@PathVariable String clave) {
        service.deleteByClave(clave.toUpperCase());
        auditoriaService.registrarActual(
            "ELIMINAR_CONFIGURACION", "CONFIGURACION",
            null,
            "Se elimino parametro de configuracion: " + clave.toUpperCase()
        );
        return ResponseEntity.noContent().build();
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

    @GetMapping("/export")
    public List<ConfiguracionNegocio> exportJson() {
        return service.findAll();
    }

    @PostMapping("/import")
    public ResponseEntity<List<ConfiguracionNegocio>> importJson(@RequestBody List<ConfiguracionNegocio> configs) {
        List<ConfiguracionNegocio> guardadas = service.saveAll(configs);
        auditoriaService.registrarActual(
            "IMPORTAR_CONFIGURACION", "CONFIGURACION",
            null,
            "Se importaron " + configs.size() + " parametros de configuracion."
        );
        return ResponseEntity.ok(guardadas);
    }

    @GetMapping("/plantillas/{tipoNegocio}")
    public List<ConfiguracionNegocio> previewPlantilla(@PathVariable String tipoNegocio) {
        return service.getPlantilla(tipoNegocio);
    }

    @PostMapping("/plantillas/{tipoNegocio}/aplicar")
    public ResponseEntity<List<ConfiguracionNegocio>> aplicarPlantilla(@PathVariable String tipoNegocio) {
        List<ConfiguracionNegocio> guardadas = service.aplicarPlantilla(tipoNegocio);
        auditoriaService.registrarActual(
            "APLICAR_PLANTILLA_CONFIGURACION", "CONFIGURACION",
            null,
            "Se aplico plantilla de configuracion: " + tipoNegocio
        );
        return ResponseEntity.ok(guardadas);
    }
}
