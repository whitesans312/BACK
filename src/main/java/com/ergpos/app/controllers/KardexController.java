package com.ergpos.app.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.services.KardexService;
import com.ergpos.app.services.KardexService.KardexRow;

@RestController
@RequestMapping("/api/kardex")
public class KardexController {

    private final KardexService kardexService;

    public KardexController(KardexService kardexService) {
        this.kardexService = kardexService;
    }

    @GetMapping("/{productoId}")
    public List<KardexRow> getKardex(@PathVariable UUID productoId) {
        return kardexService.getKardex(productoId);
    }
}