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
- **`javafx-controls` 21.0.2**: aporta `BorderPane`, `VBox`, `ListView`, `Button`, `Label`, `ComboBox`. Toda la UI declarativa.
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
- **`requires javafx.controls`**: importa los controles UI (Button, Label, ComboBox…).
- **`requires javafx.fxml`**: importa el cargador de FXML. Aunque la app construye la UI programáticamente, JavaFX lo necesita para inicializar correctamente el toolkit.
- **`requires org.eclipse.paho.client.mqttv3`**: importa el cliente Paho.
- **`opens com.example.robot to javafx.fxml`**: permite a `javafx.fxml` acceder por reflexión a las clases del paquete. Es necesario para `FXMLLoader` aunque aquí no se use, porque algunas factorías internas de JavaFX lo requieren.
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
import javafx.fxml.FXMLLoader;        // (no se usa, herencia de la plantilla)
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
```

- **`Application`**: clase base de toda aplicación JavaFX. Su método estático `launch()` arranca el toolkit gráfico, crea el hilo JavaFX Application Thread y llama a `start()`.
- **`Scene`** + **`Stage`**: el modelo "ventana → escena → grafo de nodos" típico de JavaFX.
- **`BorderPane`**: tipo de raíz devuelto por `controller.buildUI()` (declarado para evitar un cast).

### 3.3 Desglose de componentes

#### Método `start(Stage primaryStage)`

- **Propósito:** construir la UI, presentarla al usuario y arrancar el MQTT.
- **Parámetros:** `primaryStage` es la ventana principal que JavaFX inyecta.
- **Devuelve:** `void` (lanza `Exception` para no envolver errores de inicialización).
- **Lógica paso a paso:**
  1. `new DashboardController()`: instancia el controlador, que aún no toca la UI.
  2. `controller.buildUI()`: construye el grafo de nodos JavaFX (paneles, controles, canvas). Devuelve el `BorderPane` raíz.
  3. `new Scene(..., 900, 700)`: envuelve el grafo en una escena de 900×700 px.
  4. `primaryStage.setTitle("Robot Delivery Dashboard")`: título de la ventana.
  5. `primaryStage.setScene(scene)`: asocia la escena a la ventana.
  6. `primaryStage.show()`: muestra la ventana. **Importante:** sólo aquí se hace visible el hilo gráfico.
  7. `controller.startMqtt()`: arranca la conexión MQTT en un hilo separado (lo hace internamente). Se invoca **después** de `show()` para que la UI ya esté preparada para recibir mensajes asíncronos.

#### Método `stop()`

- **Propósito:** se invoca cuando el usuario cierra la ventana o llama a `Platform.exit()`. Aquí sólo delega en `super.stop()`, porque `MqttService` registra un `ShutdownHook` que cierra la conexión de forma autónoma.
- **Parámetros / devuelve:** `void`, lanza `Exception`.

#### Método `main(String[] args)`

- **Propósito:** delegar en `launch(args)`. Necesario sólo si se ejecuta el JAR directamente (no con `mvn javafx:run`).
- **Lógica:** una sola línea. `launch()` bloquea hasta que la aplicación termina.

### 3.4 Preguntas trampa del profesor

**P1 — "¿Por qué llamas a `startMqtt()` después de `show()` y no antes?"**
> Porque `MqttService` empuja mensajes a los handlers, que actualizan la UI con `Platform.runLater(...)`. Si arrancara antes de `show()`, podríamos recibir un mensaje cuando aún no existen los `Label` o `ListView`, provocando `NullPointerException`. Arrancarlo después garantiza que el grafo escena ya está construido y referenciado por el controlador.

**P2 — "¿Qué hace exactamente `Application.launch(args)`?"**
> Inicializa el JavaFX runtime, crea el **JavaFX Application Thread**, instancia `RobotApp` por reflexión, llama a `init()` (vacío por defecto), luego a `start(primaryStage)` y se queda bloqueado hasta que `Platform.exit()` o se cierra la última ventana. Después llama a `stop()` y termina.

**P3 — "¿Por qué importas `FXMLLoader` si no lo usas?"**
> Es una herencia de la plantilla generada por IntelliJ. No tiene efecto en runtime porque los imports no se cargan dinámicamente. En una versión de producción se eliminaría, pero como prueba de que la app **podría** cargar FXML sin tocar el `pom`, lo dejo declarado.

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

- **`Consumer<String>`**: interfaz funcional usada para los callbacks. Permite registrar "qué hacer cuando llega texto al topic X" sin acoplar al servicio con la UI.
- **`MqttClient`**: cliente síncrono Paho.
- **`MqttConnectOptions`**: configura keep-alive, reconexión automática, etc.
- **`MqttCallback`**: interfaz con `connectionLost`, `messageArrived`, `deliveryComplete`.
- **`MemoryPersistence`**: almacenamiento en memoria de mensajes QoS > 0 pendientes; alternativa a `MqttDefaultFilePersistence`, evita escribir archivos en disco.

### 4.3 Desglose de componentes

#### Constantes

```java
public static final String BROKER_URL = "tcp://192.168.0.105:1883";
private static final String CLIENT_ID = "RobotDashboard-JavaFX-" + System.currentTimeMillis();
public static final String TOPIC_MAP       = "map";
public static final String TOPIC_ORDERS    = "Equipo E/orders";
public static final String TOPIC_ODOMETRY  = "Equipo E/odometry";
public static final String TOPIC_STATUS    = "Equipo E/status";
```

- **`BROKER_URL`**: TCP plano al puerto 1883. **Aviso defensa:** este valor se actualizó al portátil de Pablo (commit `6077933`); el README aún cita `192.168.1.122`. Confirma siempre la IP real del broker antes de demo.
- **`CLIENT_ID`**: identificador único por instancia. El sufijo `System.currentTimeMillis()` evita choques cuando se relanza la app sin que el broker haya purgado la sesión anterior.
- **`TOPIC_*`**: cuatro topics del guion del Equipo E.

#### Campos y setters

```java
private MqttClient client;
private Consumer<String> onMapReceived;
private Consumer<String> onOdometryReceived;
private Consumer<String> onStatusReceived;
private Consumer<String> onStatusMessage;
public void setOnMapReceived(Consumer<String> cb)       { ... }
// ...
```

- **Patrón observer** simplificado vía `Consumer<String>`. Permite que `DashboardController` registre cuatro lambdas: una por topic + una para mensajes de estado de la conexión (`"Conectado"`, `"Conexión perdida"`, etc.).

#### Método `connect()`

- **Propósito:** establecer la conexión con el broker y suscribirse a los tres topics de entrada.
- **Parámetros:** ninguno.
- **Devuelve:** `void`; lanza `MqttException`.
- **Lógica paso a paso:**
  1. `if (client != null && client.isConnected()) return;` — idempotencia: evita reconectar si ya está conectado.
  2. `client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence())` — crea el cliente.
  3. Construye `MqttConnectOptions`:
     - `setCleanSession(true)`: no recupera estado de sesiones anteriores.
     - `setConnectionTimeout(10)`: 10 s para establecer TCP.
     - `setKeepAliveInterval(30)`: ping cada 30 s para detectar caídas de red.
     - `setAutomaticReconnect(true)`: Paho se reconecta solo con back-off exponencial.
  4. `client.setCallback(new MqttCallback() { ... })`: registra los tres métodos:
     - `connectionLost(cause)` → notifica al UI con `notifyStatus(...)`.
     - `messageArrived(topic, message)` → demultiplexa por topic e invoca el `Consumer` correspondiente.
     - `deliveryComplete(token)` → vacío; no nos importa cuándo se confirman los `publish`.
  5. `client.connect(opts)` — conexión TCP/MQTT bloqueante.
  6. Suscripción a los tres topics con QoS distintos:
     - `TOPIC_MAP`, QoS 1 → "al menos una vez", garantiza recibir el mapa.
     - `TOPIC_ODOMETRY`, QoS 0 → "fire and forget", llega cada segundo, se acepta perder alguno.
     - `TOPIC_STATUS`, QoS 0 → idem; las transiciones son visualizables y no nos vamos a perder muchas.
  7. `notifyStatus("MQTT: conectado a " + BROKER_URL)` — informa al UI.
  8. `Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect))` — garantiza cierre limpio al matar la JVM.

#### Método `disconnect()`

- **Propósito:** cerrar la conexión MQTT sin lanzar excepciones (se invoca desde el shutdown hook, donde no podemos hacer más que tragarlas).
- **Lógica:** `try { client.disconnect(); } catch (MqttException ignored) {}` — el `ignored` es intencional: estamos cerrando y cualquier error es irrelevante.

#### Método `isConnected()`

- Una línea: comprueba que el `client` exista y esté conectado.

#### Método `notifyStatus(String msg)`

- **Propósito:** wrapper sobre el callback `onStatusMessage` para evitar repetir el chequeo de null.

#### Método `publish(String topic, String payload)`

- **Propósito:** publicar texto en un topic.
- **Parámetros:** `topic` (String), `payload` (String).
- **Devuelve:** `void`; lanza `MqttException` si no hay conexión.
- **Lógica paso a paso:**
  1. Si no hay cliente o no está conectado → lanza `MqttException(REASON_CODE_CLIENT_NOT_CONNECTED)`. Esto se propaga al botón "Añadir pedido", que muestra el error en rojo.
  2. Crea `MqttMessage` con `payload.getBytes()` (UTF-8 por defecto en JVM moderna).
  3. Fija QoS 1 (al menos una vez) — los pedidos deben llegar sí o sí.
  4. `client.publish(topic, message)` — síncrono respecto al envío local, pero Paho lo encola y devuelve al instante.

### 4.4 Preguntas trampa del profesor

**P1 — "¿Por qué los handlers MQTT no actualizan la UI directamente?"**
> Porque Paho llama a `messageArrived` desde un **hilo propio de Paho** (no el JavaFX Application Thread). JavaFX exige que cualquier modificación del grafo escena ocurra en el JAT, o lanza `IllegalStateException`. Por eso `DashboardController` envuelve cada handler en `Platform.runLater(...)`. El servicio MQTT, deliberadamente, no conoce JavaFX: separación de responsabilidades.

**P2 — "¿Qué pasaría si el broker se cae a mitad de demo?"**
> `MqttConnectOptions.setAutomaticReconnect(true)` hace que Paho intente reconectar con back-off exponencial (empezando en 1 s, hasta 2 minutos). `connectionLost` notifica el estado al UI con un texto rojo. Si el broker vuelve, las suscripciones se restablecen automáticamente porque `cleanSession=true` y Paho reenvía los `subscribe` en cada reconexión.

**P3 — "¿Por qué QoS 1 en mapa y QoS 0 en odometría?"**
> El mapa llega cada 60 s; si lo perdemos, la app se queda con `currentMap == null` y los desplegables vacíos hasta el siguiente ciclo. QoS 1 garantiza recepción al coste de un ACK. La odometría llega cada segundo y el cálculo es **idempotente** (ver `RobotTracker.applyCompleted`): perder un mensaje no altera el resultado, ya que el siguiente reaplica toda la lista. Por tanto QoS 0 minimiza overhead.

**P4 — "¿Por qué `cleanSession=true`?"**
> Porque no queremos que el broker nos reenvíe mensajes encolados de sesiones antiguas (que serían pedidos cerrados o mapas viejos). Al reiniciar la app, queremos partir limpios y recibir sólo eventos nuevos.

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

    public final int code;
    Id(int code) { this.code = code; }

    public static Id fromCode(int code) {
        for (Id id : values()) if (id.code == code) return id;
        throw new IllegalArgumentException("Unknown tile code: " + code);
    }
}
```

