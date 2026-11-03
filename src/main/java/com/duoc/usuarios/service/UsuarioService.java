package com.duoc.usuarios.service;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duoc.usuarios.dto.LoginRequest;
import com.duoc.usuarios.dto.LoginResponse;
import com.duoc.usuarios.dto.UsuarioRequest;
import com.duoc.usuarios.dto.UsuarioResponse;
import com.duoc.usuarios.dto.UsuarioUpdateRequest;
import com.duoc.usuarios.exception.AccesoDenegadoException;
import com.duoc.usuarios.exception.ConflictoException;
import com.duoc.usuarios.exception.CredencialesInvalidasException;
import com.duoc.usuarios.exception.RecursoNoEncontradoException;
import com.duoc.usuarios.model.Rol;
import com.duoc.usuarios.model.Usuario;
import com.duoc.usuarios.repository.RolRepository;
import com.duoc.usuarios.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final String ROL_ADMIN = "ADMIN";
    private static final String ROL_USUARIO = "USUARIO";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll().stream().map(UsuarioResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id, Authentication auth) {
        verificarPropietarioOAdmin(id, auth);
        return UsuarioResponse.from(buscarPorId(id));
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarPorRol(String rol) {
        buscarRol(rol);
        return usuarioRepository.findByRolNombreIgnoreCaseOrderByNombreAsc(rol).stream()
                .map(UsuarioResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> buscarPorNombre(String nombre) {
        return usuarioRepository.findByNombreContainingIgnoreCaseOrderByNombreAsc(nombre.trim()).stream()
                .map(UsuarioResponse::from).toList();
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request, Authentication auth) {
        if (usuarioRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictoException("Ya existe un usuario registrado con el email " + request.email());
        }
        String nombreRol = request.rol() == null ? ROL_USUARIO : request.rol().toUpperCase();
        if (!ROL_USUARIO.equals(nombreRol) && !esAdmin(auth)) {
            throw new AccesoDenegadoException("Solo un administrador puede crear usuarios con rol " + nombreRol);
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(request.email().trim().toLowerCase());
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRol(buscarRol(nombreRol));

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Usuario creado: id={}, email={}, rol={}", guardado.getId(), guardado.getEmail(), nombreRol);
        return UsuarioResponse.from(guardado);
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioUpdateRequest request, Authentication auth) {
        verificarPropietarioOAdmin(id, auth);
        Usuario usuario = buscarPorId(id);

        String nuevoEmail = request.email().trim().toLowerCase();
        if (!usuario.getEmail().equalsIgnoreCase(nuevoEmail) && usuarioRepository.existsByEmailIgnoreCase(nuevoEmail)) {
            throw new ConflictoException("Ya existe un usuario registrado con el email " + nuevoEmail);
        }
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(nuevoEmail);

        if (request.password() != null) {
            usuario.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.rol() != null && !request.rol().equalsIgnoreCase(usuario.getRol().getNombre())) {
            if (!esAdmin(auth)) {
                throw new AccesoDenegadoException("Solo un administrador puede cambiar el rol de un usuario");
            }
            usuario.setRol(buscarRol(request.rol()));
        }

        Usuario actualizado = usuarioRepository.save(usuario);
        log.info("Usuario actualizado: id={}", id);
        return UsuarioResponse.from(actualizado);
    }

    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = buscarPorId(id);
        usuarioRepository.delete(usuario);
        log.info("Usuario eliminado: id={}, email={}", id, usuario.getEmail());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new CredencialesInvalidasException("Email o contraseña incorrectos"));
        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            log.warn("Intento de inicio de sesión fallido para {}", request.email());
            throw new CredencialesInvalidasException("Email o contraseña incorrectos");
        }
        log.info("Inicio de sesión exitoso: {}", usuario.getEmail());
        return new LoginResponse("Inicio de sesión exitoso. Bienvenido/a " + usuario.getNombre(),
                UsuarioResponse.from(usuario));
    }

    private Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un usuario con id " + id));
    }

    private Rol buscarRol(String nombre) {
        return rolRepository.findByNombreIgnoreCase(nombre)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol " + nombre));
    }

    private boolean esAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + ROL_ADMIN));
    }

    /** Un USUARIO solo puede ver o modificar su propia cuenta; un ADMIN puede todas. */
    private void verificarPropietarioOAdmin(Long id, Authentication auth) {
        if (esAdmin(auth)) {
            return;
        }
        boolean esPropietario = auth != null && usuarioRepository.findByEmailIgnoreCase(auth.getName())
                .map(actual -> actual.getId().equals(id))
                .orElse(false);
        if (!esPropietario) {
            throw new AccesoDenegadoException("No tienes permisos para acceder a la cuenta de otro usuario");
        }
    }
}
