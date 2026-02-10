# 🐍 Snake Race — ARSW Lab #2 (Java 21, Virtual Threads)

> **Escuela Colombiana de Ingeniería – Arquitecturas de Software**  
> Laboratorio de programación concurrente: condiciones de carrera, sincronización y colecciones seguras.

**Marlio Jose Charry Espitia**
---

## 🚀 Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## 🎮 Cómo ejecutar

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=4
```

- `-Dsnakes=N` → inicia el juego con **N** serpientes (por defecto 2).
- **Controles**:
  - **Flechas**: serpiente **0** (Jugador 1).
  - **WASD**: serpiente **1** (si existe).
  - **Espacio** o botón **Action**: Pausar / Reanudar.

---

## 📋 Reglas del juego (resumen)

- **N serpientes** corren de forma autónoma (cada una en su propio hilo).
- **Ratones**: al comer uno, la serpiente **crece** y aparece un **nuevo obstáculo**.
- **Obstáculos**: si la cabeza entra en un obstáculo hay **rebote**.
- **Teletransportadores** (flechas rojas): entrar por uno te **saca por su par**.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene **velocidad aumentada** temporal.
- Movimiento con **wrap-around** (el tablero “se repite” en los bordes).

---

## 🏗️ Arquitectura (carpetas)

```
co.eci.snake
├─ app/                 # Bootstrap de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position
├─ core/engine/         # GameClock (ticks, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica por serpiente con virtual threads)
└─ ui/legacy/           # UI estilo legado (Swing) con grilla y botón Action
```

---

# 📚 Actividades del laboratorio

## 🔥 Parte I — (Calentamiento) `wait/notify` en un programa multi-hilo

1. Toma el programa [**PrimeFinder**](https://github.com/ARSW-ECI/wait-notify-excercise).
2. Modifícalo para que **cada _t_ milisegundos**:
   - Se **pausen** todos los hilos trabajadores.
   - Se **muestre** cuántos números primos se han encontrado.
   - El programa **espere ENTER** para **reanudar**.
3. La sincronización debe usar **`synchronized`**, **`wait()`**, **`notify()` / `notifyAll()`** sobre el **mismo monitor** (sin _busy-waiting_).
4. Entrega en el reporte de laboratorio **las observaciones y/o comentarios** explicando tu diseño de sincronización (qué lock, qué condición, cómo evitas _lost wakeups_).

> Objetivo didáctico: practicar suspensión/continuación **sin** espera activa y consolidar el modelo de monitores en Java.

---

## 🎯 Parte II — SnakeRace concurrente (núcleo del laboratorio)

### 1) Análisis de concurrencia

- Explica **cómo** el código usa hilos para dar autonomía a cada serpiente.
RTA/:El sistema implementa un modelo de concurrencia donde cada serpiente opera como una entidad completamente autónoma
mediante su propio hilo de ejecución. En la clase SnakeApp, se utiliza un Executors.newVirtualThreadPerTaskExecutor() 
para crear un pool de hilos virtuales modernos, los cuales son más ligeros que los hilos tradicionales. Cada serpiente
se asocia con una instancia de SnakeRunner que implementa la interfaz Runnable, y este se ejecuta en su propio hilo
mediante exec.submit(new SnakeRunner(s, board)). Dentro del método run() de SnakeRunner, cada serpiente mantiene su 
propio bucle de control independiente que incluye lógica de toma de decisiones autónoma a través del método maybeTurn(),
donde decide aleatoriamente si cambiar de dirección basándose en probabilidades que varían según si está en modo turbo
o no. Además, cada serpiente gestiona su propia velocidad de movimiento mediante el mecanismo de turbo ticks, 
permitiendo que algunas serpientes se muevan más rápido que otras sin afectar el rendimiento general del sistema. 
Esta arquitectura permite que cada serpiente tenga su propio ciclo de vida, ritmo de decisión y comportamiento individual, 
creando un ecosistema donde múltiples entidades pueden coexistir y competir de forma simultánea sin interferencias 
directas en su lógica de control.
- **Identifica** y documenta en **`el reporte de laboratorio`**:
  - Posibles **condiciones de carrera**.
RTA/:existe una condición de carrera potencial en la clase Snake, ya que el método advance() modifica la estructura ArrayDeque que representa el cuerpo de la serpiente sin sincronización, mientras que métodos como snapshot() y head() pueden acceder a la misma estructura concurrentemente, por ejemplo desde el hilo de renderizado.
  - **Colecciones** o estructuras **no seguras** en contexto concurrente.
RTA/:Las colecciones utilizadas en Board (HashSet y HashMap) no son thread-safe, pero su acceso está correctamente protegido mediante métodos sincronizados y copias defensivas. En contraste, la colección ArrayDeque usada en Snake no es thread-safe y no cuenta con mecanismos de sincronización, lo que la convierte en un punto crítico en un entorno concurrente.
  - Ocurrencias de **espera activa** (busy-wait) o de sincronización innecesaria.
RTA/:No se encontraron casos de espera activa en el código. La sincronización aplicada en la clase Board es prudente y apropiada, ya que asegura la consistencia del estado compartido sin introducir complejidad innecesaria en la implementación.

### 2) Correcciones mínimas y regiones críticas

- **Elimina** esperas activas reemplazándolas por **señales** / **estados** o mecanismos de la librería de concurrencia.
- Protege **solo** las **regiones críticas estrictamente necesarias** (evita bloqueos amplios).
- Justifica en **`el reporte de laboratorio`** cada cambio: cuál era el riesgo y cómo lo resuelves.

RTA/:Al sincronizar los métodos head(), snapshot() y advance() de la clase Snake, se garantizó que el acceso al cuerpo de la serpiente sea exclusivo entre hilos, evitando que una operación de lectura ocurra mientras otra está modificando la estructura interna y eliminando así condiciones de carrera y estados inconsistentes. Adicionalmente, en el método step(Snake snake) de la clase Board se redujo el alcance de la sincronización, pasando de un bloqueo de grano grueso a proteger únicamente las secciones que acceden y modifican las colecciones compartidas del tablero. De esta forma, se mantuvo la atomicidad y consistencia del estado del juego, al tiempo que se evitó bloquear innecesariamente operaciones que no interactúan con datos compartidos, mejorando el comportamiento concurrente del sistema.
### 3) Control de ejecución seguro (UI)

- Implementa la **UI** con **Iniciar / Pausar / Reanudar** (ya existe el botón _Action_ y el reloj `GameClock`).
- Al **Pausar**, muestra de forma **consistente** (sin _tearing_):
  - La **serpiente viva más larga**.
  - La **peor serpiente** (la que **primero murió**).
- Considera que la suspensión **no es instantánea**; coordina para que el estado mostrado no quede “a medias”.
- 
RTA/:Se implementó el control de Pausar/Reanudar utilizando el botón Action y el GameClock. Al pausar, el sistema adquiere el writeLock del juego antes de construir el GameSnapshot, garantizando que ninguna serpiente esté avanzando en paralelo y evitando estados inconsistentes (tearing). De esta forma, el overlay muestra de manera consistente tanto la serpiente viva más larga como la peor serpiente (la que murió primero), aun considerando que la suspensión de los hilos no es instantánea.

### 4) Robustez bajo carga

- Ejecuta con **N alto** (`-Dsnakes=20` o más) y/o aumenta la velocidad.
- El juego **no debe romperse**: sin `ConcurrentModificationException`, sin lecturas inconsistentes, sin _deadlocks_.
- Si habilitas **teleports** y **turbo**, verifica que las reglas no introduzcan carreras.

RTA/:El sistema fue validado bajo carga ejecutando el juego con un número alto de serpientes (-Dsnakes=40+) y mayor velocidad. La sincronización centralizada mediante un único ReadWriteLock garantiza que no se presenten ConcurrentModificationException, lecturas inconsistentes ni deadlocks. Las reglas de teleports y turbo se aplican dentro de secciones críticas protegidas, asegurando que no se introduzcan condiciones de carrera incluso con múltiples serpientes ejecutándose en paralelo.
Este se probo ejecutando el juego con -Dsnakes=40 y turbo activo, observando estabilidad, ausencia de excepciones y comportamiento consistente al pausar y reanudar.
> Entregables detallados más abajo.

---

## 📦 Entregables

1. **Código fuente** funcionando en **Java 21**.
2. Todo de manera clara en **`**el reporte de laboratorio**`** con:
   - Data races encontradas y su solución.
   - Colecciones mal usadas y cómo se protegieron (o sustituyeron).
   - Esperas activas eliminadas y mecanismo utilizado.
   - Regiones críticas definidas y justificación de su **alcance mínimo**.
3. UI con **Iniciar / Pausar / Reanudar** y estadísticas solicitadas al pausar.

---

## 📊 Criterios de evaluación (10)

- (3) **Concurrencia correcta**: sin data races; sincronización bien localizada.
- (2) **Pausa/Reanudar**: consistencia visual y de estado.
- (2) **Robustez**: corre **con N alto** y sin excepciones de concurrencia.
- (1.5) **Calidad**: estructura clara, nombres, comentarios; sin _code smells_ obvios.
- (1.5) **Documentación**: **`reporte de laboratorio`** claro, reproducible;

---

## 💡 Tips y configuración útil

- **Número de serpientes**: `-Dsnakes=N` al ejecutar.
- **Tamaño del tablero**: cambiar el constructor `new Board(width, height)`.
- **Teleports / Turbo**: editar `Board.java` (métodos de inicialización y reglas en `step(...)`).
- **Velocidad**: ajustar `GameClock` (tick) o el `sleep` del `SnakeRunner` (incluye modo turbo).

---

## 🧪 Pruebas Unitarias

El proyecto incluye un conjunto completo de pruebas unitarias que validan la funcionalidad y concurrencia del sistema.

### ⚙️ Ejecutar todas las pruebas

```bash
mvn test
```

### 🎯 Ejecutar pruebas específicas

```bash
# Pruebas de una clase específica
mvn test -Dtest=SnakeTest
mvn test -Dtest=BoardTest
mvn test -Dtest=GameStatsTest

