# Order Service

Proyecto backend desarrollado con Java 17 y Spring Boot como laboratorio práctico para preparación de entrevistas técnicas Senior Backend Java.

El proyecto comenzó como una API REST de órdenes y evolucionó incorporando seguridad, control de concurrencia, resiliencia y arquitectura orientada a eventos.

## Tecnologías

- Java 17
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate
- Spring Security
- JWT
- OAuth2 Resource Server
- PostgreSQL
- Apache Kafka
- Docker
- Resilience4j
- Bucket4j
- Jackson
- Maven

## Arquitectura general

```text
Client / Postman
       |
       v
Spring Security
       |
       | JWT
       v
OrderController
       |
       v
OrderService
       |
       +-----------------------+
       |                       |
       v                       v
PostgreSQL                OutboxEvent
                                |
                                v
                         OutboxPublisher
                                |
                                v
                              Kafka
                                |
                                v
                      OrderCreatedConsumer
                                |
                                v
                         PaymentClient
                                |
                                v
                         PaymentService
```

## Funcionalidades implementadas

### API REST

La API utiliza endpoints versionados bajo:

```text
/api/v1
```

Permite crear y consultar órdenes utilizando validaciones, autenticación, autorización e idempotencia.

## Validaciones

Se utiliza Bean Validation con anotaciones como:

```text
@NotNull
@NotEmpty
@Min
@Email
@Size
```

Los errores son procesados mediante un manejador global de excepciones.

## Manejo global de errores

Se implementaron excepciones específicas de dominio:

```text
ProductNotFoundException
OrderNotFoundException
InsufficientStockException
UserAlreadyExistsException
InvalidCredentialsException
RateLimitExceededException
```

Se utiliza:

```java
@RestControllerAdvice
```

para traducir las excepciones a respuestas HTTP.

Entre los códigos utilizados se encuentran:

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
429 Too Many Requests
```

## Persistencia

PostgreSQL se ejecuta mediante Docker.

Principales entidades:

```text
Product
Order
OrderItem
AppUser
OutboxEvent
ProcessedEvent
```

Hibernate administra las entidades mediante JPA, Persistence Context y Dirty Checking.

## Transacciones

Las operaciones principales utilizan:

```java
@Transactional
```

para garantizar atomicidad dentro de PostgreSQL.

Se estudió específicamente que una transacción local no resuelve por sí sola los problemas de concurrencia ni las transacciones distribuidas.

## Optimistic Locking

La entidad `Product` utiliza:

```java
@Version
private Long version;
```

Esto permite detectar conflictos cuando múltiples transacciones intentan modificar simultáneamente el stock.

## Idempotencia HTTP

La creación de órdenes utiliza:

```text
Idempotency-Key
```

Ejemplo:

```http
POST /api/v1/orders
Idempotency-Key: order-test-001
```

Comportamiento:

```text
Primera solicitud
→ 201 Created

Solicitud repetida
→ 200 OK
```

La segunda solicitud reutiliza la orden existente y no vuelve a descontar stock.

La clave también posee una restricción `UNIQUE` en PostgreSQL.

## Autenticación JWT

Se implementaron:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
```

Las contraseñas se almacenan mediante BCrypt.

El JWT contiene claims como:

```json
{
  "sub": "user@test.com",
  "userId": 2,
  "role": "USER",
  "iss": "order-service"
}
```

## Roles y autorización

Roles implementados:

```text
USER
ADMIN
```

Se utiliza:

```java
@PreAuthorize
```

para restringir operaciones según rol.

## Protección BOLA / IDOR

El cliente no controla directamente el `userId` utilizado para crear una orden.

El usuario autenticado se obtiene desde el JWT.

Para consultar órdenes propias se utilizan consultas como:

```java
findByIdAndUserId(orderId, authenticatedUserId)
```

Esto evita que un usuario acceda a órdenes pertenecientes a otro usuario.

## Rate Limiting

Se implementó un Rate Limiter con Bucket4j.

Configuración de laboratorio:

```text
5 requests por minuto por usuario
```

Si se supera el límite:

```text
429 Too Many Requests
```

