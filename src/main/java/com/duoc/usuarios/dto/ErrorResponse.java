package com.duoc.usuarios.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String mensaje,
        String path,
        Map<String, String> detalles) {

    public static ErrorResponse of(int status, String error, String mensaje, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, mensaje, path, null);
    }
}
