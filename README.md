# Order Service

Backend desarrollado con **Java 17 y Spring Boot** como laboratorio práctico para preparación de entrevistas técnicas **Senior Backend Java**.

El proyecto comenzó como una API REST de órdenes y evolucionó incorporando seguridad, concurrencia, resiliencia, mensajería y patrones de arquitectura orientada a eventos.

Actualmente implementa conceptos como:

- JWT y autorización basada en roles
- protección BOLA / IDOR
- idempotencia HTTP
- Optimistic Locking
- Rate Limiting
- Circuit Breaker
- Transactional Outbox
- Apache Kafka
- Saga de pago
- Retry y Dead Letter Topic
- replay de mensajes
- idempotencia del consumer
- consistencia eventual

---

## Arquitectura

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
       +------------------------+
       |                        |
       v                        v
PostgreSQL                 OutboxEvent
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

La creación de la orden y el evento Outbox se realizan dentro de la misma transacción PostgreSQL.

La comunicación con Payment se realiza posteriormente mediante Kafka, evitando mantener una transacción de base de datos abierta mientras se espera una dependencia externa.

---

## Stack tecnológico

| Tecnología | Uso |
|---|---|
| Java 17 | Lenguaje principal |
| Spring Boot | Framework backend |
| Spring Web MVC | API REST |
| Spring Data JPA | Persistencia |
| Hibernate | ORM y Persistence Context |
| Spring Security | Autenticación y autorización |
| OAuth2 Resource Server | Validación JWT |
| BCrypt | Hash de contraseñas |
| PostgreSQL | Base de datos relacional |
| Apache Kafka | Mensajería asíncrona |
| Spring Kafka | Producer y Consumer |
| Resilience4j | Circuit Breaker |
| Bucket4j | Rate Limiting |
| Jackson | Serialización JSON |
| Maven | Gestión de dependencias |
| Docker | Infraestructura local |

---

# Funcionalidades implementadas

## API REST

La API utiliza versionado:

```text
/api/v1
```

Endpoints principales:

| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Registrar usuario | Público |
| POST | `/api/v1/auth/login` | Autenticar y obtener JWT | Público |
| POST | `/api/v1/orders` | Crear orden | Autenticado |
| GET | `/api/v1/orders/{id}` | Consultar orden | USER / ADMIN |
| GET | `/api/v1/orders` | Listar todas las órdenes | ADMIN |

---

## Validaciones

Los DTO utilizan Bean Validation.

Ejemplos:

```java
@NotNull
@NotEmpty
@Min
@Email
@Size
```

Los errores de validación se procesan de manera centralizada.

---

## Manejo global de errores

Se utiliza:

```java
@RestControllerAdvice
```

junto con excepciones específicas del dominio.

Entre ellas:

```text
ProductNotFoundException
OrderNotFoundException
InsufficientStockException
UserAlreadyExistsException
InvalidCredentialsException
RateLimitExceededException
```

Códigos HTTP utilizados:

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
429 Too Many Requests
```

---

# Persistencia

PostgreSQL se utiliza como base de datos principal.

Entidades relevantes:

```text
Product
Order
OrderItem
AppUser
OutboxEvent
ProcessedEvent
```

Hibernate administra las entidades mediante:

```text
Persistence Context
Dirty Checking
@Transactional
```

---

## Transacciones

Las operaciones principales de negocio utilizan:

```java
@Transactional
```

Esto permite mantener atomicidad dentro de PostgreSQL.

Ejemplo conceptual:

```text
BEGIN

actualizar stock
crear Order
crear OrderItem
crear OutboxEvent

COMMIT
```

Si una operación falla:

```text
ROLLBACK
```

---

# Control de concurrencia

La entidad `Product` utiliza Optimistic Locking:

```java
@Version
private Long version;
```

Esto permite detectar modificaciones concurrentes.

Ejemplo:

```text
Transacción A lee:
stock = 1
version = 4