- **Propósito:** asociar cada nombre simbólico con su **código numérico de dos dígitos** que aparece en la cadena MQTT.
- **Convención de nombres:** las letras tras `ROAD_` son las direcciones de conexión:
  - `L`/`R`/`U`/`D` = Left / Right / Up / Down.
  - `ROAD_LR` → calle horizontal este-oeste.
  - `ROAD_UD` → calle vertical norte-sur.
  - `ROAD_UR` → curva que conecta arriba y derecha.
  - `ROAD_LUR` → T con bocas izquierda + arriba + derecha (no baja).
  - `ROAD_ALL` → cruce de cuatro bocas.
- **`fromCode(int)`**: busca linealmente el `Id` con el código pedido. Si no existe → `IllegalArgumentException` (defensa explícita).

#### Campos de instancia

```java
private final Id id;
private final boolean connectsUp, connectsRight, connectsDown, connectsLeft;
```

- **`final`**: el bloque es inmutable. Una vez creado no cambia de tipo. Esto facilita el threading: cualquier hilo puede leerlo sin sincronización.

#### Constructor `Tile(int code)`

- **Propósito:** crear un bloque a partir de su código numérico.
- **Lógica paso a paso:**
  1. `this.id = Id.fromCode(code)` — convierte el entero a enum.
  2. `switch (id) -> { ... }` — pattern matching que asigna las cuatro variables de conexión. Cada `case` es una **expresión** de bloque que asigna las cuatro a la vez. Java 21 permite que el `default` cubra un caso teóricamente inalcanzable; está incluido por seguridad estática.

#### Getters

- `getId()`, `getCode()`, `isBuilding()`, `connectsUp/Right/Down/Left()`: triviales, sólo exponen el estado.

#### Método `connectionCount()`

- **Propósito:** contar cuántas direcciones tiene conexión activa.
- **Lógica:** suma de las cuatro variables booleanas convertidas a 1/0 con ternarios.
- **Casos:** edificio = 0, calle terminal = 1, calle recta o curva = 2, T = 3, cruce = 4.

