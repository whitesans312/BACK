package com.ergpos.app.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ergpos.app.model.Rol;

public interface RolRepository extends JpaRepository<Rol, UUID> {
    Rol findByNombre(String nombre);
}