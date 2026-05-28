package com.ergpos.app.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.ergpos.app.model.Auditoria;

@Repository
public interface AuditoriaRepository extends JpaRepository<Auditoria, UUID>, JpaSpecificationExecutor<Auditoria> {

    List<Auditoria> findByOrderByFechaDesc(Pageable pageable);

    List<Auditoria> findByModuloOrderByFechaDesc(String modulo, Pageable pageable);

    List<Auditoria> findByUsuarioIdOrderByFechaDesc(UUID usuarioId, Pageable pageable);

    long countByUsuarioId(UUID usuarioId);

    List<Auditoria> findByAccionOrderByFechaDesc(String accion, Pageable pageable);
}