#### Método `isPickupOrDelivery()`

- **Propósito:** devuelve `true` si el bloque es **terminal de calle**, es decir un punto válido de recogida/entrega.
- **Lógica:** `!isBuilding() && connectionCount() == 1`. Atención: esto detecta el **tipo** (terminal por su forma), no comprueba que el vecino exista y reciproque la conexión; ese chequeo más estricto lo hace `CityMap.isPickupPoint`.

### 5.4 Preguntas trampa del profesor

**P1 — "¿Por qué un enum y no constantes `int` estáticas?"**
> Type safety. Con `enum` el compilador prohíbe pasar un código inválido (p. ej. `Tile.Id.UNKNOWN`), `switch` es exhaustivo y el `IDE` autocompleta los 12 valores. Con `int`, cualquier número pasaría compilación y el bug aparecería en runtime al pintar el mapa.

**P2 — "¿Por qué guardas las cuatro variables `connectsX` en lugar de calcularlas cada vez desde el `Id`?"**
> Por **rendimiento y legibilidad**. `MapCanvas.drawTile` consulta esas cuatro variables varias veces por tile y por frame. Cachearlas en el constructor evita un `switch` por consulta. Como el tile es `final` e inmutable, no hay riesgo de inconsistencia.

**P3 — "¿Por qué dejas un `default` que asigna todo a `false` si nunca se va a ejecutar?"**
> Porque sin él, el compilador exige que todas las ramas asignen los campos `final`. Aunque el `switch` cubre los 12 enum literales, el compilador no puede demostrar exhaustividad en presencia de campos `final` sin un `default` o sin que el switch sea una expresión (no statement). El `default` me evita una segunda lectura del `switch` y deja el código robusto si alguien añade un código nuevo al enum sin actualizar este constructor.

---

## 6. `CityMap.java` — Decodificación y modelado del mapa

### 6.1 Resumen ejecutivo

Cuando llega el mapa por MQTT, llega como una **cadena de 70 caracteres** (5 columnas × 7 filas × 2 dígitos por casilla). Este archivo es el **traductor**: convierte ese texto en una rejilla 7×5 de objetos `Tile` y, además, detecta qué casillas pueden ser puntos de recogida/entrega.

### 6.2 Arquitectura y dependencias

```java
import java.util.ArrayList;
import java.util.List;
```

Nada externo. Usa sólo `Tile` y colecciones del JDK.

### 6.3 Desglose de componentes

#### Campos

```java
private final int cols;
private final int rows;
private final Tile[][] grid;
private final List<int[]> pickupPoints = new ArrayList<>();
```

- `grid[row][col]` — convención **fila-primero** (matriz). Ojo: `grid` se indexa `[fila][columna]`.
- `pickupPoints` — lista de coordenadas válidas para recogida/entrega. Cada elemento es un `int[2]` = `{row, col}`.

#### Constructor `CityMap(String mqttPayload, int cols, int rows)`

- **Propósito:** parsear el payload y construir la rejilla más la lista de puntos válidos.
- **Parámetros:** payload crudo, número de columnas (5) y filas (7).
- **Lógica paso a paso:**
  1. `String s = mqttPayload.trim().replaceAll("\\s+", "")` — elimina cualquier espacio o salto de línea. Robusto frente a payload con whitespace incidental.
  2. `if (s.length() != rows * cols * 2) throw ...` — validación dura: debe haber exactamente 70 caracteres para 5×7.
  3. **Primera pasada** — bucle anidado `(row, col)`:
     - `idx = (row * cols + col) * 2` — fórmula de **layout row-major** para encontrar el offset.
     - `code = Integer.parseInt(s.substring(idx, idx + 2))` — parsea los dos dígitos.
     - `grid[row][col] = new Tile(code)`.
  4. **Segunda pasada** — para cada celda no-edificio, llama a `isPickupPoint(row, col)` y, si es válida, añade `{row, col}` a `pickupPoints`.

#### Método privado `isPickupPoint(int row, int col)`

- **Propósito:** decidir si la casilla es un terminal de calle **real**: que el bloque diga "tengo una boca hacia X" Y que el vecino en X exista y diga "te recibo".
- **Lógica paso a paso:**
  1. `validConnections = 0`.
  2. Por cada dirección (UP/DOWN/LEFT/RIGHT) comprueba:
     - El tile actual conecta en esa dirección.
     - El vecino está dentro del mapa (`row > 0` para UP, etc.).
     - El vecino no es edificio.
     - El vecino conecta en la dirección opuesta.
  3. Si las cuatro condiciones se cumplen, incrementa `validConnections`.
  4. Devuelve `validConnections == 1`. **Exactamente uno**, por la definición de terminal.

> Esto descarta falsos positivos: una calle dibujada hacia el este pero con un edificio al este no es punto de recogida; ni siquiera lo es si el vecino no le devuelve la conexión.

#### Getters

- `getTile(row, col)`, `getCols()`, `getRows()`, `getPickupPoints()` — triviales.

### 6.4 Preguntas trampa del profesor

**P1 — "¿Por qué validar conexiones recíprocas en `isPickupPoint`? Bastaría con `tile.connectionCount() == 1`."**
> No. El método de `Tile` sólo mira la forma del bloque. El mapa real puede tener inconsistencias: una calle terminal hacia el este pero con edificio al este es físicamente imposible para el robot. Validar la reciprocidad garantiza que los puntos que mostramos al operario son **alcanzables**, no sólo "con forma de terminal". Es la diferencia entre `isPickupOrDelivery()` (heurística por tipo) y `isPickupPoint()` (validación contra el grafo).

**P2 — "¿Qué pasa si llega un mapa con un código inválido, por ejemplo `99`?"**
> `new Tile(99)` invocará `Id.fromCode(99)` que lanza `IllegalArgumentException`. La excepción se propaga al constructor de `CityMap`, que la propaga a `DashboardController.handleMapPayload`, que la captura y muestra "Error al parsear mapa: …" en la barra de estado. El mapa anterior sigue pintado.

**P3 — "¿Por qué `List<int[]>` y no una clase `Point`?"**
> Compromiso pragmático. Un `int[2]` es asignación trivial y serialización inmediata al string `"row,col"` que usamos en los ComboBox. Una clase `Point` añadiría boilerplate sin valor en este alcance. Si en producción hubiera más operaciones sobre puntos (distancia, ordenación), sí refactorizaría a un `record Point(int row, int col)`.

**P4 — "¿Por qué dos pasadas en lugar de una?"**
> Porque `isPickupPoint` mira a los **vecinos**. En una pasada única, al evaluar la celda (0,0) los vecinos (0,1) y (1,0) aún no existirían. Separar en dos pasadas hace el código sencillo y obviamente correcto.

