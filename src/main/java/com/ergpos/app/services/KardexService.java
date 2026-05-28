package com.ergpos.app.services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ergpos.app.model.MovimientoInventario;
import com.ergpos.app.repositories.MovimientoInventarioRepository;

@Service
public class KardexService {

    private final MovimientoInventarioRepository movimientoRepo;

    public KardexService(MovimientoInventarioRepository movimientoRepo) {
        this.movimientoRepo = movimientoRepo;
    }

    public static class KardexRow {
        public String fecha;
        public String tipo;
        public String concepto;
        public Integer entrada;
        public Integer salida;
        public Integer saldo;

        public KardexRow(String fecha, String tipo, String concepto,
                         Integer entrada, Integer salida, Integer saldo) {
            this.fecha    = fecha;
            this.tipo     = tipo;
            this.concepto = concepto;
            this.entrada  = entrada;
            this.salida   = salida;
            this.saldo    = saldo;
        }
    }

    public List<KardexRow> getKardex(UUID productoId) {
        List<MovimientoInventario> movimientos =
                movimientoRepo.findByProductoIdOrderByFechaAsc(productoId);

        List<KardexRow> kardex = new ArrayList<>();
        int saldo = 0;

        for (MovimientoInventario m : movimientos) {
            boolean esEntrada = "ENTRADA".equals(m.getTipo());
            int entrada = esEntrada ? m.getCantidad() : 0;
            int salida  = esEntrada ? 0 : m.getCantidad();
            saldo += esEntrada ? m.getCantidad() : -m.getCantidad();

            // proveedor ahora es un objeto Proveedor — usamos getNombre()
            String concepto = m.getObservacion() != null
                    ? m.getObservacion()
                    : (m.getProveedor() != null ? "Proveedor: " + m.getProveedor().getNombre() : "");

            kardex.add(new KardexRow(
                    m.getFecha().toString(),
                    m.getTipo(),
                    concepto,
                    entrada > 0 ? entrada : null,
                    salida  > 0 ? salida  : null,
                    saldo
            ));
        }
        return kardex;
    }
}