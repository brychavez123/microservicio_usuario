package com.duoc.usuarios.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duoc.usuarios.dto.LoginRequest;
import com.duoc.usuarios.dto.LoginResponse;
import com.duoc.usuarios.dto.MensajeResponse;
import com.duoc.usuarios.dto.UsuarioRequest;
import com.duoc.usuarios.dto.UsuarioResponse;
import com.duoc.usuarios.dto.UsuarioUpdateRequest;
import com.duoc.usuarios.service.UsuarioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    /** ADMIN: lista todos los usuarios. */
    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    /** ADMIN o el propio usuario. */
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtener(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(usuarioService.obtener(id, auth));
    }

    /** ADMIN: usuarios filtrados por rol (USUARIO / ADMIN). */
    @GetMapping("/rol/{rol}")
    public ResponseEntity<List<UsuarioResponse>> listarPorRol(@PathVariable String rol) {
        return ResponseEntity.ok(usuarioService.listarPorRol(rol));
    }

    /** ADMIN: búsqueda por nombre (coincidencia parcial). */
    @GetMapping("/buscar")
    public ResponseEntity<List<UsuarioResponse>> buscar(@RequestParam String nombre) {
        return ResponseEntity.ok(usuarioService.buscarPorNombre(nombre));
    }

    /** Público: registro de usuario. Crear un ADMIN requiere estar autenticado como ADMIN. */
    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request, auth));
    }

    /** ADMIN o el propio usuario. */
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody UsuarioUpdateRequest request, Authentication auth) {
        return ResponseEntity.ok(usuarioService.actualizar(id, request, auth));
    }

    /** ADMIN: elimina un usuario. */
    @DeleteMapping("/{id}")
    public ResponseEntity<MensajeResponse> eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.ok(new MensajeResponse("Usuario con id " + id + " eliminado correctamente"));
    }

    /** Público: inicio de sesión. */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(usuarioService.login(request));
    }
}