---

## 7. `RobotTracker.java` — Cálculo de posición del robot

### 7.1 Resumen ejecutivo

El robot real no sabe en qué casilla está. Sólo sabe qué instrucciones ya ha completado (`MOVE`, `TURN_LEFT`, etc.). Este archivo es el **GPS virtual**: parte de una casilla inicial conocida y aplica las instrucciones para deducir dónde está y hacia dónde mira.

### 7.2 Arquitectura y dependencias

```java
import java.util.List;
```

Sólo el JDK. Es lógica pura.

### 7.3 Desglose de componentes

#### Enum `Heading`

```java
public enum Heading { N, E, S, W }
```

Cuatro orientaciones cardinales. Orden importante: el orden N→E→S→W define la rotación a la derecha y se aprovecha implícitamente en `turnRight`/`turnLeft`.

#### Campos

```java
private int row, col;
private Heading heading;
private int snapshotRow, snapshotCol;
private Heading snapshotHeading;
```

- `(row, col, heading)` — estado **actual** del robot.
- `(snapshotRow, snapshotCol, snapshotHeading)` — estado al inicio del pedido actual. Se actualiza al recibir `LISTO`.

#### Constructor

- Inicializa estado actual y snapshot a los mismos valores: al arrancar, "donde está = donde empezó".

#### Método estático `initialHeading(Tile tile)`

- **Propósito:** deducir la orientación inicial del robot mirando el tipo de bloque donde arranca.
- **Regla del enunciado:** si el bloque conecta hacia arriba, el robot mira al norte; si conecta hacia la derecha, al este, etc.
- **Lógica:** cadena de `if` con prioridad N > E > S > W. En la práctica el bloque (6,0) será `ROAD_UR` (boca arriba y derecha), por lo que la regla devuelve `N` (norte).

#### Método `applyCompleted(List<String> instructions)`

- **Propósito:** recalcular la posición actual a partir de la lista completa de instrucciones completadas.
- **Parámetros:** `instructions` — la lista íntegra recibida por MQTT.
- **Lógica paso a paso:**
  1. Restaura el estado al snapshot (`row = snapshotRow`, etc.).
  2. Itera la lista entera y aplica cada instrucción.
- **Idempotencia:** ejecutar dos veces seguidas la misma lista deja el mismo estado, porque empezamos siempre desde el snapshot. Esto es **crítico**: la odometría se reenvía cada segundo y los mensajes pueden duplicarse o llegar fuera de orden lógico; el tracker absorbe todos los casos.

#### Método `commitSnapshot()`

- **Propósito:** congelar la posición actual como nuevo punto de partida. Se llama al recibir `LISTO`, porque Pablo resetea el contador de instrucciones del firmware al empezar el siguiente pedido.

#### Método privado `applyOne(String instr)`

- **Propósito:** aplicar una instrucción al estado.
- **Lógica:**
  - `MOVE`: avanza una casilla en la dirección del `heading`.
    - N: `row--` (norte = filas decrecientes en convención row-major).
    - S: `row++`.
    - E: `col++`.
    - W: `col--`.
  - `TURN_LEFT` / `TURN_RIGHT`: cambio de heading mediante funciones puras.
  - `TURN_BACK`: dos giros a la izquierda (180°).
  - Cualquier otra (`PICK_UP`, `DELIVER`, `STRAIGHT`): no afectan la posición; se ignoran explícitamente con el `default`.

#### Métodos estáticos `turnLeft(Heading)` y `turnRight(Heading)`

- **Propósito:** rotar el heading 90°.
- **Lógica:** `switch` expression que devuelve el nuevo `Heading`. Son funciones puras, sin efectos colaterales, fácilmente testeables.

#### Getters

- `getRow()`, `getCol()`, `getHeading()` — exponen el estado actual.

### 7.4 Preguntas trampa del profesor

**P1 — "¿Por qué reaplicar la lista completa en cada mensaje en lugar de aplicar sólo el delta nuevo?"**
> Por **robustez frente a pérdida y duplicación**. MQTT con QoS 0 no garantiza orden ni entrega exacta. Si aplicáramos sólo el delta y se pierde un mensaje, el estado quedaría incorrecto para siempre. Como la lista es pequeña (≤ pocas decenas de instrucciones por pedido), reaplicarla cada segundo es trivial en CPU y nos da idempotencia gratuita.

**P2 — "¿Por qué `TURN_BACK` se implementa como dos `turnLeft`? ¿No sería más eficiente un mapeo directo N↔S, E↔W?"**
> Equivalencia exacta y código más mantenible. La composición de dos rotaciones de 90° es la misma operación que el operario entendería; el mapeo directo sería un cuarto `switch` que duplica conocimiento. El coste (dos operaciones de enum) es despreciable y se ejecuta como mucho una vez por segundo.

**P3 — "¿Qué garantiza que `applyCompleted` no se ejecuta concurrentemente?"**
> El `DashboardController` envuelve la llamada en `Platform.runLater(...)`, por lo que se ejecuta siempre en el JavaFX Application Thread. Hay un único productor (el handler de odometría) y un único hilo consumidor (el JAT). Sin contienda, no necesitamos `synchronized`.

**P4 — "¿Qué pasa si el robot publica un `MOVE` que lo llevaría a una casilla edificio?"**
> El tracker es **ciego al mapa**: simplemente actualiza `row`/`col` aritméticamente. Si el robot real respeta el grafo de calles, el resultado es correcto. Si el robot publica una instrucción inválida, el dot naranja se dibujaría sobre un edificio: visual obvio de un bug en `telepizza-ev3`, no nuestro. El dashboard es un **observador**, no un validador.

---

## 8. `MapCanvas.java` — Render gráfico del mapa

### 8.1 Resumen ejecutivo

Es el **pintor**. Recibe el mapa procesado y dibuja sobre un lienzo: las casillas con sus PNG, los números de fila/columna, el borde amarillo de los puntos de recogida y el círculo naranja del robot. Si falta un PNG, dibuja a mano un sucedáneo vectorial.

### 8.2 Arquitectura y dependencias

```java
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import java.util.EnumMap;
import java.util.Map;
```

- **`Canvas` / `GraphicsContext`**: API de dibujo inmediato (rasterización). Más rápido y flexible que componer 35 `Pane` con `ImageView`.
- **`Image`**: imagen cargada en memoria (PNG).
- **`EnumMap<Tile.Id, Image>`**: mapa especializado para claves de tipo `enum`; internamente es un array indexado por `ordinal()`, mucho más rápido que un `HashMap`.

### 8.3 Desglose de componentes

#### Constantes de color

Trece colores web (hex) precalculados: edificio, calles, líneas verdes (interior de carril), líneas azules (exterior), cuadrado negro central (intersección), naranja del robot, texto del ID, etc.

#### Constante `MARGIN = 22`