Transacción B lee:
stock = 1
version = 4
```

La primera actualización cambia:

```text
version = 5
```

La segunda transacción ya no puede actualizar utilizando `version = 4`.

Esto ayuda a evitar problemas de overselling.

---

# Idempotencia HTTP

La creación de órdenes utiliza:

```text
Idempotency-Key
```

Ejemplo:

```http
POST /api/v1/orders
Idempotency-Key: order-test-001
```

Primera ejecución:

```text
201 Created
```

Repetición con la misma clave:

```text
200 OK
```

La segunda solicitud:

- no crea otra orden
- no vuelve a descontar stock
- devuelve la orden existente

La columna `idempotency_key` posee además una restricción `UNIQUE` en PostgreSQL.

---

# Autenticación JWT

El proyecto implementa registro y login.

## Registro

```http
POST /api/v1/auth/register
```

Ejemplo:

```json
{
  "email": "user@test.com",
  "password": "Password123"
}
```

Las contraseñas se almacenan utilizando BCrypt.

Nunca se almacenan en texto plano.

---

## Login

```http
POST /api/v1/auth/login
```

Ejemplo:

```json
{
  "email": "user@test.com",
  "password": "Password123"
}
```

Respuesta:

```json
{
  "token": "eyJ..."
}
```

---

## Claims del JWT

Ejemplo conceptual:

```json
{
  "sub": "user@test.com",
  "userId": 2,
  "role": "USER",
  "iss": "order-service"
}
```

La firma utiliza HMAC SHA-256.

La aplicación trabaja de forma stateless:

```java
SessionCreationPolicy.STATELESS
```

---

# Roles y autorización

Roles implementados:

```text
USER
ADMIN
```

El claim del JWT se convierte en authorities:

```text
USER
-> ROLE_USER

ADMIN
-> ROLE_ADMIN
```

Se utiliza Method Security:

```java
@EnableMethodSecurity
```

y:

```java
@PreAuthorize
```

para proteger operaciones administrativas.

---

# Protección BOLA / IDOR

El cliente no controla el `userId` utilizado para crear una orden.

El identificador se obtiene desde el JWT validado:

```text
JWT
 |
 v
userId
 |
 v
Order
```

Para consultar una orden como usuario se utiliza ownership:

```java
findByIdAndUserId(orderId, authenticatedUserId)
```

Esto evita que un usuario pueda consultar recursos pertenecientes a otro usuario modificando únicamente el identificador de la URL.

Un administrador puede consultar órdenes sin aplicar esta restricción de ownership.

---

# Rate Limiting

Se implementó Rate Limiting utilizando Bucket4j.

Configuración de laboratorio:

```text
5 requests por minuto por usuario
```

Al superar el límite:

```text
429 Too Many Requests
```

Cada usuario posee su propio bucket.

La implementación actual utiliza almacenamiento en memoria.

Por esta razón, en una arquitectura con múltiples réplicas sería necesario utilizar una solución compartida, por ejemplo:

```text
Redis
API Gateway
Ingress Controller
```

---

# Circuit Breaker

La integración con Payment utiliza Resilience4j.

Estados principales:

```text
CLOSED
OPEN
HALF_OPEN
```

Configuración actual de laboratorio:

```text
sliding window size = 5
minimum calls = 3
failure threshold = 50%
open state = 10 segundos
half-open calls = 2
```

El objetivo es evitar seguir invocando una dependencia externa que está fallando.

---

# Transactional Outbox

La aplicación implementa el patrón **Transactional Outbox**.

Cuando se crea una orden se almacenan dentro de la misma transacción:

```text
Order
+
OutboxEvent
```

La orden queda inicialmente:

```text
CREATED
```

y se genera un evento:

```text
ORDER_CREATED
```

con estado:

```text
PENDING
```

Esto evita el problema de Dual Write entre PostgreSQL y Kafka.

---

## Flujo Outbox

```text
POST /orders
      |
      v
@Transactional
      |
      +--> Order = CREATED
      |
      +--> actualizar stock
      |
      +--> OutboxEvent = PENDING
      |
      v
COMMIT
```

Posteriormente:

```text
OutboxPublisher
      |
      v
Kafka
      |
      v
OutboxEvent = PUBLISHED
```

---

# Outbox Publisher

Se utiliza un proceso programado:

```java
@Scheduled
```

que busca eventos:

```text
PENDING
```

y los publica en Kafka.

Después de una publicación correcta:

```text
PENDING
-> PUBLISHED
```

También se registra:

```text
publishedAt
```

---

# Apache Kafka

Kafka se ejecuta mediante Docker.

Imagen utilizada:

```text
apache/kafka
```

Topics principales:

```text
order-created
order-created.DLT
```

El `orderId` se utiliza como Kafka key.

Esto ayuda a mantener los eventos asociados a una misma orden dentro de la misma partición.

---

# Saga de pago

El consumer:

```text
OrderCreatedConsumer
```

procesa eventos provenientes del topic:

```text
order-created
```

---

## Pago aprobado

```text
ORDER_CREATED
      |
      v
PaymentClient
      |
      v
Payment aprobado
      |
      v
Order = PAID
```

El stock permanece descontado porque la venta fue confirmada.

---

## Pago rechazado

```text
ORDER_CREATED
      |
      v
PaymentClient
      |
      v
Payment rejected
      |
      v
Order = CANCELLED
      |
      v
restoreStock()
```

La restitución del stock actúa como operación compensatoria de la Saga.

---

# Falla técnica de Payment

Una falla técnica no se interpreta como un rechazo del pago.

Ejemplos:

```text
timeout
servicio no disponible
excepción técnica
circuit abierto
```

El comportamiento es:

```text
Payment exception
      |
      v
