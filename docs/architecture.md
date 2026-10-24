# Order Service Architecture

## 1. Arquitectura general

El siguiente diagrama representa los principales componentes del servicio y el flujo asíncrono de procesamiento de órdenes.

```mermaid
flowchart TD

    A[Client / Postman]
    B[Spring Security]
    C[JWT Validation]
    D[OrderController]
    E[OrderService]

    F[(PostgreSQL)]
    G[OutboxEvent]
    H[OutboxPublisher]
    I[Apache Kafka]
    J[OrderCreatedConsumer]
    K[PaymentClient]
    L[PaymentService]
    M[ProcessedEvent]

    A --> B
    B --> C
    C --> D
    D --> E

    E --> F
    E --> G

    G --> H
    H --> I
    I --> J

    J --> K
    K --> L

    J --> M
```

## 2. Autenticación y autorización JWT

La aplicación utiliza JWT para autenticar usuarios y proteger los endpoints privados.

El flujo se divide en dos etapas:

- autenticación, donde el usuario obtiene un token;
- autorización, donde Spring Security valida el JWT en cada request protegida.

```mermaid
flowchart TD

    A[Client / Postman]

    B[AuthController]
    C[AuthService]
    D[(users table)]
    E[BCrypt PasswordEncoder]
    F[JwtService]

    G[JWT Token]

    H[Protected Request]
    I[Spring Security]
    J[JwtDecoder]
    K[JwtRoleConverter]

    L[ROLE_USER / ROLE_ADMIN]

    M[OrderController]

    A -->|POST /api/v1/auth/login| B
    B --> C

    C --> D
    C --> E

    E --> C

    C --> F
    F --> G

    G --> A

    A --> H
    H -->|Authorization: Bearer JWT| I

    I --> J
    J --> K

    K --> L
    L --> M
```

### Claims principales del JWT

El token contiene información utilizada para identificar al usuario autenticado.

```text
sub
userId
role
iss
iat
exp
```

Ejemplo conceptual:

```json
{
  "sub": "user@test.com",
  "userId": 2,
  "role": "USER",
  "iss": "order-service"
}
```

### Roles

Los roles utilizados actualmente son:

```text
USER
ADMIN
```

El claim `role` se convierte en una authority de Spring Security.

```text
USER
-> ROLE_USER

ADMIN
-> ROLE_ADMIN
```

Esto permite utilizar controles como:

```java
@PreAuthorize("hasRole('ADMIN')")
```

### Protección BOLA / IDOR

El `userId` utilizado por el backend no se recibe directamente desde el cliente.

Se obtiene desde el JWT validado.

```mermaid
flowchart TD

    A[Authenticated Client]
    B[JWT]
    C[userId claim]
    D[OrderController]
    E[OrderService]
    F[OrderRepository]

    A --> B
    B --> C
    C --> D
    D --> E

    E -->|orderId + authenticated userId| F
```

Para usuarios normales, la consulta se realiza utilizando ownership:

```java
findByIdAndUserId(orderId, authenticatedUserId)
```

Esto evita que un usuario pueda acceder a una orden perteneciente a otro usuario simplemente modificando el identificador de la URL.

Un usuario con rol `ADMIN` puede consultar órdenes sin aplicar esta restricción de ownership.