La implementación actual utiliza memoria local.

En una arquitectura distribuida debería utilizarse Redis, API Gateway o una solución equivalente.

## Circuit Breaker

La integración con Payment utiliza Resilience4j.

Estados principales:

```text
CLOSED
OPEN
HALF_OPEN
```

El Circuit Breaker evita continuar llamando a una dependencia que está fallando.

## Transactional Outbox

Cuando se crea una orden se guardan dentro de la misma transacción:

```text
Order
+
OutboxEvent
```

La orden comienza con estado:

```text
CREATED
```

y el evento Outbox:

```text
ORDER_CREATED
PENDING
```

Esto evita el problema conocido como Dual Write entre PostgreSQL y Kafka.

## Outbox Publisher

Un proceso con:

```java
@Scheduled
```

busca eventos `PENDING` y los publica en Kafka.

Después de una publicación exitosa:

```text
PENDING
→ PUBLISHED
```

## Apache Kafka

Kafka se ejecuta mediante Docker.

Topics utilizados:

```text
order-created
order-created.DLT
```

Los eventos utilizan el `orderId` como Kafka key para mantener afinidad de partición para una misma orden.

## Saga de pago

El consumer `OrderCreatedConsumer` procesa eventos `ORDER_CREATED`.

Pago aprobado:

```text
ORDER_CREATED
→ Payment
→ approved
→ Order = PAID
```

Pago rechazado:

```text
ORDER_CREATED
→ Payment
→ rejected
→ Order = CANCELLED
→ restaurar stock
```

La restitución del stock actúa como operación compensatoria.

## Retry y DLT

Las fallas técnicas del consumer utilizan:

```text
DefaultErrorHandler
FixedBackOff
DeadLetterPublishingRecoverer
```

Flujo:

```text
Consumer
→ error técnico
→ retry
→ retry
→ retries agotados
→ order-created.DLT
```

Una falla técnica de Payment no se interpreta automáticamente como un rechazo de negocio.

## Replay desde DLT

Los eventos de:

```text
order-created.DLT
```

pueden ser diagnosticados y republicados posteriormente en:

```text
order-created
```

cuando la causa del error ha sido corregida.

## Idempotencia del consumer

Cada nuevo evento contiene:

```text
eventId
```

Los eventos procesados se almacenan en:

```text
processed_events
```

Antes de ejecutar los efectos de negocio:

```text
¿eventId ya procesado?

sí
→ ignorar

no
→ procesar
→ registrar eventId
```

Esto permite tolerar entregas duplicadas, retries, rebalanceos y replay.

## Dos niveles de idempotencia

El proyecto utiliza dos mecanismos diferentes:

```text
Idempotency-Key
→ protege requests HTTP duplicados

eventId
→ protege eventos Kafka duplicados
```

## Infraestructura local

Actualmente se utilizan:

```text
order-postgres
→ PostgreSQL
→ puerto 5432

order-kafka
→ Apache Kafka
→ puerto 9092
```

## Estados de una orden

```text
CREATED
PAID
CANCELLED
```

Flujo principal:

```text
CREATED
   |
   +--- pago aprobado ---> PAID
   |
   +--- pago rechazado --> CANCELLED
```

Una falla técnica puede mantener temporalmente la orden en `CREATED` mientras el evento pasa por Retry y DLT.

## Próximos pasos

- Observabilidad con Actuator
- Métricas
- Correlation IDs
- Logging estructurado
- Unit tests
- Integration tests
- Testcontainers
- OpenAPI / Swagger
- Docker Compose
- Kubernetes
- Rate Limiting distribuido
- Mejoras del Transactional Outbox
- System Design

## Objetivo

Este proyecto tiene como objetivo practicar y demostrar conceptos utilizados en Backend Java Senior:

```text
REST
Spring Boot
JPA / Hibernate
PostgreSQL
Transacciones
Concurrencia
Optimistic Locking
Idempotencia
JWT
Roles
BOLA / IDOR
Rate Limiting
Circuit Breaker
Kafka
Transactional Outbox
Saga
Retry
DLT
Replay
Consumer Idempotency
Consistencia eventual
```