Retry
      |
      v
Retry
      |
      v
Retries agotados
      |
      v
order-created.DLT
```

La orden permanece temporalmente:

```text
CREATED
```

porque el resultado del pago todavía no es conocido.

---

# Retry y Dead Letter Topic

Spring Kafka utiliza:

```text
DefaultErrorHandler
FixedBackOff
DeadLetterPublishingRecoverer
```

Configuración de laboratorio:

```text
intento original
+
2 retries
```

con:

```text
2 segundos de espera
```

Después de agotar los intentos:

```text
order-created
      |
      v
order-created.DLT
```

---

# Replay desde DLT

Los mensajes almacenados en:

```text
order-created.DLT
```

pueden ser analizados y posteriormente republicados en:

```text
order-created
```

cuando la causa técnica haya sido corregida.

Flujo:

```text
DLT
 |
 v
diagnóstico
 |
 v
corrección de la causa
 |
 v
replay controlado
 |
 v
order-created
 |
 v
consumer
```

---

# Idempotencia del consumer Kafka

Cada nuevo `OrderCreatedEvent` incluye:

```text
eventId
```

Ejemplo:

```json
{
  "eventId": "dd2cc850-c222-4e7f-b36e-97b778dc6aa6",
  "orderId": 16,
  "userId": 2,
  "total": 799990.00,
  "createdAt": "2026-10-18T12:41:20"
}
```

Los eventos procesados se almacenan en:

```text
processed_events
```

Antes de ejecutar el efecto de negocio:

```text
¿eventId ya procesado?

sí
-> ignorar evento

no
-> procesar
-> guardar eventId
```

Esto permite tolerar:

```text
retries
rebalances
replay
duplicados
semántica at-least-once
```

La base de datos mantiene además una restricción `UNIQUE` sobre `eventId`.

---

# Dos niveles de idempotencia

El proyecto utiliza dos mecanismos distintos.

## Idempotencia HTTP

```text
Idempotency-Key
```

Protege una operación REST repetida.

## Idempotencia Kafka

```text
eventId
```

Protege un evento asíncrono repetido.

Por lo tanto:

```text
Idempotency-Key
-> Request HTTP

eventId
-> Evento Kafka
```

---

# Estados de una orden

Estados implementados:

```text
CREATED
PAID
CANCELLED
```

Flujo principal:

```text
                   +---- pago aprobado ----> PAID
                   |
CREATED -----------+
                   |
                   +---- pago rechazado ---> CANCELLED
```

Una falla técnica puede dejar temporalmente la orden en `CREATED` mientras el evento pasa por Retry o DLT.

---

# Infraestructura local

Actualmente el proyecto utiliza dos contenedores:

```text
order-postgres
-> PostgreSQL
-> puerto 5432

order-kafka
-> Apache Kafka
-> puerto 9092
```

---

# Quick Start

## Requisitos

Antes de ejecutar el proyecto se necesita:

```text
Java 17
Docker Desktop
Git
```

Maven puede ejecutarse utilizando el Maven Wrapper incluido en el proyecto.

---

## 1. Clonar el repositorio

```bash
git clone https://github.com/ArielMontano/order-service-spring.git
```

Entrar al proyecto:

```bash
cd order-service-spring
```

---

## 2. Crear PostgreSQL

Ejemplo para desarrollo local:

```powershell
docker run --name order-postgres `
  -e POSTGRES_DB=orderdb `
  -e POSTGRES_USER=orderuser `
  -e POSTGRES_PASSWORD=local-dev-password `
  -p 5432:5432 `
  -d postgres:16
```

Si el contenedor ya existe:

```powershell
docker start order-postgres
```

---

## 3. Crear Kafka

```powershell
docker run -d `
  --name order-kafka `
  -p 9092:9092 `
  apache/kafka:4.2.2
```

Si el contenedor ya existe:

```powershell
docker start order-kafka
```

---

## 4. Crear topics Kafka

Topic principal:

```powershell
docker exec -it order-kafka `
  /opt/kafka/bin/kafka-topics.sh `
  --create `
  --topic order-created `
  --bootstrap-server localhost:9092 `
  --partitions 3 `
  --replication-factor 1
```

Dead Letter Topic:

```powershell
docker exec -it order-kafka `
  /opt/kafka/bin/kafka-topics.sh `
  --create `
  --topic order-created.DLT `
  --bootstrap-server localhost:9092 `
  --partitions 3 `
  --replication-factor 1
```

Verificar topics:

```powershell
docker exec -it order-kafka `
  /opt/kafka/bin/kafka-topics.sh `
  --list `
  --bootstrap-server localhost:9092
```

---

##
