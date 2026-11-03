package com.duoc.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos para modificar un usuario. La contraseña y el rol son opcionales. */
public record UsuarioUpdateRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
        String nombre,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres")
        String email,

        @Size(min = 6, max = 60, message = "La contraseña debe tener entre 6 y 60 caracteres")
        String password,

        @Pattern(regexp = "(?i)USUARIO|MODERADOR|ADMIN", message = "El rol debe ser USUARIO, MODERADOR o ADMIN")
        String rol) {
}
