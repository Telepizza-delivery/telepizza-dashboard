# Telepizza Dashboard — `Telepizza-dashboard`

**Responsable:** Clarence
**Asignatura:** Inteligencia Ambiental — Categoría C (seguimiento de calles)

Aplicación cliente del sistema Telepizza. Es una **app de escritorio JavaFX** que permite al operario lanzar pedidos al robot LEGO EV3, ver el mapa de la ciudad pintado en tiempo real, seguir la posición del robot a más de 1 Hz y consultar el estado de cada pedido (recogiendo, recogido, entregado). Convive con [`telepizza-mapa`](https://github.com/Telepizza-delivery/telepizza-mapa) (planificación de rutas, Joseju) y [`telepizza-ev3`](https://github.com/Telepizza-delivery/telepizza-ev3) (robot, Pablo).

---

## Índice

1. [Qué hace la app](#1-qué-hace-la-app)
2. [Estructura del proyecto](#2-estructura-del-proyecto)
3. [Flujo del sistema](#3-flujo-del-sistema)
4. [Cálculo de la odometría](#4-cálculo-de-la-odometría)
5. [Protocolo MQTT](#5-protocolo-mqtt)
6. [Puesta en marcha](#6-puesta-en-marcha)
7. [Cumplimiento de los requisitos mínimos](#7-cumplimiento-de-los-requisitos-mínimos)

---

## 1. Qué hace la app

- Recibe el mapa codificado por MQTT (`map`) y lo **dibuja** en un canvas 5×7 con tiles PNG por tipo de bloque.
- Marca con un borde amarillo y la letra "P" todos los **puntos válidos de recogida/entrega**.
- Permite **crear pedidos mediante clic en el mapa**: el operario pulsa "Seleccionar recogida", hace clic sobre un punto válido (verde), luego "Seleccionar entrega" y clic sobre otro (azul), y finalmente "Añadir pedido". El pedido se publica en `Equipo E/orders` y aparece en la cola FIFO.
- Recibe **odometría** (lista de instrucciones completadas) y **estado** (PEDIDO\_RECIBIDO / RECOGIDO / LISTO) del robot, y los muestra en tiempo real:
  - El círculo naranja del robot se mueve por el mapa.
  - El semáforo de tres luces refleja la fase actual.
  - El pedido pasa al historial al recibir `LISTO`.

---

## 2. Estructura del proyecto

```
Telepizza-dashboard/
└── robot-dashboard/
    ├── pom.xml                       # Build con Maven, JavaFX y Paho MQTT
    └── src/main/
        ├── java/com/example/robot/
        │   ├── RobotApp.java         # Punto de entrada JavaFX
        │   ├── DashboardController.java # UI, layout y handlers MQTT
        │   ├── MqttService.java      # Conexión, suscripciones y publish
        │   ├── CityMap.java          # Decodifica y modela el mapa
        │   ├── Tile.java             # Tipos de bloque (00-11) y conexiones
        │   ├── MapCanvas.java        # Render del mapa + posición del robot
        │   ├── RobotTracker.java     # Estado (row, col, heading) del robot
        │   └── Order.java            # Modelo de pedido (id, recogida, entrega)
        └── resources/tiles/          # PNGs de cada tipo de bloque
```

---

## 3. Flujo del sistema

Los 11 pasos del guión del equipo, vistos desde la app (★ = participación de Clarence):

```
Broker MQTT
    │
    ├── map  (cada 60 s)
    │      └─► ★ Dibuja el mapa y marca los puntos de recogida/entrega
    │
    ├── Equipo E/orders   ◄── ★ Publish al confirmar un pedido
    │      └─► Joseju calcula la ruta y la encola
    │
    ├── Equipo E/instructions  (Joseju → Pablo)
    │      └─► Pablo ejecuta
    │
    ├── Equipo E/odometry  ► ★ Lista de instrucciones completadas, cada 1 s
    │      └─► ★ RobotTracker recalcula la casilla y MapCanvas la pinta
    │
    └── Equipo E/status   ► ★ PEDIDO_RECIBIDO / RECOGIDO / LISTO
           └─► ★ Semáforo de tres luces + historial al recibir LISTO
```

---

## 4. Cálculo de la odometría

El robot es deliberadamente "tonto": no sabe en qué casilla está. Solo informa de qué instrucciones ya ha completado. La posición la calcula esta app.

`RobotTracker` aplica las instrucciones completadas a una posición/orientación inicial conocida:

- **Casilla inicial:** siempre `(6, 0)` (esquina inferior izquierda — definida en el enunciado).
- **Orientación inicial:** se deduce del tipo de bloque de `(6, 0)`:
  - Si el bloque conecta hacia arriba (vertical) → mira al **norte**.
  - Si conecta hacia la derecha (horizontal) → mira al **este**.
- A partir de ahí cada `MOVE` avanza una casilla, cada `TURN_LEFT`/`TURN_RIGHT` rota el heading 90°, y `TURN_BACK` rota 180°. `PICK_UP`, `DELIVER` y `STRAIGHT` no afectan a la posición.

El cálculo es **idempotente**: cada vez que llega un nuevo mensaje de odometría se reaplica toda la lista desde el snapshot inicial, por lo que un mensaje duplicado o perdido no rompe el estado.

Al recibir `LISTO`, el tracker hace **snapshot** de la posición actual: el siguiente pedido empezará a aplicarse desde ahí (el robot no se ha movido, pero el contador de instrucciones de Pablo se resetea con cada pedido nuevo).

---

## 5. Protocolo MQTT

### Broker

| Ajuste | Valor |
|---|---|
| IP | Variable de entorno `IP_ADDRESS_SERVER` (por defecto `192.168.137.2`) |
| Puerto | Variable de entorno `PORT_SERVER` (por defecto `1883`) |

La IP se puede sobreescribir en tiempo de ejecución sin recompilar:

```bash
IP_ADDRESS_SERVER=192.168.1.122 PORT_SERVER=1883 mvn javafx:run
```

### Topics

| Topic | Dirección | Formato |
|---|---|---|
| `map` | servidor → app | Cadena de 70 caracteres (5×7 bloques × 2 dígitos) |
| `Equipo E/orders` | app → mapa | `{"id":"ORD-xxx","pickup":[r,c],"delivery":[r,c]}` |
| `Equipo E/odometry` | robot → app | `{"instructions":["MOVE","TURN_LEFT",...]}` (cada ≥1 Hz) |
| `Equipo E/status` | robot → app | `PEDIDO_RECIBIDO` / `RECOGIDO` / `LISTO` (texto plano, JSON-string o JSON objeto) |

La app **no** se suscribe a `Equipo E/instructions`: no necesita conocer la ruta prevista, le basta con casilla inicial + orientación inicial + instrucciones completadas (ver sección 4).

---

## 6. Puesta en marcha

### Requisitos
- Java 21+ (LTS)
- Maven 3.9+
- Broker MQTT activo (Mosquitto o equivalente). La IP se configura con la variable de entorno `IP_ADDRESS_SERVER`.

### Build y ejecución

```bash
cd Telepizza-dashboard/robot-dashboard
mvn javafx:run
```

El plugin `javafx-maven-plugin` arranca directamente la clase `com.example.robot.RobotApp`. La conexión MQTT se establece automáticamente en `RobotApp.start()` después de mostrar la ventana. Al cerrar la ventana, `RobotApp.stop()` invoca `DashboardController.stopMqtt()` para cerrar la conexión limpiamente.

### Uso

1. Esperar a que aparezca el mapa (al recibir el primer `map` MQTT). Los puntos válidos de recogida/entrega se marcan con borde amarillo y letra "P".
2. Pulsar **Seleccionar recogida** y hacer clic sobre un punto válido en el mapa (se resalta en verde).
3. Pulsar **Seleccionar entrega** y hacer clic sobre otro punto válido (se resalta en azul). Tras fijar la recogida, el modo avanza automáticamente a entrega.
4. Pulsar **Añadir pedido**. Se publica en `Equipo E/orders` y el pedido aparece en la cola FIFO de la columna derecha.
5. Repetir para pedidos adicionales (cola sin límite).
6. Observar el robot moverse por el mapa, el semáforo cambiar de color y el historial llenarse al completar entregas.

---

## 7. Cumplimiento de los requisitos mínimos

Del enunciado del proyecto:

- **R1 — Lanzar un pedido y poner otro en cola:** ✅ Formulario con selección por clic en mapa + cola FIFO en el panel derecho. Cualquier número de pedidos válidos se acepta.
- **R2 — Mostrar odometría a ≥ 1 Hz:** ✅ El robot publica cada 1 s y el tracker recalcula la casilla inmediatamente.
- **R3 — Recoger en A y entregar en B dos veces:** ✅ Soportado a nivel de protocolo y UI; depende del subsistema robot.

---

*Última actualización: 18-05-2026*