Margen reservado para los números de fila/columna alrededor de la rejilla.

#### Constructor `MapCanvas(double width, double height)`

- Extiende `Canvas(width, height)` y llama a `loadTileImages()` una sola vez.

#### Método privado `loadTileImages()`

- **Propósito:** intentar cargar `/tiles/<NOMBRE_ENUM>.png` para cada `Tile.Id`.
- **Lógica:**
  1. Itera los 12 valores del enum.
  2. Compone el path: `"/tiles/" + id.name() + ".png"` (p. ej. `/tiles/ROAD_URD.png`).
  3. Llama a `getResourceAsStream(path)`. Si no existe, el stream es `null` y se salta el tile.
  4. Si carga, comprueba `img.isError()` y lo añade al `EnumMap`.

#### `setMap(CityMap)` y `setRobotPosition(int, int)`

- Actualizan el estado interno y llaman a `redraw()`. Cada cambio fuerza un repintado completo (no hay invalidación incremental).

#### Método `redraw()`

- **Propósito:** repintar todo el canvas desde cero.
- **Lógica paso a paso:**
  1. `gc.clearRect(0, 0, w, h)` — borra el canvas.
  2. Pinta fondo gris claro en toda la zona de margen.
  3. Si `map == null`, muestra texto "Esperando mapa MQTT..." centrado y termina.
  4. Calcula `cellW = (w - MARGIN) / map.getCols()` y `cellH = (h - MARGIN) / map.getRows()`.
  5. Doble bucle sobre `(row, col)`: llama a `drawTile(gc, tile, x, y, cellW, cellH, row, col)`.
  6. Pinta la **rejilla negra** encima (líneas verticales y horizontales con `strokeLine`).
  7. Pinta **bordes amarillos** alrededor de cada tile pickup/delivery, **encima** de la rejilla negra, para que destaquen.
  8. Pinta los **números de columna** en el margen superior y los **números de fila** en el izquierdo.
  9. Si `robotRow >= 0`, llama a `drawRobot(gc, ...)`.

#### Método privado `drawTile(...)`

- **Propósito:** dibujar una casilla individual.
- **Lógica paso a paso:**
  1. **Camino imagen** — si hay un PNG cargado para este `Tile.Id`:
     - `gc.drawImage(img, x, y, w, h)`.
     - Si es pickup/delivery, superpone:
       - Rectángulo amarillo semitransparente.
       - Letra "P" en la esquina superior derecha.
       - Borde amarillo grueso de 2.5 px.
     - `return` (no se hace dibujo vectorial).
  2. **Camino vectorial (fallback)** — si no hay PNG:
     - Si es edificio: fondo rosa pálido + círculo rojo central.
     - Si es calle: fondo blanco azulado o verdoso (si es pickup), rectángulos celestes para cada brazo de la calle, dos pares de líneas (verdes interiores + azules exteriores) y un cuadrado negro central que representa la intersección.
     - Marcador "P" + borde amarillo si es pickup.
     - Etiqueta con el código numérico de la tile en la esquina inferior izquierda.

#### Método privado `drawLaneLines(...)`

- **Propósito:** dibujar los pares de líneas paralelas (carriles) que recorren la calle en cada dirección activa.
- **Lógica:** para cada dirección activa, dos `strokeLine` paralelos separados una distancia `off = rh * offsetFrac`. Se invoca dos veces desde `drawTile`: una vez con color verde y offset 0.18 (carriles interiores), otra con azul y offset 0.36 (carriles exteriores).

#### Método privado `drawRobot(...)`

- **Propósito:** dibujar el indicador del robot.
- **Lógica:** círculo naranja semitransparente con borde blanco grueso y letra "R" centrada en blanco. El radio es 20 % del lado más pequeño de la celda.

### 8.4 Preguntas trampa del profesor

**P1 — "¿Por qué `Canvas` y no `GridPane` con `ImageView` en cada celda?"**
> `GridPane` mantendría 35 nodos en el grafo escena, cada uno con su propia gestión de eventos, layout y CSS. Para 35 celdas que se repintan en bloque cuando llega un mapa nuevo o se mueve el robot, `Canvas` es 5–10× más eficiente: una sola llamada al pintor del sistema, sin overhead de scene graph. Además permite combinar imagen + overlays (borde amarillo, letra "P", marcador del robot) sin Z-orden complejo.

**P2 — "¿Qué pasa si un PNG está corrupto en `/resources/tiles/`?"**
> `new Image(stream)` cargará la imagen y marcará `img.isError() == true`. Compruebo ese flag y, si está, no añado la imagen al `EnumMap`. Como `drawTile` consulta `tileImages.get(id)` y, si es `null`, cae al camino vectorial, la app sigue funcionando sin esa imagen y se nota visualmente (color plano en lugar de PNG), pero no crashea.

**P3 — "¿Por qué dibujas los bordes amarillos después de la rejilla negra?"**
> Para que sean visibles. Si los dibujara antes, las líneas negras (1.5 px) los taparían parcialmente en las esquinas. Al pintarlos después con `lineWidth = 3.0`, dominan visualmente y el operario ve de un vistazo dónde puede recoger/entregar.

**P4 — "¿Por qué `EnumMap` y no `HashMap`?"**
> Porque la clave es un `enum` con 12 valores. `EnumMap` está implementado internamente como un array de tamaño 12 indexado por `Tile.Id.ordinal()`, sin hashing ni colisiones. Es más rápido y consume menos memoria que un `HashMap`. Además, no admite claves null, lo que es deseable aquí.

**P5 — "Si la ventana se redimensiona, ¿se repinta?"**
> El canvas tiene tamaño fijo en el constructor (`new MapCanvas(420, 588)`). Una ventana resizable no hace que el canvas crezca: se mantendrá en 420×588 y se centrará/desplazará dentro de su contenedor. Para soportar resize habría que enganchar `widthProperty().addListener(...)` y `heightProperty().addListener(...)` para recalcular y llamar a `redraw()`. No es requisito en el enunciado.

---

## 9. `Order.java` — Modelo de pedido

### 9.1 Resumen ejecutivo

Representa un pedido individual: tiene un id (`ORD-001`), un punto de recogida `(row, col)`, un punto de entrega `(row, col)`, un estado (`PENDING`, `IN_PROGRESS`, `DELIVERED`, `FAILED`) y un porcentaje de progreso de 0 a 1.

### 9.2 Arquitectura y dependencias

Ninguna. Clase POJO autocontenida.

### 9.3 Desglose de componentes

#### Enum `Status`

```java
public enum Status { PENDING, IN_PROGRESS, DELIVERED, FAILED }
```

Modelado del ciclo de vida del pedido.

#### Campos

```java
private final String id;
private final int pickupRow, pickupCol;
private final int deliveryRow, deliveryCol;
private Status status;
private double progress;
```

