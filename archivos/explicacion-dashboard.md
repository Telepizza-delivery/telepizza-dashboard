# Guía de Defensa — Telepizza Dashboard

> Documento exhaustivo de estudio para la defensa académica del módulo **Clarence** (dashboard JavaFX) del proyecto Telepizza — Inteligencia Ambiental, Categoría C.
>
> Cada archivo se explica con: resumen para no técnicos, dependencias, desglose de cada componente y preguntas trampa con respuesta blindada.

---

## Índice

1. [Visión general del ecosistema](#visión-general-del-ecosistema)
2. [`pom.xml` — Configuración Maven](#1-pomxml--configuración-maven)
3. [`module-info.java` — Descriptor de módulo Java](#2-module-infojava--descriptor-de-módulo-java)
4. [`RobotApp.java` — Punto de entrada JavaFX](#3-robotappjava--punto-de-entrada-javafx)
5. [`MqttService.java` — Capa de comunicaciones MQTT](#4-mqttservicejava--capa-de-comunicaciones-mqtt)
6. [`Tile.java` — Modelo de bloque del mapa](#5-tilejava--modelo-de-bloque-del-mapa)
7. [`CityMap.java` — Decodificación y modelado del mapa](#6-citymapjava--decodificación-y-modelado-del-mapa)
8. [`RobotTracker.java` — Cálculo de posición del robot](#7-robottrackerjava--cálculo-de-posición-del-robot)
9. [`MapCanvas.java` — Render gráfico del mapa](#8-mapcanvasjava--render-gráfico-del-mapa)
10. [`Order.java` — Modelo de pedido](#9-orderjava--modelo-de-pedido)
11. [`DashboardController.java` — Orquestador de la UI y handlers MQTT](#10-dashboardcontrollerjava--orquestador-de-la-ui-y-handlers-mqtt)
12. [Anexo — Preguntas globales del profesor](#anexo--preguntas-globales-del-profesor)

---

## Visión general del ecosistema

El proyecto Telepizza simula un reparto de pizzas con un robot LEGO EV3 sobre un mapa-ciudad de 5×7 bloques. Está dividido en tres aplicaciones que se comunican exclusivamente por **MQTT**:

- **`telepizza-mapa`** (Joseju): publica el mapa codificado y planifica rutas en forma de listas de instrucciones.
- **`telepizza-ev3`** (Pablo): firmware del robot que ejecuta las instrucciones y reporta avance.
- **`telepizza-dashboard`** (Clarence, este repo): aplicación de escritorio JavaFX que sirve de **puesto de mando del operario**. Lanza pedidos, pinta el mapa, ubica al robot en tiempo real y muestra el estado de cada entrega mediante un semáforo de tres luces.

El módulo Clarence es deliberadamente **stateless en el robot**: el firmware no sabe en qué casilla está, sólo informa de qué instrucciones ha completado. La posición la **reconstruye el dashboard** a partir de (casilla inicial, orientación inicial, lista de instrucciones completadas). Esta separación es clave en la defensa: el dashboard es el "cerebro espacial" del sistema.

---

## 1. `pom.xml` — Configuración Maven

### 1.1 Resumen ejecutivo

Imagina que el proyecto es una receta de cocina. Este archivo es la **lista de la compra**: dice qué ingredientes (librerías) hay que comprar, qué horno (versión de Java) hay que usar y cómo encender la cocina (qué comando ejecuta la app). Sin él, Maven no sabría construir nada.

### 1.2 Arquitectura y dependencias

```xml
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>
<javafx.version>21.0.2</javafx.version>
```

- **Java 21 (LTS)**: versión obligatoria para usar `switch expressions` con patrones, `var` y bloques `->` (que aparecen en `RobotTracker` y `DashboardController`).
- **`javafx-controls` 21.0.2**: aporta `BorderPane`, `VBox`, `ListView`, `Button`, `Label`. Toda la UI declarativa.
- **`javafx-fxml` 21.0.2**: incluida por completitud; el módulo permite cargar FXML aunque la UI se construye programáticamente.
- **`org.eclipse.paho.client.mqttv3` 1.2.5**: cliente MQTT v3.1.1, ligero, sin dependencias transitivas pesadas. Es el estándar de facto en JVM para MQTT.
- **`javafx-maven-plugin` 0.0.8**: orquesta `mvn javafx:run`, configurando automáticamente el `--module-path` y los `--add-modules` que JavaFX necesita en runtime.

### 1.3 Desglose de componentes

| Bloque XML | Propósito | Detalle |
|---|---|---|
| `<modelVersion>4.0.0</modelVersion>` | Identifica el POM como Maven 4. | Es obligatorio en todos los `pom.xml`. |
| `<groupId>com.example</groupId>` | Espacio de nombres del organismo. | Académico — en producción sería el dominio invertido real. |
| `<artifactId>robot-dashboard</artifactId>` | Nombre del JAR resultante. | Coincide con la carpeta. |
| `<packaging>jar</packaging>` | Empaquetado JAR (no WAR ni POM). | App de escritorio. |
| `<properties>` | Variables reutilizables. | Centraliza la versión de JavaFX. |
| `<dependencies>` | Lista de librerías que se descargan. | Maven resuelve el grafo desde Maven Central. |
| `<build><plugins>` | Plugins que se ejecutan en el ciclo de vida. | Solo `javafx-maven-plugin` para ejecutar la app. |
| `<mainClass>` | Clase con el `main(String[])`. | `com.example.robot.RobotApp`. |

### 1.4 Preguntas trampa del profesor

**P1 — "¿Por qué Java 21 y no Java 8 o Java 17?"**
> Java 21 es el LTS vigente y permite usar **switch expressions con flecha** (`case X -> ...`) y `switch` sobre `enum` sin `break`, que aparecen en `RobotTracker` y `DashboardController`. Java 8 no soporta esa sintaxis y Java 17 sí, pero 21 es la versión soportada a largo plazo en el aula. Además JavaFX 21 está compilado contra esa versión.

**P2 — "¿Por qué Paho y no, por ejemplo, HiveMQ MQTT Client?"**
> Paho v3 es **el cliente de referencia** del Eclipse Foundation, está documentado en los apuntes, sólo trae una dependencia (`slf4j-api`), y se integra trivialmente con un único `MqttCallback`. HiveMQ es excelente pero asíncrono basado en `CompletableFuture`, lo que complicaría innecesariamente un dashboard que recibe pocos mensajes por segundo.

**P3 — "El POM no genera un fat-JAR. ¿Cómo se distribuiría la app a otra máquina?"**
> Correcto, el comentario `<!-- Fat JAR with all dependencies -->` muestra que está previsto pero no implementado. Para distribuir se añadiría el `maven-shade-plugin` o `jlink`/`jpackage`. En el contexto académico la app se ejecuta con `mvn javafx:run`, que resuelve el classpath en local; en producción se empaquetaría con `jpackage` para generar un instalador nativo que incluya un runtime Java embebido.

---

## 2. `module-info.java` — Descriptor de módulo Java

### 2.1 Resumen ejecutivo

Es la **portería** del módulo. Java 9+ usa un sistema de módulos: cada módulo declara explícitamente con quién habla y qué deja ver al exterior. Este archivo dice "necesito JavaFX y MQTT, y dejo a JavaFX leer mis clases por reflexión".

### 2.2 Arquitectura y dependencias

```java
module com.example.robot {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.eclipse.paho.client.mqttv3;

    opens com.example.robot to javafx.fxml;
    exports com.example.robot;
}
```

### 2.3 Desglose de componentes

- **`module com.example.robot`**: declara el módulo. El nombre debe ser único en el `module-path`.
- **`requires javafx.controls`**: importa los controles UI (Button, Label…).
- **`requires javafx.fxml`**: importa el cargador de FXML. Aunque la app construye la UI programáticamente, JavaFX lo necesita para inicializar correctamente el toolkit.
- **`requires org.eclipse.paho.client.mqttv3`**: importa el cliente Paho.
- **`opens com.example.robot to javafx.fxml`**: permite a `javafx.fxml` acceder por reflexión a las clases del paquete. Necesario para que algunas factorías internas de JavaFX funcionen.
- **`exports com.example.robot`**: hace visible el paquete a otros módulos (al menos a `javafx.graphics`, que lanza la `Application`).

### 2.4 Preguntas trampa del profesor

**P1 — "¿Qué pasaría si quitaras `opens com.example.robot to javafx.fxml`?"**
> Si en algún momento se introdujera un `FXMLLoader`, lanzaría `IllegalAccessException` al intentar inyectar campos `@FXML` por reflexión. Como protección de futuro y porque la dependencia `javafx-fxml` ya está en el POM, lo mantenemos abierto.

**P2 — "¿Por qué exportas el paquete?"**
> Porque `javafx.graphics` instancia `RobotApp` por reflexión cuando llamamos a `Application.launch(args)`. Sin `exports`, lanzaría `IllegalAccessError`.

**P3 — "¿Es seguro abrir todo el paquete al sistema de módulos?"**
> En producción se usaría `opens com.example.robot.ui to javafx.fxml` para limitar la apertura a un subpaquete con sólo controladores FXML. En el contexto académico de una única clase de UI, la diferencia es nula y simplifica el módulo.

---

## 3. `RobotApp.java` — Punto de entrada JavaFX

### 3.1 Resumen ejecutivo

Es el **interruptor de la luz** de la aplicación. Cuando ejecutas el programa, este archivo es lo primero que se enciende: crea la ventana, pinta dentro el dashboard y arranca la conexión con el broker MQTT.

### 3.2 Arquitectura y dependencias

```java
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
```

- **`Application`**: clase base de toda aplicación JavaFX. Su método estático `launch()` arranca el toolkit gráfico, crea el hilo JavaFX Application Thread y llama a `start()`.
- **`Scene`** + **`Stage`**: el modelo "ventana → escena → grafo de nodos" típico de JavaFX.

### 3.3 Desglose de componentes

#### Método `start(Stage primaryStage)`

1. `new DashboardController()` — instancia el controlador (no toca la UI aún).
2. `controller.buildUI()` — construye el grafo de nodos. Devuelve el `BorderPane` raíz.
3. `new Scene(..., 900, 700)` — escena de 900×700 px.
4. `primaryStage.setTitle("Robot Delivery Dashboard")`.
5. `primaryStage.show()` — hace visible la ventana.
6. `controller.startMqtt()` — arranca la conexión MQTT **después** de `show()`, para que los handlers asíncronos encuentren la UI ya construida.

#### Método `stop()`

Se invoca cuando el usuario cierra la ventana. Llama a `controller.stopMqtt()`, que delega en `MqttService.disconnect()` para cerrar la conexión limpiamente antes de que la JVM termine.

#### Método `main(String[] args)`

Una línea: delega en `launch(args)`. Necesario sólo si se ejecuta el JAR directamente.

### 3.4 Preguntas trampa del profesor

**P1 — "¿Por qué llamas a `startMqtt()` después de `show()` y no antes?"**
> Porque `MqttService` empuja mensajes a los handlers, que actualizan la UI con `Platform.runLater(...)`. Si arrancara antes de `show()`, podríamos recibir un mensaje cuando aún no existen los `Label` o `ListView`, provocando `NullPointerException`. Arrancarlo después garantiza que el grafo escena ya está construido.

**P2 — "¿Qué hace exactamente `Application.launch(args)`?"**
> Inicializa el JavaFX runtime, crea el **JavaFX Application Thread**, instancia `RobotApp` por reflexión, llama a `init()` (vacío por defecto), luego a `start(primaryStage)` y se queda bloqueado hasta que se cierra la última ventana. Después llama a `stop()` y termina.

**P3 — "¿Por qué `stop()` llama a `controller.stopMqtt()` en lugar de confiar en el ShutdownHook?"**
> El ShutdownHook de `MqttService` es un seguro de último recurso (p. ej. kill -9). Llamar explícitamente a `stopMqtt()` en `stop()` garantiza un cierre MQTT ordenado (DISCONNECT MQTT antes de cerrar el socket TCP) dentro del ciclo de vida normal de JavaFX.

---

## 4. `MqttService.java` — Capa de comunicaciones MQTT

### 4.1 Resumen ejecutivo

Es la **antena de radio** del dashboard. Se conecta al servidor MQTT (el "centro de mensajería"), escucha tres canales (mapa, odometría, estado del robot) y publica en un cuarto canal (los pedidos). Cuando llega un mensaje, llama a la función adecuada del controlador.

### 4.2 Arquitectura y dependencias

```java
import java.util.function.Consumer;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
```

- **`Consumer<String>`**: interfaz funcional usada para los callbacks.
- **`MqttClient`**: cliente síncrono Paho.
- **`MqttConnectOptions`**: configura keep-alive, reconexión automática, etc.
- **`MqttCallbackExtended`**: interfaz ampliada con `connectComplete` (llamado también en reconexiones).
- **`MemoryPersistence`**: almacenamiento en memoria de mensajes QoS > 0 pendientes; evita escribir archivos en disco.

### 4.3 Desglose de componentes

#### Constantes

```java
public static final String BROKER_URL =
    "tcp://" + System.getenv().getOrDefault("IP_ADDRESS_SERVER", "192.168.137.2")
             + ":"
             + System.getenv().getOrDefault("PORT_SERVER", "1883");
```

La IP y el puerto se leen de **variables de entorno** en tiempo de arranque. Si no están definidas, se usan los valores por defecto `192.168.137.2:1883`. Esto permite cambiar el broker sin recompilar:

```bash
IP_ADDRESS_SERVER=192.168.1.122 PORT_SERVER=1883 mvn javafx:run
```

```java
private static final String CLIENT_ID =
    System.getenv().getOrDefault("CLIENT_ID" + System.currentTimeMillis(),
                                 "RobotDashboard-JavaFX-" + System.currentTimeMillis());
```

El sufijo con milisegundos hace el ID único por instancia y evita choques con sesiones anteriores en el broker.

```java
public static final String TOPIC_MAP       = "map";
public static final String TOPIC_ORDERS    = "Equipo E/orders";
public static final String TOPIC_ODOMETRY  = "Equipo E/odometry";
public static final String TOPIC_STATUS    = "Equipo E/status";
```

#### Campos y setters

Cuatro `Consumer<String>`: uno por topic de entrada y uno para mensajes de estado de la conexión. El patrón observer desacopla `MqttService` de la UI.

#### Método `connect()`

1. `if (client != null && client.isConnected()) return;` — idempotencia.
2. Construye `MqttClient` con `MemoryPersistence`.
3. `MqttConnectOptions`: `cleanSession=true`, timeout 10 s, keep-alive 30 s, `automaticReconnect=true`.
4. Registra `MqttCallbackExtended`:
   - `connectComplete(reconnect, uri)` → notifica al UI si fue reconexión o conexión inicial.
   - `connectionLost(cause)` → notifica "conexión perdida".
   - `messageArrived(topic, message)` → demultiplexa por topic e invoca el `Consumer` correspondiente.
   - `deliveryComplete(token)` → vacío.
5. `client.connect(opts)`.
6. Suscripción: `TOPIC_MAP` con QoS 1; `TOPIC_ODOMETRY` y `TOPIC_STATUS` con QoS 0.
7. `Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect))` — cierre de emergencia.

#### Método `publish(String topic, String payload)`

- Verifica conexión; si no hay, lanza `MqttException(REASON_CODE_CLIENT_NOT_CONNECTED)`.
- Crea `MqttMessage` con QoS 1 (los pedidos deben llegar).
- `client.publish(topic, message)`.

### 4.4 Preguntas trampa del profesor

**P1 — "¿Por qué los handlers MQTT no actualizan la UI directamente?"**
> Porque Paho llama a `messageArrived` desde un **hilo propio de Paho** (no el JavaFX Application Thread). JavaFX exige que cualquier modificación del grafo escena ocurra en el JAT, o lanza `IllegalStateException`. Por eso `DashboardController` envuelve cada handler en `Platform.runLater(...)`.

**P2 — "¿Qué pasaría si el broker se cae a mitad de demo?"**
> `setAutomaticReconnect(true)` hace que Paho intente reconectar con back-off exponencial. `connectionLost` notifica el estado al UI. Si el broker vuelve, las suscripciones se restablecen automáticamente porque Paho reenvía los `subscribe` en cada reconexión cuando `cleanSession=true`. El `connectComplete` con `reconnect=true` notifica al UI que volvió la conexión.

**P3 — "¿Por qué QoS 1 en mapa y QoS 0 en odometría?"**
> El mapa llega cada 60 s; perderlo deja la app sin casillas de recogida/entrega durante un minuto entero. QoS 1 garantiza recepción. La odometría llega cada segundo y el cálculo es idempotente: perder un mensaje no altera el resultado porque el siguiente reaplica toda la lista. QoS 0 minimiza overhead.

**P4 — "¿Por qué se usan variables de entorno para la IP del broker?"**
> Porque en el laboratorio la IP del punto de acceso o del portátil que actúa de broker puede cambiar de sesión a sesión. Con variables de entorno no hay que recompilar; basta con exportar la variable antes de lanzar. Es una práctica de los 12-factor apps que evita hardcodear credenciales o direcciones en el código fuente.

---

## 5. `Tile.java` — Modelo de bloque del mapa

### 5.1 Resumen ejecutivo

Cada casilla del mapa (5 columnas × 7 filas = 35 casillas) es de un tipo: edificio o calle. Si es calle, indica hacia dónde conecta (norte, sur, este, oeste). Este archivo define ese vocabulario: 12 tipos numerados del 0 al 11, igual que las **Figuras 9-20 del enunciado**.

### 5.2 Arquitectura y dependencias

No importa nada. Es una clase autocontenida. Usa `enum` interno (`Tile.Id`) y switch expressions de Java 14+.

### 5.3 Desglose de componentes

#### Enum `Tile.Id`

```java
public enum Id {
    BUILDING(0), ROAD_LR(1), ROAD_UD(2), ROAD_UR(3),
    ROAD_RD(4),  ROAD_DL(5), ROAD_LU(6), ROAD_LUR(7),
    ROAD_URD(8), ROAD_RDL(9), ROAD_DLU(10), ROAD_ALL(11);
    ...
}
```

- **Convención de nombres:** L/R/U/D = Left/Right/Up/Down.
- **`fromCode(int)`**: busca linealmente el `Id`. Si no existe → `IllegalArgumentException`.

#### Constructor `Tile(int code)`

Convierte el código a enum y asigna las cuatro variables booleanas de conexión mediante un `switch` expression. El `default` cubre el caso teóricamente inalcanzable y permite que el compilador valide la asignación de campos `final`.

#### Método `isPickupOrDelivery()`

`!isBuilding() && connectionCount() == 1`. Detecta el **tipo** (terminal por su forma), sin comprobar vecinos. El chequeo más estricto contra el grafo real lo hace `CityMap.isPickupPoint`.

### 5.4 Preguntas trampa del profesor

**P1 — "¿Por qué un enum y no constantes `int` estáticas?"**
> Type safety. Con `enum` el compilador prohíbe pasar un código inválido, `switch` es exhaustivo y el IDE autocompleta los 12 valores.

**P2 — "¿Por qué guardas las cuatro variables `connectsX` en lugar de calcularlas cada vez desde el `Id`?"**
> Por rendimiento y legibilidad. `MapCanvas.drawTile` consulta esas cuatro variables varias veces por tile y por frame. Cachearlas en el constructor evita un `switch` por consulta.

**P3 — "¿Por qué dejas un `default` que asigna todo a `false` si nunca se va a ejecutar?"**
> Porque sin él, el compilador no puede demostrar exhaustividad en presencia de campos `final` en un `switch` statement (no expresión). El `default` me evita una segunda lectura del `switch` y deja el código robusto si alguien añade un código nuevo al enum sin actualizar el constructor.

---

## 6. `CityMap.java` — Decodificación y modelado del mapa

### 6.1 Resumen ejecutivo

Cuando llega el mapa por MQTT, llega como una **cadena de 70 caracteres** (5 columnas × 7 filas × 2 dígitos por casilla). Este archivo es el **traductor**: convierte ese texto en una rejilla 7×5 de objetos `Tile` y detecta qué casillas pueden ser puntos de recogida/entrega.

### 6.2 Desglose de componentes

#### Constructor `CityMap(String mqttPayload, int cols, int rows)`

1. Limpia whitespace del payload.
2. Valida longitud exacta (`rows * cols * 2 = 70` chars).
3. Primera pasada: rellena `grid[row][col]` con `new Tile(code)` usando offset `(row * cols + col) * 2`.
4. Segunda pasada: para cada celda no-edificio llama a `isPickupPoint(row, col)`.

#### Método privado `isPickupPoint(int row, int col)`

Comprueba cuatro condiciones por dirección: el tile conecta en esa dirección, el vecino existe dentro del mapa, el vecino no es edificio, y el vecino conecta de vuelta. Si **exactamente una** dirección cumple todo, el tile es punto válido.

### 6.3 Preguntas trampa del profesor

**P1 — "¿Por qué validar conexiones recíprocas en `isPickupPoint`? Bastaría con `tile.connectionCount() == 1`."**
> No. `Tile.isPickupOrDelivery()` sólo mira la forma del bloque. `isPickupPoint` valida que el vecino existe y reciproca la conexión, garantizando que los puntos mostrados son **alcanzables** por el robot.

**P2 — "¿Qué pasa si llega un mapa con un código inválido, por ejemplo `99`?"**
> `new Tile(99)` lanza `IllegalArgumentException`. La excepción se propaga a `DashboardController.handleMapPayload`, que la captura y muestra "Error al parsear mapa" en la barra de estado. El mapa anterior sigue pintado.

**P3 — "¿Por qué dos pasadas en lugar de una?"**
> Porque `isPickupPoint` mira a los vecinos. En una pasada única, al evaluar la celda (0,0) los vecinos aún no existirían en el grid.

---

## 7. `RobotTracker.java` — Cálculo de posición del robot

### 7.1 Resumen ejecutivo

El robot real no sabe en qué casilla está. Sólo sabe qué instrucciones ya ha completado. Este archivo es el **GPS virtual**: parte de una casilla inicial conocida y aplica las instrucciones para deducir dónde está y hacia dónde mira.

### 7.2 Desglose de componentes

#### Enum `Heading`

```java
public enum Heading { N, E, S, W }
```

#### Campos

- `(row, col, heading)` — estado actual.
- `(snapshotRow, snapshotCol, snapshotHeading)` — estado al inicio del pedido actual, actualizado al recibir `LISTO`.

#### Método `applyCompleted(List<String> instructions)`

1. Restaura el estado al snapshot.
2. Itera la lista entera aplicando cada instrucción.

**Idempotencia clave:** ejecutar la misma lista varias veces da el mismo resultado. Con QoS 0, los mensajes de odometría pueden duplicarse; el tracker lo absorbe sin error.

#### Método `applyOne(String instr)`

- `MOVE`: avanza una casilla según `heading` (N → `row--`, S → `row++`, E → `col++`, W → `col--`).
- `TURN_LEFT` / `TURN_RIGHT`: giro de 90°.
- `TURN_BACK`: dos giros a la izquierda (180°).
- `PICK_UP`, `DELIVER`, `STRAIGHT` y cualquier otro: ignorados, no afectan la posición.

#### Método `commitSnapshot()`

Congela la posición actual como nuevo punto de partida. Se llama al recibir `LISTO`, porque Pablo resetea el contador de instrucciones del firmware al empezar el siguiente pedido.

### 7.3 Preguntas trampa del profesor

**P1 — "¿Por qué reaplicar la lista completa en cada mensaje en lugar de aplicar sólo el delta nuevo?"**
> Por robustez frente a pérdida y duplicación con QoS 0. Si aplicáramos sólo el delta y se pierde un mensaje, el estado quedaría incorrecto para siempre. La lista es pequeña (pocas decenas de instrucciones por pedido); reaplicarla cada segundo es trivial y da idempotencia gratuita.

**P2 — "¿Por qué `TURN_BACK` se implementa como dos `turnLeft`?"**
> Equivalencia exacta y código más mantenible. La composición de dos rotaciones de 90° es idéntica a un giro de 180°. El coste (dos operaciones de enum) se ejecuta como mucho una vez por segundo y es despreciable.

**P3 — "¿Qué garantiza que `applyCompleted` no se ejecuta concurrentemente?"**
> `DashboardController` envuelve la llamada en `Platform.runLater(...)`, ejecutándose siempre en el JavaFX Application Thread. Un único productor (el handler de odometría de Paho) y un único hilo consumidor (el JAT). Sin contienda, no necesitamos `synchronized`.

**P4 — "¿Qué pasa si el robot publica un `MOVE` que lo llevaría a una casilla edificio?"**
> El tracker es ciego al mapa: actualiza `row`/`col` aritméticamente. Si el robot real respeta el grafo de calles el resultado es correcto. Si publica una instrucción inválida, el dot naranja se dibujaría sobre un edificio: un bug visible en `telepizza-ev3`, no en el dashboard.

---

## 8. `MapCanvas.java` — Render gráfico del mapa

### 8.1 Resumen ejecutivo

Es el **pintor**. Recibe el mapa procesado y dibuja sobre un lienzo: las casillas con sus PNG, los números de fila/columna, el borde amarillo de los puntos de recogida, los overlays de selección (verde para recogida, azul para entrega) y el círculo naranja del robot.

### 8.2 Novedades respecto a versiones anteriores

- **Selección por clic en mapa**: nuevos métodos `setSelectedPickup(row, col)`, `setSelectedDelivery(row, col)` y `clearSelections()` que pinden un overlay semitransparente sobre las celdas seleccionadas y llaman a `redraw()`.
- **Overlay de selección en `redraw()`**: después de pintar los bordes amarillos de los pickup points, pinta (si están definidos) un rectángulo verde semitransparente + borde verde sobre la celda de recogida, y un rectángulo azul semitransparente + borde azul sobre la celda de entrega.

### 8.3 Desglose de componentes

#### Constructor `MapCanvas(double width, double height)`

- Tamaño fijo 420×588 px.
- Llama a `loadTileImages()`: intenta cargar `/tiles/<ID_NAME>.png` para cada `Tile.Id`. Si falta o está corrupto, la celda usa dibujo vectorial de fallback.

#### Método `redraw()`

Orden de pintado:
1. Fondo gris claro (zona de márgenes).
2. Si no hay mapa: texto centrado "Esperando mapa MQTT...".
3. Tiles (imagen PNG o vectorial por fallback) en todos los `(row, col)`.
4. Rejilla negra encima de los tiles.
5. **Bordes amarillos** encima de la rejilla, alrededor de cada pickup/delivery tile.
6. **Overlay verde** semitransparente + borde verde sobre la celda de recogida seleccionada (si hay).
7. **Overlay azul** semitransparente + borde azul sobre la celda de entrega seleccionada (si hay).
8. Números de columna (margen superior) y fila (margen izquierdo).
9. Círculo naranja del robot si `robotRow >= 0`.

#### `setSelectedPickup(int, int)` / `setSelectedDelivery(int, int)` / `clearSelections()`

Actualizan las coordenadas de selección y llaman a `redraw()`. Llamados desde `DashboardController.handleMapClick`.

### 8.4 Preguntas trampa del profesor

**P1 — "¿Por qué `Canvas` y no `GridPane` con `ImageView` en cada celda?"**
> `GridPane` mantendría 35 nodos en el grafo escena. Para 35 celdas que se repintan en bloque, `Canvas` es 5-10× más eficiente: una sola llamada al pintor del sistema. Además permite superponer imagen + overlays semitransparentes sin Z-orden complejo.

**P2 — "¿Por qué dibujas los bordes amarillos después de la rejilla negra?"**
> Para que sean visibles. Si los dibujara antes, las líneas negras los taparían parcialmente en las esquinas. Al pintarlos después con `lineWidth = 3.0`, dominan visualmente.

**P3 — "¿Por qué los overlays de selección van después de los bordes amarillos?"**
> Para que el overlay de selección siempre sea la capa más visible. Un tile puede ser a la vez pickup point (borde amarillo) y estar seleccionado como recogida (overlay verde): el verde encima del amarillo deja claro cuál es la selección activa.

**P4 — "¿Por qué `EnumMap` y no `HashMap`?"**
> Porque la clave es un `enum` con 12 valores. `EnumMap` está implementado como un array indexado por `ordinal()`, sin hashing ni colisiones. Es más rápido y consume menos memoria.

---

## 9. `Order.java` — Modelo de pedido

### 9.1 Resumen ejecutivo

POJO que representa un pedido: id (`ORD-001`), punto de recogida `(row, col)`, punto de entrega `(row, col)`, estado y progreso. Está disponible para un posible enriquecimiento futuro de la UI (vista de progreso, filtrado del historial).

### 9.2 Preguntas trampa del profesor

**P1 — "¿Por qué `Order` existe si `DashboardController` usa `List<String>` para la cola?"**
> Es un modelo previsto para enriquecer la UI futura. En la implementación actual el flujo MQTT es asíncrono y simple, pero `Order` documenta la intención del dominio y facilita un refactor a una `ObservableList<Order>` con `CellFactory`.

**P2 — "¿Por qué `progress` es `double` y no un porcentaje entero?"**
> Porque casa naturalmente con `ProgressBar.setProgress(double)` de JavaFX, que recibe valores `[0.0, 1.0]`. Evita la conversión en el binding.

---

## 10. `DashboardController.java` — Orquestador de la UI y handlers MQTT

### 10.1 Resumen ejecutivo

Es el **director de orquesta**. Construye el layout de tres columnas del dashboard y conecta cada mensaje MQTT con su efecto visual. Gestiona la selección de puntos de recogida/entrega por clic en el mapa.

### 10.2 Arquitectura del layout

```
┌─────────────────────┬──────────────────┬──────────────────┐
│  MapCanvas (5×7)    │  Pedido actual   │  Cola (ListView) │
│  + leyenda          │  Gestión pedidos │  Historial       │
│  + estado MQTT      │  (clic en mapa)  │  Info MQTT       │
│                     │  Semáforo estado │                  │
└─────────────────────┴──────────────────┴──────────────────┘
│  Barra de estado + raw map                                 │
```

- **Izquierda (`setLeft`)**: `MapCanvas` + leyenda de colores + estado MQTT.
- **Centro (`setCenter`)**: panel pedido actual, formulario de gestión (selección por clic), semáforo.
- **Derecha (`setRight`)**: cola FIFO, historial, info MQTT.

### 10.3 Novedades: selección por clic en mapa

#### Enum `ClickMode`

```java
private enum ClickMode { PICKUP, DELIVERY, NONE }
```

Controla qué tipo de punto se está seleccionando. Tras fijar la recogida, el modo avanza automáticamente a `DELIVERY`.

#### Campos de selección

```java
private int selPickupRow = -1, selPickupCol = -1;
private int selDeliveryRow = -1, selDeliveryCol = -1;
```

#### Método `buildQueuePanelNoQueue()` (formulario, columna central)

- Dos botones: **Seleccionar recogida** y **Seleccionar entrega**, que activan `clickMode`.
- Dos etiquetas de selección: muestran "— toca un punto en el mapa" o "✔ (row,col)" con color verde/azul.
- Botón **Añadir pedido**: valida que ambas selecciones estén definidas y sean distintas, construye el JSON y publica.
- Etiqueta de estado del formulario: muestra instrucciones al usuario en verde, errores en rojo.

#### Método `handleMapClick(double pixelX, double pixelY)`

Llamado desde `mapCanvas.setOnMouseClicked(...)`.

1. Si `currentMap == null` o `clickMode == NONE`, ignora el clic.
2. Convierte píxeles a `(row, col)` usando la misma aritmética que `MapCanvas.redraw()` (margen 22 px + `cellW/cellH`).
3. Valida que la casilla esté dentro del mapa.
4. Comprueba que la casilla esté en `currentMap.getPickupPoints()`. Si no → error rojo.
5. Si `clickMode == PICKUP`: actualiza `selPickupRow/Col`, llama a `mapCanvas.setSelectedPickup(row, col)`, avanza el modo a `DELIVERY`.
6. Si `clickMode == DELIVERY`: actualiza `selDeliveryRow/Col`, llama a `mapCanvas.setSelectedDelivery(row, col)`, pone el modo a `NONE`.

### 10.4 Desglose de otros componentes

#### Constantes de configuración

```java
private static final int MAP_COLS = 5;
private static final int MAP_ROWS = 7;
private static final int START_ROW = 6;
private static final int START_COL = 0;
```

#### Campos de estado

- `currentMap` / `tracker`: actualizados al recibir el mapa.
- `queueItems` (ObservableList) + `queuedIds` (List): en paralelo para la cola — `queueItems` contiene la descripción visual, `queuedIds` el id puro para promoverlo a activo.
- `activeOrderId`: el pedido en curso; `null` si no hay ninguno.
- `mqtt`: única instancia de `MqttService`.

#### `startMqtt()` / `stopMqtt()`

- `startMqtt()`: registra los cuatro callbacks y lanza `mqtt.connect()` en un hilo `"mqtt-connect"` (bloqueo TCP fuera del JAT).
- `stopMqtt()`: llama a `mqtt.disconnect()`. Invocado desde `RobotApp.stop()`.

#### `handleMapPayload(String)`

Parsea `CityMap` en el hilo MQTT (fuera del JAT). Luego, en `Platform.runLater`:
- Actualiza `currentMap`, canvas y tracker.
- Inicializa el robot en `(START_ROW, START_COL)` con orientación deducida del bloque.

#### `handleOdometryPayload(String)`

Parsea la lista de instrucciones con `parseInstructionsArray`. En `Platform.runLater`: `tracker.applyCompleted(done)` y actualiza canvas y etiqueta de progreso.

#### `handleStatusPayload(String)`

Parser robusto que acepta tres formatos de `estado`:
1. JSON objeto `{"status":"LISTO"}`.
2. JSON-string `"LISTO"` (con comillas externas).
3. Texto plano `LISTO`.

Luego invoca `applyStatus(estado)` en el JAT.

#### `applyStatus(String estado)`

- **`PEDIDO_RECIBIDO`**: promueve el primer `queuedIds` a `activeOrderId`, retira de la cola visual, enciende la luz naranja del semáforo.
- **`RECOGIDO`**: enciende la luz azul.
- **`LISTO`**: enciende la luz verde, añade el pedido al historial, llama a `tracker.commitSnapshot()`, limpia el panel "Pedido actual".

### 10.5 Preguntas trampa del profesor

**P1 — "¿Por qué sustituiste los ComboBox por selección directa en el mapa?"**
> Porque los ComboBox mostraban las coordenadas como texto (`"1,2"`) y el operario tenía que saber de memoria qué casilla era cuál. El clic directo en el mapa es más intuitivo: el operario ve los puntos amarillos y toca el que quiere. Además evita que el operario seleccione un punto y el otro se les cambie visualmente sin que lo noten.

**P2 — "¿Por qué el modo avanza automáticamente de PICKUP a DELIVERY tras fijar la recogida?"**
> Para reducir el número de clics del operario: es el flujo natural (primero recogida, luego entrega). Puede sobrescribir la recogida volviendo a pulsar "Seleccionar recogida" en cualquier momento.

**P3 — "¿Cómo conviertes las coordenadas del clic en (row, col)?"**
> Con la misma fórmula que usa `MapCanvas.redraw()`. El margen es `MARGIN = 22` px. `col = (int)((pixelX - MARGIN) / cellW)` y `row = (int)((pixelY - MARGIN) / cellH)`, donde `cellW = (canvasWidth - MARGIN) / cols` y `cellH = (canvasHeight - MARGIN) / rows`. Es importante usar exactamente los mismos parámetros o habría un desfase entre dónde el usuario hace clic y qué casilla selecciona el código.

**P4 — "¿Por qué `Platform.runLater` en cada handler MQTT?"**
> Porque los callbacks de Paho se ejecutan en hilos propios de Paho. JavaFX exige que toda modificación del grafo escena ocurra en el JAT. Sin `Platform.runLater` lanzaría `IllegalStateException: Not on FX application thread`.

**P5 — "¿Por qué `queuedIds` y `queueItems` en paralelo?"**
> La `ListView` muestra una etiqueta descriptiva (`"ORD-001 (1,2) -> (4,3)"`) y necesito conservar el id puro (`"ORD-001"`) para promoverlo a activo cuando llega `PEDIDO_RECIBIDO`. La alternativa sería una `ObservableList<Order>` con `CellFactory`; dejado como mejora futura.

**P6 — "¿Qué pasa si el operario pulsa 'Añadir pedido' antes de hacer los dos clics?"**
> El validador en `btnAdd.setOnAction` comprueba `selPickupRow < 0 || selDeliveryRow < 0` y muestra "Selecciona recogida y entrega en el mapa." en rojo sin publicar nada.

**P7 — "El parser de status acepta tres formatos. ¿Por qué tanta flexibilidad?"**
> Porque durante la integración con el firmware de Pablo los formatos cambiaron varias veces. El guion dice "texto plano", pero algunos prototipos publicaban JSON-string o JSON objeto. Aceptar las tres variantes blinda la app contra cualquier mensaje compatible.

---

## Anexo — Preguntas globales del profesor

**G1 — "¿Cómo aseguras que el dashboard sigue funcionando si el robot se reinicia a mitad de pedido?"**
> El tracker se basa en `snapshotRow/Col` que sólo se actualiza con `LISTO`. Si el robot reenvía una lista parcial nueva, `applyCompleted` la reaplica desde el snapshot. Si publica `PEDIDO_RECIBIDO` por error, sólo cambia el semáforo. El operario lo nota visualmente y puede reiniciar manualmente.

**G2 — "¿Cómo escalarías esto a varios robots simultáneamente?"**
> Cambiaría los topics a `Equipo E/<robotId>/odometry` y similares, suscribiría con wildcard `Equipo E/+/odometry`, extraería el `robotId` del topic en `messageArrived`, mantendría un `Map<String, RobotTracker>` y dibujaría un círculo por robot con color distinto.

**G3 — "¿Qué patrones de diseño identificas en el código?"**
> - **Observer/Callback** en `MqttService` con `Consumer<String>`.
> - **MVC informal**: `CityMap`/`Tile`/`RobotTracker`/`Order` son el modelo, `MapCanvas` la vista, `DashboardController` el controlador.
> - **Snapshot/Memento** en `RobotTracker.commitSnapshot()`.
> - **Strategy** implícito en `MapCanvas.drawTile`: imagen si hay PNG, vectorial si no.
> - **State** en `DashboardController.ClickMode`: el comportamiento del clic en el mapa cambia según el estado de selección activo.

**G4 — "¿Qué tests automatizados pondrías?"**
> - `Tile`: verificar conexiones para los 12 códigos y que `fromCode(99)` lance excepción.
> - `CityMap`: parsear un payload conocido y comprobar rejilla y pickup points.
> - `RobotTracker`: dada posición inicial y lista de instrucciones, comprobar resultado; comprobar idempotencia; comprobar `TURN_BACK`; comprobar `commitSnapshot`.
> - `DashboardController.parseInstructionsArray`: corner cases (vacío, sin clave, valores con espacios).
> - `MqttService`: integración contra un broker embebido (Moquette).

**G5 — "¿Qué problemas de seguridad ves?"**
> - MQTT sin autenticación ni TLS (puerto 1883 plano). Aceptable en red de laboratorio; en producción usaría `tcps://` con credenciales.
> - Sin validación de longitud del payload de odometría: una lista enorme consumiría memoria. En producción limitaría tamaño máximo en el broker.
> - Las coordenadas de los pedidos vienen de `currentMap.getPickupPoints()`, no de entrada libre del usuario, por lo que no hay riesgo de inyección.

**G6 — "¿Qué mejoras de rendimiento podrías introducir?"**
> - **Dirty redraw**: mantener un `WritableImage` con el mapa pre-pintado y sólo borrar/repintar la celda del robot.
> - **Imágenes pre-escaladas**: precomputar versiones escaladas de los PNGs al cargar el mapa, evitando escalado por frame.
> - **Batching de `runLater`**: si llegara un volumen alto de mensajes de odometría, coalescer múltiples actualizaciones en un solo `runLater`.

---

*Documento elaborado para defensa académica del módulo Clarence — Telepizza Dashboard. Mayo 2026.*
