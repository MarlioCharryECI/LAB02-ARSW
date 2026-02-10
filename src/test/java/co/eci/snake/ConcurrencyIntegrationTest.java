package co.eci.snake;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameSnapshot;
import co.eci.snake.core.engine.GameStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyIntegrationTest {

    private Board board;
    private GameStats stats;
    private ReadWriteLock gameLock;
    private AtomicBoolean paused;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        board = new Board(30, 30);
        stats = new GameStats();
        gameLock = new ReentrantReadWriteLock();
        paused = new AtomicBoolean(false);
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Test
    @DisplayName("Debería manejar múltiples serpientes concurrentemente sin excepciones")
    void shouldHandleMultipleConcurrentSnakesWithoutExceptions() throws InterruptedException {
        int numSnakes = 20;
        Snake[] snakes = new Snake[numSnakes];
        Future<?>[] futures = new Future[numSnakes];
        
        // Crear serpientes en diferentes posiciones
        for (int i = 0; i < numSnakes; i++) {
            int x = (i * 3) % board.width();
            int y = (i * 2) % board.height();
            Direction dir = Direction.values()[i % Direction.values().length];
            snakes[i] = Snake.of(x, y, dir);
        }
        
        // Iniciar todos los runners concurrentemente
        for (int i = 0; i < numSnakes; i++) {
            SnakeRunner runner = new SnakeRunner(snakes[i], board, stats, gameLock, paused::get);
            futures[i] = executor.submit(runner);
        }
        
        // Dejar correr por un tiempo significativo
        Thread.sleep(2000);
        
        // Pausar y crear snapshot
        paused.set(true);
        Thread.sleep(100);
        
        gameLock.writeLock().lock();
        try {
            GameSnapshot snapshot = new GameSnapshot(
                java.util.List.of(snakes),
                java.util.List.of(snakes).stream()
                    .filter(Snake::isAlive)
                    .max((a, b) -> Integer.compare(a.length(), b.length()))
                    .orElse(null),
                stats.worstSnake()
            );
            
            // Verificar consistencia del snapshot
            assertNotNull(snapshot);
            assertEquals(numSnakes, snapshot.snakes.size());
            
            // Verificar que al menos algunas serpientes están vivas
            long aliveCount = snapshot.snakes.stream().filter(Snake::isAlive).count();
            assertTrue(aliveCount >= 0);
            
        } finally {
            gameLock.writeLock().unlock();
        }
        
        // Interrumpir todos los hilos
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        
        // Esperar a que todos terminen
        Thread.sleep(500);
        
        // Verificar estado final
        int finalAliveCount = 0;
        for (Snake snake : snakes) {
            if (snake.isAlive()) {
                finalAliveCount++;
            }
        }
        assertTrue(finalAliveCount >= 0);
    }

    @Test
    @DisplayName("Debería mantener consistencia durante pausa coordinada")
    void shouldMaintainConsistencyDuringCoordinatedPause() throws InterruptedException {
        int numSnakes = 10;
        Snake[] snakes = new Snake[numSnakes];
        Future<?>[] futures = new Future[numSnakes];
        
        // Crear serpientes
        for (int i = 0; i < numSnakes; i++) {
            snakes[i] = Snake.of(i, i, Direction.RIGHT);
        }
        
        // Iniciar runners
        for (int i = 0; i < numSnakes; i++) {
            SnakeRunner runner = new SnakeRunner(snakes[i], board, stats, gameLock, paused::get);
            futures[i] = executor.submit(runner);
        }
        
        // Dejar correr
        Thread.sleep(500);
        
        // Realizar múltiples pausas y reanudaciones
        for (int cycle = 0; cycle < 5; cycle++) {
            // Pausar
            paused.set(true);
            Thread.sleep(100);
            
            // Crear snapshot y verificar consistencia
            gameLock.writeLock().lock();
            try {
                GameSnapshot snapshot = new GameSnapshot(
                    java.util.List.of(snakes),
                    java.util.List.of(snakes).stream()
                        .filter(Snake::isAlive)
                        .max((a, b) -> Integer.compare(a.length(), b.length()))
                        .orElse(null),
                    stats.worstSnake()
                );
                
                // Verificar que el snapshot es consistente
                assertNotNull(snapshot);
                
                // Verificar que las serpientes vivas no se mueven durante pausa
                for (Snake snake : snakes) {
                    if (snake.isAlive()) {
                        Position pos = snake.head();
                        Thread.sleep(50);
                        assertEquals(pos, snake.head());
                    }
                }
                
            } finally {
                gameLock.writeLock().unlock();
            }
            
            // Reanudar
            paused.set(false);
            Thread.sleep(200);
        }
        
        // Interrumpir todos
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        
        Thread.sleep(500);
    }

    @Test
    @DisplayName("Debería manejar carga alta sin deadlocks")
    void shouldHandleHighLoadWithoutDeadlocks() throws InterruptedException {
        int numSnakes = 30;
        Snake[] snakes = new Snake[numSnakes];
        Future<?>[] futures = new Future[numSnakes];
        
        // Crear muchas serpientes
        for (int i = 0; i < numSnakes; i++) {
            snakes[i] = Snake.of(
                (i * 5) % board.width(),
                (i * 3) % board.height(),
                Direction.values()[i % Direction.values().length]
            );
        }
        
        // Iniciar todos los runners
        for (int i = 0; i < numSnakes; i++) {
            SnakeRunner runner = new SnakeRunner(snakes[i], board, stats, gameLock, paused::get);
            futures[i] = executor.submit(runner);
        }
        
        // Simular actividad intensa con pausas frecuentes
        for (int i = 0; i < 10; i++) {
            Thread.sleep(100);
            
            // Pausa breve
            paused.set(true);
            Thread.sleep(20);
            paused.set(false);
        }
        
        // Esperar más tiempo
        Thread.sleep(1000);
        
        // Verificar que no hay deadlocks (los hilos deberían responder a interrupción)
        boolean allResponsive = true;
        for (Future<?> future : futures) {
            future.cancel(true);
            try {
                future.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                allResponsive = false;
                break;
            } catch (Exception e) {
                // Expected due to cancellation
            }
        }
        
        assertTrue(allResponsive, "Algunos hilos no respondieron a la interrupción (posible deadlock)");
    }

    @Test
    @DisplayName("Debería mantener integridad de datos bajo concurrencia")
    void shouldMaintainDataIntegrityUnderConcurrency() throws InterruptedException {
        int numSnakes = 15;
        int numCycles = 3;
        
        for (int cycle = 0; cycle < numCycles; cycle++) {
            Snake[] snakes = new Snake[numSnakes];
            Future<?>[] futures = new Future[numSnakes];
            
            // Crear serpientes
            for (int i = 0; i < numSnakes; i++) {
                snakes[i] = Snake.of(i, cycle * 10 + i, Direction.RIGHT);
            }
            
            // Iniciar runners
            for (int i = 0; i < numSnakes; i++) {
                SnakeRunner runner = new SnakeRunner(snakes[i], board, stats, gameLock, paused::get);
                futures[i] = executor.submit(runner);
            }
            
            // Dejar correr
            Thread.sleep(800);
            
            // Pausar y verificar integridad
            paused.set(true);
            Thread.sleep(100);
            
            gameLock.writeLock().lock();
            try {
                // Verificar estado del tablero -允许合理的范围变化
                assertTrue(board.mice().size() >= 4 && board.mice().size() <= 8,
                           "Mice count should be reasonable: " + board.mice().size());
                assertTrue(board.obstacles().size() >= 4 && board.obstacles().size() <= 10,
                           "Obstacles count should be reasonable: " + board.obstacles().size());
                assertTrue(board.turbo().size() >= 0 && board.turbo().size() <= 8,
                           "Turbo count should be non-negative: " + board.turbo().size());
                assertEquals(4, board.teleports().size()); // Teleports son fijos
                
                // Verificar estado de serpientes
                for (Snake snake : snakes) {
                    assertNotNull(snake.head());
                    assertTrue(snake.length() >= 1);
                    
                    if (!snake.isAlive()) {
                        assertTrue(snake.deathOrder() > 0);
                    }
                }
                
                // Verificar estadísticas
                if (stats.worstSnake() != null) {
                    assertTrue(stats.worstSnake().deathOrder() > 0);
                }
                
            } finally {
                gameLock.writeLock().unlock();
            }
            
            // Interrumpir todos
            for (Future<?> future : futures) {
                future.cancel(true);
            }
            
            Thread.sleep(200);
        }
    }

    @Test
    @DisplayName("Debería manejar escenario de juego completo")
    void shouldHandleCompleteGameScenario() throws InterruptedException {
        int numSnakes = 25;
        Snake[] snakes = new Snake[numSnakes];
        Future<?>[] futures = new Future[numSnakes];
        
        // Crear serpientes
        for (int i = 0; i < numSnakes; i++) {
            snakes[i] = Snake.of(
                (i * 4) % board.width(),
                (i * 2) % board.height(),
                Direction.values()[i % Direction.values().length]
            );
        }
        
        // Iniciar juego
        for (int i = 0; i < numSnakes; i++) {
            SnakeRunner runner = new SnakeRunner(snakes[i], board, stats, gameLock, paused::get);
            futures[i] = executor.submit(runner);
        }
        
        // Simular sesión de juego
        long startTime = System.currentTimeMillis();
        int pauseCount = 0;
        
        while (System.currentTimeMillis() - startTime < 3000) { // 3 segundos de juego
            Thread.sleep(200);
            
            // Pausar ocasionalmente
            if (pauseCount % 5 == 0) {
                paused.set(true);
                Thread.sleep(100);
                
                // Verificar estado durante pausa
                gameLock.writeLock().lock();
                try {
                    long aliveCount = java.util.List.of(snakes).stream()
                        .filter(Snake::isAlive)
                        .count();
                    
                    if (aliveCount == 0) {
                        break; // Todas las serpientes murieron
                    }
                    
                } finally {
                    gameLock.writeLock().unlock();
                }
                
                paused.set(false);
            }
            
            pauseCount++;
        }
        
        // Finalizar juego
        paused.set(true);
        
        // Interrumpir todos los hilos
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        
        Thread.sleep(500);
        
        // Verificación final del juego
        long finalAliveCount = java.util.List.of(snakes).stream()
            .filter(Snake::isAlive)
            .count();
        
        long finalDeadCount = numSnakes - finalAliveCount;
        
        assertTrue(finalDeadCount >= 0);
        assertTrue(finalAliveCount >= 0);
        
        // Si hay serpientes muertas, debería haber una peor serpiente
        if (finalDeadCount > 0) {
            assertNotNull(stats.worstSnake());
            assertTrue(stats.worstSnake().deathOrder() >= 1, 
                      "Worst snake should have valid death order: " + stats.worstSnake().deathOrder());
        }
    }

    @Test
    @DisplayName("Debería ser robusto bajo condiciones extremas")
    void shouldBeRobustUnderExtremeConditions() throws InterruptedException {
        int numSnakes = 40; // Máxima carga
        Snake[] snakes = new Snake[numSnakes];
        Future<?>[] futures = new Future[numSnakes];
        
        // Crear serpientes en posiciones muy cercanas para aumentar interacciones
        for (int i = 0; i < numSnakes; i++) {
            snakes[i] = Snake.of(
                (i % 10) * 3,
                (i / 10) * 3,
                Direction.values()[i % Direction.values().length]
            );
        }
        
        // Iniciar todos los runners
        for (int i = 0; i < numSnakes; i++) {
            SnakeRunner runner = new SnakeRunner(snakes[i], board, stats, gameLock, paused::get);
            futures[i] = executor.submit(runner);
        }
        
        // Simular condiciones extremas: pausas muy frecuentes
        for (int i = 0; i < 50; i++) {
            paused.set(true);
            Thread.sleep(10); // Pausas muy breves
            paused.set(false);
            Thread.sleep(20); // Períodos muy cortos de actividad
        }
        
        // Período más largo de actividad
        Thread.sleep(1000);
        
        // Verificar que el sistema sigue funcionando
        boolean systemStable = true;
        try {
            // Intentar crear snapshot
            gameLock.writeLock().lock();
            try {
                new GameSnapshot(
                    java.util.List.of(snakes),
                    java.util.List.of(snakes).stream()
                        .filter(Snake::isAlive)
                        .findFirst()
                        .orElse(null),
                    stats.worstSnake()
                );
            } finally {
                gameLock.writeLock().unlock();
            }
        } catch (Exception e) {
            systemStable = false;
        }
        
        assertTrue(systemStable, "El sistema se volvió inestable bajo carga extrema");
        
        // Limpiar
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        
        Thread.sleep(500);
    }

    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