- **`final`** en id y coordenadas: invariantes una vez creado el pedido.
- **`status` y `progress`** son mutables porque cambian a lo largo del pedido.

#### Constructor

- Inicializa todo y deja `status = PENDING`, `progress = 0`.

#### Getters

- Triviales para los seis campos.

#### Setters

- `setStatus(Status)`: directo.
- `setProgress(double p)`: aplica `Math.max(0, Math.min(1, p))` para **clamping** entre 0 y 1. Defensa frente a valores inválidos del exterior.

#### `getPickupLabel()` y `getDeliveryLabel()`

- Devuelven cadenas con formato `"(row,col)"` para mostrar en la UI.

#### `toString()`

- Devuelve `"ORD-001 (1,2) → (4,3)"`.

### 9.4 Preguntas trampa del profesor

**P1 — "¿Por qué `Order` existe si `DashboardController` usa `List<String>` para la cola y el historial?"**
> Es un modelo previsto para enriquecer la UI futura. En la implementación actual el flujo MQTT es asíncrono y simple — basta con strings — pero `Order` queda preparado para refactor: añadir vista de progreso por pedido, ordenar por estado, filtrar el historial. Mantenerlo en el código documenta la intención del dominio.

**P2 — "¿Por qué `progress` es `double` y no, por ejemplo, un porcentaje entero?"**
> Porque casa naturalmente con un `ProgressBar` de JavaFX, que recibe valores `[0.0, 1.0]`. Evita la conversión en el binding.

**P3 — "¿Es thread-safe?"**
> No, los setters no son `synchronized` ni los campos `volatile`. Diseñado para vivir en el JavaFX Application Thread (donde se manipulan los modelos UI). Si en un futuro un hilo MQTT lo modificara directamente, habría que añadir sincronización o, mejor, usar `Platform.runLater`.

---

## 10. `DashboardController.java` — Orquestador de la UI y handlers MQTT

### 10.1 Resumen ejecutivo

Es el **director de orquesta**. Construye toda la interfaz gráfica del dashboard (mapa a la izquierda, panel de pedidos/cola/historial/estado a la derecha, barra de estado abajo) y conecta cada mensaje MQTT con su efecto visual.

### 10.2 Arquitectura y dependencias

```java
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import java.util.ArrayList;
import java.util.List;
```

- **`Platform.runLater(Runnable)`**: ejecuta código en el JavaFX Application Thread. Imprescindible para mutar UI desde handlers MQTT.
- **`ObservableList`**: lista que notifica cambios; las `ListView` se actualizan automáticamente al añadir/quitar elementos.
- **Layout `BorderPane` + `VBox` + `HBox`**: composición declarativa de paneles.

### 10.3 Desglose de componentes

#### Constantes de configuración

```java
private static final int MAP_COLS = 5;
private static final int MAP_ROWS = 7;
private static final int START_ROW = 6;
private static final int START_COL = 0;
```

- Mapa 5 columnas × 7 filas.
- Posición inicial del robot: fila 6, columna 0 → esquina inferior izquierda. Definido en el enunciado.

#### Campos de UI

Referencias a todos los nodos que se actualizan en runtime: el canvas, las etiquetas de pedido actual, las listas, los puntos del semáforo, los combos de pickup/delivery, etc. Se inicializan en los `build*Panel()`.

#### Campos de estado

```java
private CityMap currentMap;
private RobotTracker tracker;
private final ObservableList<String> queueItems   = FXCollections.observableArrayList();
private final ObservableList<String> historyItems = FXCollections.observableArrayList();
private final List<String> queuedIds = new ArrayList<>();
private String activeOrderId = null;
private final MqttService mqtt = new MqttService();
```

- **`queueItems`** y **`historyItems`** son `ObservableList`: las `ListView` que las muestran se refrescan automáticamente.
- **`queuedIds`** es una lista paralela a `queueItems` que conserva sólo los identificadores `ORD-xxx` (sin formato de visualización), para promover el pedido a "activo" cuando llega `PEDIDO_RECIBIDO`.
- **`activeOrderId`** es el pedido en curso, `null` si no hay ninguno.
- **`mqtt`** es la única instancia del servicio MQTT, no estática para facilitar tests futuros.

#### Método `buildUI()`

- **Propósito:** construir el `BorderPane` raíz con todo el layout.
- **Devuelve:** el `BorderPane` listo para envolver en `Scene`.
- **Lógica paso a paso:**
  1. Crea el `BorderPane`, fija padding 12 px y fondo `#f7f7f5` (gris muy claro).
  2. Crea el `MapCanvas` 420×588 px.
  3. Apila en un `VBox` (`mapBox`): título "Mapa de ciudad" + canvas + leyenda de colores. Lo decora con fondo blanco y borde redondeado, lo coloca en la posición `LEFT` del BorderPane.
  4. Crea un `VBox` (`right`) con los cinco paneles del lateral derecho: pedido actual, cola/gestión, historial, semáforo de estado, info MQTT. Ancho preferido 300 px, margen izquierdo 10 px, posición `CENTER`.
  5. Crea la barra de estado inferior: `lblStatus` + `lblMapRaw` (este último monospace, 10 pt, muestra los primeros 40 chars del payload del mapa).
  6. Devuelve el `BorderPane` completo.

#### Método `buildCurrentOrderPanel()`

- **Propósito:** construir el panel "Pedido actual" con id, recogida, entrega y progreso.
- **Lógica:** crea cuatro `Label` con tipografía y colores definidos. Los envuelve en un `VBox` interno con fondo gris azulado (`#f0f4f8`) y bordes redondeados, y este a su vez en un `wrapPanel` blanco.

#### Método `buildQueuePanel()`

- **Propósito:** construir el formulario de creación de pedidos + la cola visual.
- **Lógica paso a paso:**
  1. Crea dos `ComboBox<String>`: pickup y delivery, ancho expandido al máximo.
  2. **Contador local mutable** `final int[] orderCounter = {1}`. Truco clásico para mutar un valor dentro de una lambda preservando final-effective.
  3. Crea el botón "Anadir pedido" y la etiqueta de estado del formulario.
  4. Registra `setOnAction(e -> { ... })`:
     - Lee los valores seleccionados. Si alguno es `null` → muestra error rojo y `return`.
     - Si pickup == delivery → error rojo y `return`.
     - Genera `orderId = "ORD-" + String.format("%03d", orderCounter[0]++)`. Formato de tres dígitos con cero a la izquierda.
     - Parte cada cadena `"r,c"` por la coma con `.split(",")`.
     - Construye JSON manual con `String.format`: `{"id":"...","pickup":[r,c],"delivery":[r,c]}`. Se evita una librería JSON externa para no inflar dependencias.
     - Llama a `mqtt.publish(TOPIC_ORDERS, json)`.
     - Si OK: añade `"ORD-001 (1,2) -> (4,3)"` a `queueItems` y `"ORD-001"` a `queuedIds`. Resetea combos y muestra confirmación verde.
     - Si excepción MQTT: muestra error rojo con el mensaje.
  5. Crea el `ListView` enlazado a `queueItems` con altura 80 px y lo añade al `VBox` final del panel.

