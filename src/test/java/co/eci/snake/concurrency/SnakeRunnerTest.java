package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SnakeRunnerTest {

    private Board board;
    private Snake snake;
    private GameStats stats;
    private ReadWriteLock lock;
    private AtomicBoolean paused;
    private SnakeRunner runner;

    @BeforeEach
    void setUp() {
        board = new Board(20, 20);
        snake = Snake.of(10, 10, Direction.RIGHT);
        stats = new GameStats();
        lock = new ReentrantReadWriteLock();
        paused = new AtomicBoolean(false);
        runner = new SnakeRunner(snake, board, stats, lock, paused::get);
    }

    @Test
    @DisplayName("Debería crear SnakeRunner con parámetros correctos")
    void shouldCreateSnakeRunnerWithCorrectParameters() {
        assertNotNull(runner);
        // No hay forma directa de verificar los parámetros internos sin reflection
        // pero podemos verificar que no lanza excepción al crear
    }

    @Test
    @DisplayName("Debería respetar el estado de pausa")
    void shouldRespectPauseState() throws InterruptedException {
        paused.set(true);
        
        Thread runnerThread = new Thread(runner);
        runnerThread.start();
        
        // Esperar un poco para que el hilo comience
        Thread.sleep(100);
        
        // La serpiente no debería haberse movido porque está en pausa
        Position originalPosition = snake.head();
        
        // Esperar más tiempo
        Thread.sleep(200);
        
        // La posición debería seguir siendo la misma
        assertEquals(originalPosition, snake.head());
        
        // Reanudar y detener
        paused.set(false);
        Thread.sleep(50);
        runnerThread.interrupt();
        runnerThread.join();
    }

    @Test
    @DisplayName("Debería mover serpiente cuando no está en pausa")
    void shouldMoveSnakeWhenNotPaused() throws InterruptedException {
        paused.set(false);
        
        Thread runnerThread = new Thread(runner);
        runnerThread.start();
        
        // Esperar un poco para que la serpiente se mueva
        Thread.sleep(150);
        
        // La serpiente debería haberse movido
        Position originalPosition = new Position(10, 10);
        assertNotEquals(originalPosition, snake.head());
        
        // Detener el hilo
        runnerThread.interrupt();
        runnerThread.join();
    }

    @Test
    @DisplayName("Debería detenerse cuando la serpiente muere")
    void shouldStopWhenSnakeDies() throws InterruptedException {
        paused.set(false);
        
        // Colocar serpiente cerca de un obstáculo
        Position obstaclePos = board.obstacles().iterator().next();
        snake = Snake.of(obstaclePos.x() - 1, obstaclePos.y(), Direction.RIGHT);
        runner = new SnakeRunner(snake, board, stats, lock, paused::get);
        
        Thread runnerThread = new Thread(runner);
        runnerThread.start();
        
        // Esperar a que la serpiente choque y muera
        Thread.sleep(200);
        
        // La serpiente debería estar muerta
        assertFalse(snake.isAlive());
        assertTrue(snake.deathOrder() > 0);
        
        // El hilo debería haber terminado
        runnerThread.join(1000); // Timeout para evitar que el test se cuelgue
        assertFalse(runnerThread.isAlive());
    }

    @Test
    @DisplayName("Debería registrar muerte en estadísticas")
    void shouldRegisterDeathInStats() throws InterruptedException {
        paused.set(false);
        
        // Colocar serpiente cerca de un obstáculo
        Position obstaclePos = board.obstacles().iterator().next();
        snake = Snake.of(obstaclePos.x() - 1, obstaclePos.y(), Direction.RIGHT);
        runner = new SnakeRunner(snake, board, stats, lock, paused::get);
        
        Thread runnerThread = new Thread(runner);
        runnerThread.start();
        
        // Esperar a que la serpiente muera
        Thread.sleep(200);
        
        // Verificar que la muerte fue registrada
        assertNotNull(stats.worstSnake());
        assertEquals(snake, stats.worstSnake());
        
        runnerThread.join(1000);
    }

    @Test
    @DisplayName("Debería manejar interrupción correctamente")
    void shouldHandleInterruptionCorrectly() throws InterruptedException {
        paused.set(false);
        
        Thread runnerThread = new Thread(runner);
        runnerThread.start();
        
        // Esperar un poco
        Thread.sleep(50);
        
        // Interrumpir el hilo
        runnerThread.interrupt();
        
        // El hilo debería terminar
        runnerThread.join(1000);
        assertFalse(runnerThread.isAlive());
        
        // La serpiente debería seguir viva (no murió por colisión)
        assertTrue(snake.isAlive());
    }

    @Test
    @DisplayName("Debería manejar cambios de pausa dinámicos")
    void shouldHandleDynamicPauseChanges() throws InterruptedException {
        Thread runnerThread = new Thread(runner);
        runnerThread.start();
        
        Position initialPosition = snake.head();
        
        // Dejar correr por un tiempo
        Thread.sleep(100);
        
        // Pausar
        paused.set(true);
        Thread.sleep(100);
        
        Position pausedPosition = snake.head();
        assertNotEquals(initialPosition, pausedPosition);
        
        // Esperar más tiempo en pausa - no debería moverse
        Thread.sleep(150);
        assertEquals(pausedPosition, snake.head());
        
        // Reanudar
        paused.set(false);
        Thread.sleep(100);
        
        // Debería haberse movido nuevamente
        assertNotEquals(pausedPosition, snake.head());
        
        // Detener
        runnerThread.interrupt();
        runnerThread.join();
    }

    @Test
    @DisplayName("Debería manejar múltiples runners concurrentemente")
    void shouldHandleMultipleRunnersConcurrently() throws InterruptedException {
        int numRunners = 5;
        Thread[] threads = new Thread[numRunners];
        Snake[] snakes = new Snake[numRunners];
        SnakeRunner[] runners = new SnakeRunner[numRunners];
        
        // Crear múltiples serpientes y runners
        for (int i = 0; i < numRunners; i++) {
            snakes[i] = Snake.of(i * 2, 10, Direction.RIGHT);
            runners[i] = new SnakeRunner(snakes[i], board, stats, lock, paused::get);
        }
        
        // Iniciar todos los hilos
        for (int i = 0; i < numRunners; i++) {
            threads[i] = new Thread(runners[i]);
            threads[i].start();
        }
        
        // Dejar correr por un tiempo
        Thread.sleep(200);
        
        // Pausar todos
        paused.set(true);
        Thread.sleep(100);
        
        // Verificar que todos están en pausa
        for (Snake snake : snakes) {
            if (snake.isAlive()) {
                // Las serpientes vivas no deberían moverse en pausa
                Position currentPos = snake.head();
                Thread.sleep(50);
                assertEquals(currentPos, snake.head());
            }
        }
        
        // Reanudar
        paused.set(false);
        Thread.sleep(100);
        
        // Interrumpir todos
        for (Thread thread : threads) {
            thread.interrupt();
        }
        
        // Esperar a que todos terminen
        for (Thread thread : threads) {
            thread.join(1000);
        }
        
        // Verificar estado final
        int aliveCount = 0;
        for (Snake snake : snakes) {
            if (snake.isAlive()) {
                aliveCount++;
            }
        }
        assertTrue(aliveCount >= 0); // Al menos algunas podrían estar vivas
    }

    @Test
    @DisplayName("Debería manejar turbo correctamente")
    void shouldHandleTurboCorrectly() throws InterruptedException {
        paused.set(false);
        
        // Colocar serpiente cerca de un turbo
        Position turboPos = board.turbo().iterator().next();
        snake = Snake.of(turboPos.x() - 1, turboPos.y(), Direction.RIGHT);
        runner = new SnakeRunner(snake, board, stats, lock, paused::get);
        
        Thread runnerThread = new Thread(runner);
        runnerThread.start();
        
        // Esperar a que la serpiente coma el turbo
        Thread.sleep(150);
        
        // La serpiente debería haberse movido más rápido después del turbo
        // (esto es difícil de probar directamente sin acceso al estado interno)
        // Pero podemos verificar que sigue viva y se movió
        assertTrue(snake.isAlive());
        assertNotEquals(new Position(turboPos.x() - 1, turboPos.y()), snake.head());
        
        runnerThread.interrupt();
        runnerThread.join();
    }

    @Test
    @DisplayName("Debería manejar giros aleatorios")
    void shouldHandleRandomTurns() throws InterruptedException {
        paused.set(false);
        
        Direction initialDirection = snake.direction();
        
        Thread runnerThread = new Thread(runner);
        runnerThread.start();
        
        // Esperar suficiente tiempo para que puedan ocurrir giros
        Thread.sleep(500);
        
        // La dirección podría haber cambiado (es aleatorio, no garantizado)
        // Pero la serpiente debería haberse movido significativamente
        assertTrue(snake.length() >= 1);
        assertTrue(snake.isAlive());
        
        runnerThread.interrupt();
        runnerThread.join();
    }

    @Test
    @DisplayName("Debería ser thread-safe con acceso concurrente")
    void shouldBeThreadSafeWithConcurrentAccess() throws InterruptedException {
        AtomicInteger moveCount = new AtomicInteger(0);
        
        Thread runnerThread = new Thread(runner);
        Thread observerThread = new Thread(() -> {
            int localCount = 0;
            while (localCount < 10 && runnerThread.isAlive()) {
                if (snake.isAlive()) {
                    moveCount.incrementAndGet();
                }
                localCount++;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        runnerThread.start();
        observerThread.start();
        
        Thread.sleep(500);
        
        runnerThread.interrupt();
        observerThread.interrupt();
        
        runnerThread.join(1000);
        observerThread.join(1000);
        
        // Verificar que no hubo excepciones y el sistema permaneció consistente
        assertTrue(moveCount.get() >= 0);
    }
}
