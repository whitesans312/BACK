package com.ergpos.app.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ergpos.app.model.Rol;
import com.ergpos.app.repositories.RolRepository;

@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final RolRepository rolRepository;

    public RolController(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    @GetMapping
    public List<Rol> getAll() {
        return rolRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Rol> getById(@PathVariable("id") UUID id) {
        return rolRepository.findById(id)
                .map(r -> ResponseEntity.ok(r))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}