#### Método `buildHistoryPanel()`

- **Propósito:** `ListView` que muestra los pedidos completados, enlazado a `historyItems`. Altura 100 px.

#### Método `buildStatusSemaphorePanel()`

- **Propósito:** construir las tres "luces" del semáforo (pedido recibido / recogido / entregado).
- **Lógica:** crea tres `Label` con `statusDot(text)`, los apila en un `VBox` y los inicializa apagados con `resetSemaphore()`.

#### Método `buildMqttInfoPanel()`

- **Propósito:** mostrar estado de conexión MQTT y la URL del broker con el equipo.
- **Lógica:** dos `Label` apilados en gris claro.

#### Método `startMqtt()`

- **Propósito:** registrar los callbacks del `MqttService` y arrancar la conexión en un hilo separado.
- **Lógica paso a paso:**
  1. `mqtt.setOnStatusMessage(msg -> Platform.runLater(() -> { ... }))` — actualiza `lblMqttStatus` y `lblStatus` desde el JAT.
  2. Registra los tres handlers de topics: `handleMapPayload`, `handleOdometryPayload`, `handleStatusPayload` (referencias a métodos con `::`).
  3. Lanza un `Thread` llamado `"mqtt-connect"` que llama a `mqtt.connect()`. **Imprescindible** que no se ejecute en el JAT, porque `connect()` es bloqueante (handshake TCP + MQTT CONNECT/CONNACK con timeout 10 s) y congelaría la UI.
  4. Si lanza excepción, la propaga al JAT con `Platform.runLater` y la muestra en `lblStatus`.

#### Método `handleMapPayload(String payload)`

- **Propósito:** recibir el mapa, parsearlo y actualizar la UI.
- **Lógica paso a paso:**
  1. Imprime longitud por consola (depuración).
  2. **En el hilo MQTT** parsea `CityMap` (parseo pesado fuera del JAT — buena práctica).
  3. **En `Platform.runLater`**:
     - Guarda el mapa, lo asigna al canvas.
     - Inicializa el `RobotTracker` con la orientación deducida del bloque (6,0) y lo dibuja sobre la casilla inicial.
     - Actualiza `lblMapRaw` con los primeros 40 chars del payload (debug visual).
     - Actualiza `lblStatus` con el número de puntos de recogida y la orientación inicial.
     - Llena los combos con todos los puntos detectados, formateados como `"row,col"`.
  4. Si excepción: muestra "Error al parsear mapa" en la barra de estado.

#### Método `handleOdometryPayload(String payload)`

- **Propósito:** recibir la lista de instrucciones completadas y mover el robot.
- **Lógica:**
  1. Si no hay tracker (no llegó mapa todavía), `return`.
  2. Parsea con `parseInstructionsArray`.
  3. **En el JAT:** `tracker.applyCompleted(done)`, actualiza posición del canvas y muestra "N instrucciones completadas (HEADING)".

#### Método `handleStatusPayload(String payload)`

- **Propósito:** interpretar el mensaje de estado del robot y actualizar el semáforo.
- **Lógica:** parser robusto que acepta tres formatos:
  1. JSON con clave `"status"`: `{"status":"LISTO"}`. Busca `"status"` y extrae el valor entre comillas.
  2. JSON-string puro: `"LISTO"` (con comillas). Quita las comillas.
  3. Texto plano: `LISTO`. Usar tal cual.
- Luego invoca `applyStatus(estado)` en el JAT.

#### Método `applyStatus(String estado)`

- **Propósito:** aplicar la transición visual del semáforo.
- **Lógica (switch expression):**
  - **`PEDIDO_RECIBIDO`**:
    - Si hay un id en `queuedIds`, lo mueve a `activeOrderId` y lo retira de la cola visual.
    - `resetSemaphore()` apaga las tres luces.
    - Pinta `dotPedidoRecibido` en naranja `#FFB300`.
    - Actualiza `lblStatus` con "Pedido X en marcha".
  - **`RECOGIDO`**: pinta `dotRecogido` en azul `#1a5fa5` y actualiza la barra.
  - **`LISTO`**:
    - Pinta `dotListo` en verde oscuro `#3B6D11`.
    - Añade `"ORD-XXX  ENTREGADO"` al inicio de `historyItems` (más reciente arriba).
    - `tracker.commitSnapshot()` — congela posición para el siguiente pedido.
    - Limpia textos del panel "Pedido actual".
  - `default`: ignora estados desconocidos.

#### Método estático privado `parseInstructionsArray(String json)`

- **Propósito:** extraer la lista de strings del JSON `{"instructions":["MOVE","TURN_LEFT",...]}` sin usar una librería.
- **Lógica paso a paso:**
  1. Busca `"instructions"`. Si no aparece, devuelve lista vacía.
  2. Busca el `[` después de la clave y el `]` posterior.
  3. Extrae el cuerpo, lo trimea. Si vacío, devuelve vacío.
  4. Divide por comas y, para cada item, quita comillas envolventes.
  5. Añade los strings no vacíos a la lista.
- **Limitación conocida:** un valor que contuviese una coma o un corchete dentro de comillas rompería el parser. Aceptable porque las instrucciones (`MOVE`, `TURN_LEFT`, etc.) son tokens fijos.

#### Helpers UI

- **`sectionLabel(text)`**: crea una etiqueta de sección en mayúsculas, gris claro, 10 pt.
- **`wrapPanel(VBox inner)`**: aplica fondo blanco, borde redondeado y padding 10 px.
- **`buildLegend()`**: leyenda horizontal con cuatro `legendDot` (edificio rojo, calle verde, calle azul, robot naranja).
- **`legendDot(color, text)`**: cuadradito coloreado + texto.
- **`statusDot(text)`**: etiqueta inicial gris para el semáforo.
- **`resetSemaphore()`**: apaga las tres luces.
- **`paintDotInactive(Label)`** / **`paintDotActive(Label, hex)`**: aplica estilo CSS inline con fondo gris/coloreado y texto negro/blanco.

### 10.4 Preguntas trampa del profesor

**P1 — "¿Por qué construyes el JSON con `String.format` en lugar de usar Jackson o Gson?"**
> Por **minimalismo de dependencias**. El payload es trivial (tres campos, sin anidación profunda, sin tipos exóticos). Añadir Jackson supondría arrastrar 3-4 JARs y una API a la hora de la defensa que no aporta valor. El formato está fijado por el guion, no va a cambiar. Si en producción el protocolo creciera (autenticación, metadatos, validación de esquema), refactorizaría a Jackson sin dudarlo.

