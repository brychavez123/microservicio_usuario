package com.duoc.usuarios.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público que identifica al microservicio (útil para verificar el despliegue en el cloud). */
@RestController
@RequestMapping("/api/info")
public class InfoController {

    @Value("${spring.application.name}")
    private String nombre;

    @GetMapping
    public ResponseEntity<Map<String, String>> info() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("servicio", nombre);
        body.put("descripcion", "Microservicio de control de usuarios e inicio de sesion");
        body.put("version", "1.0.0");
        body.put("estado", "OK");
        return ResponseEntity.ok(body);
    }
}
