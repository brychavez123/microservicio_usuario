package com.duoc.usuarios.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.duoc.usuarios.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/** Manejo global de excepciones: todas las respuestas de error son JSON con el mismo formato. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponse> conflicto(ConflictoException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> credenciales(CredencialesInvalidasException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccesoDenegadoException ex, HttpServletRequest req) {
        log.warn("Acceso denegado en {}: {}", req.getRequestURI(), ex.getMessage());
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> detalles = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> detalles.putIfAbsent(error.getField(), error.getDefaultMessage()));
        log.debug("Errores de validación en {}: {}", req.getRequestURI(), detalles);
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(), 400, "Bad Request",
                "Los datos enviados no son válidos", req.getRequestURI(), detalles);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> jsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "El parámetro '" + ex.getName() + "' tiene un valor inválido: " + ex.getValue(), req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> parametroFaltante(MissingServletRequestParameterException ex,
            HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, "Falta el parámetro obligatorio '" + ex.getParameterName() + "'", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoSoportado(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest req) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " no soportado en esta ruta", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaNoExiste(NoResourceFoundException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.NOT_FOUND, "La ruta solicitada no existe", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violación de integridad en {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.CONFLICT, "La operación viola una restricción de la base de datos", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> general(Exception ex, HttpServletRequest req) {
        log.error("Error inesperado en {}", req.getRequestURI(), ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno en el servidor", req);
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String mensaje, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), mensaje, req.getRequestURI()));
    }
}
