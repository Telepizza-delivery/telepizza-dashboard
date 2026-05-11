# Resumen de Comunicación MQTT
### Dashboard del Robot — Proyecto de Inteligencia Ambiental

---

## Broker

| Ajuste | Valor |
|---|---|
| IP | `192.168.0.108` |
| Puerto | `1883` |
| Protocolo | MQTT v3 |

---

## Resumen de Topics

| Topic | Dirección | Quién lo envía |
|---|---|---|
| `map` | → Dashboard | Externo / broker (cada 60s) |
| `robot/position` | → Dashboard | **Robot** |
| `robot/order/status` | → Dashboard | **Robot** |
| `EquipoE/orders` | → Robot | Dashboard |

---

## 1. El robot envía su posición → `robot/position`

El dashboard escucha este topic para mover el punto del robot en el mapa.

**Formato:** JSON

```json
{"row": 2, "col": 3}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `row` | entero | Índice de fila en la cuadrícula del mapa (base 0, arriba = 0) |
| `col` | entero | Índice de columna en la cuadrícula del mapa (base 0, izquierda = 0) |

**QoS:** 0 (enviar y olvidar — las actualizaciones de posición son frecuentes, perder una no es problema)

El robot debe publicar esto cada vez que se mueva a una nueva celda.

---

## 2. El robot envía el progreso del pedido → `robot/order/status`

El dashboard escucha este topic para actualizar la barra de progreso y el panel del pedido actual.

**Formato:** JSON

```json
{
  "id": "ORD-042",
  "progress": 0.6,
  "status": "IN_PROGRESS",
  "pickupRow": 0,
  "pickupCol": 0,
  "deliveryRow": 3,
  "deliveryCol": 4
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | cadena | ID del pedido, p. ej. `"ORD-042"` |
| `progress` | decimal | 0.0 (sin empezar) → 1.0 (completado) |
| `status` | cadena | `"IN_PROGRESS"`, `"COMPLETED"` o `"PENDING"` |
| `pickupRow` | entero | Fila del punto de recogida |
| `pickupCol` | entero | Columna del punto de recogida |
| `deliveryRow` | entero | Fila del punto de entrega |
| `deliveryCol` | entero | Columna del punto de entrega |

**QoS:** 0

---

## 3. El dashboard envía un nuevo pedido → `EquipoE/orders`

Cuando un usuario añade un pedido en la interfaz del dashboard, se publica en este topic. El robot debe suscribirse aquí para recibir su próximo trabajo.

**Formato:** JSON

```json
{
  "id": "ORD-001",
  "pickupRow": 1,
  "pickupCol": 2,
  "deliveryRow": 4,
  "deliveryCol": 3
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | cadena | ID único del pedido |
| `pickupRow` | entero | Fila de la celda de recogida |
| `pickupCol` | entero | Columna de la celda de recogida |
| `deliveryRow` | entero | Fila de la celda de entrega |
| `deliveryCol` | entero | Columna de la celda de entrega |

**QoS:** 1 (al menos una vez — los pedidos no se pueden perder)

---

## 4. Topic del mapa → `map` *(el robot no necesita enviar esto)*

El dashboard recibe el diseño del mapa de la ciudad como una cadena de códigos de 2 dígitos cada 60 segundos. El robot no necesita publicar aquí — esto llega desde otro lugar (p. ej. un servidor de mapas o un script de prueba).

**Formato:** cadena de texto, p. ej. `"010203..."` — cada par de dígitos es el código de una celda, leído de izquierda a derecha y de arriba a abajo. El mapa tiene 5 columnas × 7 filas = 70 dígitos en total.

---

## Lista de verificación para el equipo del robot

- [ ] Conectarse al broker en `192.168.0.108:1883`
- [ ] **Suscribirse** a `EquipoE/orders` (QoS 1) para recibir pedidos
- [ ] **Publicar** en `robot/position` (QoS 0) en cada movimiento de celda: `{"row": F, "col": C}`
- [ ] **Publicar** en `robot/order/status` (QoS 0) cuando cambie el estado del pedido
- [ ] Enviar `"status": "COMPLETED"` y `"progress": 1.0` cuando un pedido esté completado

---

*Generado a partir de: `MqttService.java` y `DashboardController.java`*
