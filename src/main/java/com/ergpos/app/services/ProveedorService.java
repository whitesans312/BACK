package com.ergpos.app.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ergpos.app.model.Proveedor;
import com.ergpos.app.repositories.ProveedorRepository;

@Service
public class ProveedorService {

    private final ProveedorRepository repo;

    public ProveedorService(ProveedorRepository repo) {
        this.repo = repo;
    }

    public List<Proveedor> findAll()           { return repo.findAll(); }
    public List<Proveedor> findActivos()       { return repo.findByActivoTrue(); }
    public List<Proveedor> buscar(String q)    { return repo.buscar(q); }
    public Optional<Proveedor> findById(UUID id) { return repo.findById(id); }
    public boolean existsByNit(String nit)     {
        return nit != null && !nit.isBlank() && repo.existsByNit(nit);
    }

    public Proveedor save(Proveedor p) {
        p.setUpdatedAt(LocalDateTime.now());
        return repo.save(p);
    }
}