# Pruebas de concurrencia
mvn test -Dtest=ConcurrencyIntegrationTest
mvn test -Dtest=SnakeRunnerTest

# Pruebas individuales
mvn test -Dtest=SnakeTest#shouldCreateSnakeWithInitialPositionAndDirection
```

#### **Características de las Pruebas**

- **Thread-Safety**: Todos los tests verifican comportamiento concurrente
- **Robustez**: Manejan la aleatoriedad del sistema con rangos aceptables
- **Integración**: Pruebas end-to-end del sistema completo
- **Determinismo**: Tests diseñados para ser consistentes y reproducibles

#### **Validaciones Clave**

- ✅ **Condiciones de carrera**: Detectadas y prevenidas
- ✅ **Sincronización**: Regiones críticas protegidas correctamente  
- ✅ **Estado consistente**: Snapshots sin tearing
- ✅ **Rendimiento**: Sistema estable bajo alta carga
- ✅ **Robustez**: Sin excepciones inesperadas en concurrencia

---

## 🏃‍♂️ Cómo correr pruebas

```bash
mvn clean verify
```

Incluye compilación y ejecución de pruebas JUnit. Si tienes análisis estático, ejecútalo en `verify` o `site` según tu `pom.xml`.

---

## 👥 Créditos

Este laboratorio es una adaptación modernizada del ejercicio **SnakeRace** de ARSW. El enunciado de actividades se conserva para mantener los objetivos pedagógicos del curso.

**Base construida por el Ing. Javier Toquica.**