**P2 — "El parser de instrucciones es frágil. ¿No es preocupante?"**
> Es **deliberadamente limitado** al formato concreto que publica Pablo. Las instrucciones son tokens fijos de un alfabeto cerrado (`MOVE`, `TURN_LEFT`, `TURN_RIGHT`, `TURN_BACK`, `PICK_UP`, `DELIVER`, `STRAIGHT`); ninguna contiene comas ni corchetes. El parser se documenta en la propia función. Si el protocolo evolucionara a JSON arbitrario, sí necesitaría una librería real.

**P3 — "¿Por qué `Platform.runLater` en cada handler MQTT?"**
> Porque los callbacks de Paho se ejecutan en hilos propios de Paho (no en el JavaFX Application Thread). JavaFX exige que toda modificación del grafo escena (mover el robot, añadir a la `ObservableList`, cambiar el texto de un `Label`) ocurra en el JAT. `Platform.runLater` encola un `Runnable` para que se ejecute en el siguiente tick del JAT. Sin él, lanzaría `IllegalStateException: Not on FX application thread`.

**P4 — "¿Por qué `queuedIds` y `queueItems` en paralelo?"**
> Porque la `ListView` muestra al operario una etiqueta descriptiva (`"ORD-001 (1,2) → (4,3)"`) y necesito conservar el id puro (`"ORD-001"`) para poder retirarlo de la cola e identificarlo como activo cuando llega `PEDIDO_RECIBIDO`. Mantener una segunda lista en orden FIFO es la solución más simple. Una alternativa sería tener una `ObservableList<Order>` con un `CellFactory` que renderice la descripción; lo dejé como mejora futura porque el alcance no lo justifica.

**P5 — "Si el broker tarda 10 segundos en responder, ¿la UI se congela?"**
> No. `mqtt.connect()` se lanza en un `new Thread(..., "mqtt-connect")`, fuera del JAT. La ventana ya está visible y responde a clics; sólo el panel MQTT muestra "Desconectado" hasta que termine el handshake.

**P6 — "¿Qué pasa si el operario pulsa 'Añadir pedido' antes de que llegue el mapa?"**
> El botón siempre está habilitado, pero los `ComboBox` están vacíos hasta que llegue el primer mapa. Si el operario abre los combos y selecciona algo, no podrá porque no hay items. Si no selecciona, el botón muestra "Selecciona recogida y entrega" en rojo. Una mejora sería deshabilitar el botón con `btnAdd.disableProperty().bind(Bindings.isEmpty(cbPickup.getItems()))`.

**P7 — "El parser de status acepta tres formatos. ¿Por qué tanta flexibilidad?"**
> Porque durante la integración con el firmware de Pablo, los formatos exactos cambiaron varias veces. El guion dice "texto plano", pero algunos prototipos publicaban JSON-string o JSON objeto. Aceptar las tres variantes nos blinda contra cualquier mensaje compatible que llegue desde el robot, sin depender de la versión exacta del firmware.

---

## Anexo — Preguntas globales del profesor

**G1 — "¿Cómo aseguras que el dashboard sigue funcionando si el robot se reinicia a mitad de pedido?"**
> El robot al reiniciarse pierde su contador de instrucciones, pero **no su posición física**: sigue donde estaba. El tracker se basa en `snapshotRow/Col` que sólo se actualiza con `LISTO`. Si el robot reenvía una lista parcial nueva, `applyCompleted` la reaplica desde el snapshot y el cálculo es correcto. Si publica `PEDIDO_RECIBIDO` por error, sólo cambia el semáforo, no la posición. Pérdida total = aceptable porque el operario lo nota visualmente y puede reiniciar manualmente.

**G2 — "¿Cómo escalarías esto a varios robots simultáneamente?"**
> Cambiaría los topics a `Equipo E/<robotId>/odometry`, `Equipo E/<robotId>/status`, y suscribiría con wildcard `Equipo E/+/odometry`. En `MqttService.messageArrived` extraería el `robotId` del nombre del topic. En el dashboard tendría un `Map<String, RobotTracker>` y dibujaría un círculo por robot, cada uno con un color distinto. La cola de pedidos podría asignar robots por proximidad/disponibilidad.

**G3 — "¿Qué patrones de diseño identificas en el código?"**
> - **Observer/Callback** en `MqttService` con `Consumer<String>` para desacoplar transporte y UI.
> - **MVC informal**: `CityMap`/`Tile`/`RobotTracker`/`Order` son el modelo, `MapCanvas` es la vista, `DashboardController` es el controlador.
> - **Snapshot/Memento** en `RobotTracker.commitSnapshot()` para conservar el estado al inicio de cada pedido y permitir el cálculo idempotente.
> - **Strategy** implícito en `MapCanvas.drawTile`: si hay imagen usa rasterización, si no, dibujo vectorial.

**G4 — "¿Qué tests automatizados pondrías?"**
> - `Tile`: verificar conexiones para los 12 códigos y que `fromCode(99)` lance excepción.
> - `CityMap`: parsear un payload conocido y comprobar la rejilla y los pickup points.
> - `RobotTracker`: dada una posición inicial y una lista de instrucciones, comprobar el resultado; comprobar idempotencia aplicando la misma lista dos veces; comprobar `commitSnapshot`.
> - `DashboardController.parseInstructionsArray`: corner cases (vacío, sin clave, valores con espacios).
> - `MqttService`: integración contra un broker embebido (Moquette) verificando publish/subscribe.

**G5 — "¿Qué problemas de seguridad ves?"**
> - MQTT sin autenticación ni TLS (puerto 1883 plano). En una red doméstica o de laboratorio es aceptable; en producción usaría `tcps://` y credenciales con `MqttConnectOptions.setUserName/setPassword`.
> - Inyección en `String.format` de las coordenadas: están parseadas de un `ComboBox` cuyos valores los aportamos nosotros desde `pickupPoints`, no del usuario libre. Riesgo nulo.
> - Sin validación de longitud del payload de odometría: un atacante podría enviar una lista enorme. La JVM tragaría memoria. En producción limitaría tamaño máximo del mensaje en el broker.

**G6 — "¿Qué mejoras de rendimiento podrías introducir?"**
> - **Dirty redraw**: actualmente cada cambio de posición repinta los 35 tiles. Podría mantener un `WritableImage` con el mapa pre-pintado y, en cada frame, sólo borrar la celda anterior del robot y pintar la nueva.
> - **`Image` cacheada**: ya se hace en `tileImages`, pero las `drawImage` redimensionan en CPU. Si el tamaño del canvas no cambia, precomputar versiones escaladas como `WritableImage` evitaría escalado por frame.
> - **Worker pool MQTT**: si llegara un volumen alto de mensajes, los handlers podrían encolarse y procesarse en lotes en el JAT con `runLater` agrupado.

---

*Documento elaborado para defensa académica del módulo Clarence — Telepizza Dashboard. Mayo 2026.*
