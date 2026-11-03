# ms-usuarios — Sistema de Foros en Línea

Microservicio de **control de usuarios** (crear, modificar, eliminar) e **inicio de sesión**.
Desarrollo Full Stack III (DSY2205) — Experiencia 1, Sumativa "Programando nuestro BackEnd".

Trabaja junto a [ms-foro](https://github.com/brychavez123/microservicios_foro.git). Ambos usan la misma base de datos Oracle Cloud.

## Tecnologías
- Java 21 + **Spring Boot 3.5** (Maven)
- Spring Web (API REST), Spring Data JPA, Bean Validation
- Spring Security (HTTP Basic + BCrypt, autorización por roles) y CORS
- **Oracle Cloud Autonomous Database** con Wallet (`ojdbc11` + `oraclepki`)
- Lombok (`@Slf4j` para logging) y Actuator

## Arquitectura (por capas)
```
src/main/java/com/duoc/usuarios
├── controller/   UsuarioController      → endpoints REST
├── service/      UsuarioService         → reglas de negocio y permisos
├── repository/   UsuarioRepository, RolRepository → Spring Data JPA
├── model/        Usuario, Rol           → entidades (tablas USUARIO, ROL)
├── dto/          Request / Response     → validaciones y respuestas JSON
├── exception/    GlobalExceptionHandler → errores JSON uniformes (@RestControllerAdvice)
└── security/     SecurityConfig, UsuarioDetailsService
```

## Roles
| Rol | Privilegios en este microservicio |
|-----|-----------------------------------|
| **ADMIN** | Listar, buscar, filtrar por rol, eliminar usuarios y asignar roles |
| **MODERADOR** | Ver y editar su propia cuenta (en ms-foro modera publicaciones) |
| **USUARIO** | Ver y editar su propia cuenta |

## Base de datos
1. Crea tu Autonomous Database en Oracle Cloud y descarga el **Wallet**.
2. Ejecuta [`db/01_usuarios.sql`](db/01_usuarios.sql) como script (F5 en SQL Developer o "Run Script" en Database Actions).
3. Después ejecuta `db/02_foro.sql` del repositorio ms-foro.

Usuarios de prueba:

| Email | Contraseña | Rol |
|-------|-----------|-----|
| admin@foro.cl | Admin123 | ADMIN |
| carla@foro.cl | Admin123 | MODERADOR |
| juan@foro.cl | User1234 | USUARIO |
| maria@foro.cl | User1234 | USUARIO |
| pedro@foro.cl | User1234 | USUARIO |

## Configuración y ejecución
1. Descomprime el Wallet en la raíz del proyecto (ej. `Wallet_foro/`). Está en `.gitignore`, así que no se sube a Git.
2. Copia `db.properties.example` como `db.properties` y completa tus datos:
   ```properties
   DB_TNS_NAME=foro_high
   DB_WALLET_PATH=./Wallet_foro
   DB_USERNAME=ADMIN
   DB_PASSWORD=tu_password
   ```
   `DB_TNS_NAME` es uno de los nombres que aparecen en `Wallet_xxx/tnsnames.ora`.
3. Ejecuta:
   ```bash
   mvn spring-boot:run
   ```
   El servicio queda en `http://localhost:8081`.

## Endpoints
Autenticación: **Basic Auth** (usuario = email).

| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| POST | `/api/usuarios/login` | Público | Inicio de sesión |
| POST | `/api/usuarios` | Público* | Registrar usuario (*crear MODERADOR o ADMIN requiere ADMIN) |
| GET | `/api/usuarios` | ADMIN | Listar usuarios |
| GET | `/api/usuarios/{id}` | Propio / ADMIN | Ver usuario |
| PUT | `/api/usuarios/{id}` | Propio / ADMIN | Modificar usuario (cambiar el rol solo lo puede hacer un ADMIN) |
| DELETE | `/api/usuarios/{id}` | ADMIN | Eliminar usuario |
| GET | `/api/usuarios/rol/{rol}` | ADMIN | Usuarios por rol |
| GET | `/api/usuarios/buscar?nombre=` | ADMIN | Buscar por nombre |

Ejemplo `POST /api/usuarios`:
```json
{ "nombre": "Sofía Rojas", "email": "sofia@foro.cl", "password": "Sofia123" }
```

Códigos de respuesta: `200`, `201` (creado), `400` (validación), `401` (sin login o credenciales malas), `403` (sin permisos para tu rol), `404` (no existe), `409` (email duplicado).

## Postman
Importa [`postman/ms-usuarios.postman_collection.json`](postman/ms-usuarios.postman_collection.json). Las credenciales de cada rol ya están como variables de la colección, y cada petición incluye tests del código de estado.
