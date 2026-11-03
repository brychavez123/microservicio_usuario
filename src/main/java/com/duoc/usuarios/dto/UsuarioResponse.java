package com.duoc.usuarios.dto;

import java.time.LocalDate;

import com.duoc.usuarios.model.Usuario;

public record UsuarioResponse(Long id, String nombre, String email, String rol, LocalDate fechaRegistro) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol().getNombre(),
                usuario.getFechaRegistro());
    }
}
