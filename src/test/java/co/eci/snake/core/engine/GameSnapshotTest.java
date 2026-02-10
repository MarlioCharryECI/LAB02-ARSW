package co.eci.snake.core.engine;

import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameSnapshotTest {

    private Snake snake1;
    private Snake snake2;
    private Snake snake3;

    @BeforeEach
    void setUp() {
        snake1 = Snake.of(1, 1, Direction.RIGHT);
        snake2 = Snake.of(5, 5, Direction.UP);
        snake3 = Snake.of(10, 10, Direction.LEFT);
        
        // Hacer crecer algunas serpientes
        snake1.advance(new Position(2, 1), true);
        snake1.advance(new Position(3, 1), true);
        snake1.advance(new Position(4, 1), true); // longitud 4
        
        snake2.advance(new Position(5, 4), true);
        snake2.advance(new Position(5, 3), true); // longitud 3
        
        snake3.advance(new Position(9, 10), false); // longitud 1
    }

    @Test
    @DisplayName("Debería crear snapshot con datos correctos")
    void shouldCreateSnapshotWithCorrectData() {
        List<Snake> snakes = List.of(snake1, snake2, snake3);
        
        GameSnapshot snapshot = new GameSnapshot(snakes, snake1, snake3);
        
        assertEquals(snakes, snapshot.snakes);
        assertEquals(snake1, snapshot.longestAlive);
        assertEquals(snake3, snapshot.worstSnake);
    }

    @Test
    @DisplayName("Debería manejar valores nulos correctamente")
    void shouldHandleNullValuesCorrectly() {
        List<Snake> snakes = List.of(snake1, snake2);
        
        GameSnapshot snapshot = new GameSnapshot(snakes, null, null);
        
        assertEquals(snakes, snapshot.snakes);
        assertNull(snapshot.longestAlive);
        assertNull(snapshot.worstSnake);
    }

    @Test
    @DisplayName("Debería manejar lista vacía de serpientes")
    void shouldHandleEmptySnakeList() {
        List<Snake> emptyList = List.of();
        
        GameSnapshot snapshot = new GameSnapshot(emptyList, null, null);
        
        assertTrue(snapshot.snakes.isEmpty());
        assertNull(snapshot.longestAlive);
        assertNull(snapshot.worstSnake);
    }

    @Test
    @DisplayName("Debería identificar correctamente la serpiente más larga")
    void shouldCorrectlyIdentifyLongestSnake() {
        List<Snake> snakes = List.of(snake1, snake2, snake3);
        
        // snake1 tiene longitud 4, snake2 tiene 3, snake3 tiene 1
        GameSnapshot snapshot = new GameSnapshot(snakes, snake1, snake3);
        
        assertEquals(snake1, snapshot.longestAlive);
        assertEquals(4, snapshot.longestAlive.length());
    }

    @Test
    @DisplayName("Debería manejar serpientes muertas")
    void shouldHandleDeadSnakes() {
        snake1.markDead(1);
        snake2.markDead(2);
        snake3.markDead(3);
        
        List<Snake> snakes = List.of(snake1, snake2, snake3);
        GameSnapshot snapshot = new GameSnapshot(snakes, snake2, snake1);
        
        assertFalse(snapshot.longestAlive.isAlive());
        assertFalse(snapshot.worstSnake.isAlive());
        assertEquals(2, snapshot.longestAlive.deathOrder());
        assertEquals(1, snapshot.worstSnake.deathOrder());
    }

    @Test
    @DisplayName("Debería ser inmutable")
    void shouldBeImmutable() {
        List<Snake> originalSnakes = List.of(snake1, snake2);
        GameSnapshot snapshot = new GameSnapshot(originalSnakes, snake1, snake2);
        
        // Intentar modificar la lista original
        List<Snake> newSnakes = List.of(snake3);
        
        // El snapshot no debería cambiar
        assertEquals(originalSnakes, snapshot.snakes);
        assertNotEquals(newSnakes, snapshot.snakes);
    }

    @Test
    @DisplayName("Debería manejar múltiples serpientes con misma longitud")
    void shouldHandleMultipleSnakesWithSameLength() {
        // Crear serpientes con misma longitud
        Snake snakeA = Snake.of(0, 0, Direction.RIGHT);
        Snake snakeB = Snake.of(5, 5, Direction.RIGHT);
        
        snakeA.advance(new Position(1, 0), true);
        snakeA.advance(new Position(2, 0), true); // longitud 3
        
        snakeB.advance(new Position(6, 5), true);
        snakeB.advance(new Position(7, 5), true); // longitud 3
        
        List<Snake> snakes = List.of(snakeA, snakeB);
        
        // Cualquiera podría ser la "más larga" si tienen misma longitud
        GameSnapshot snapshot = new GameSnapshot(snakes, snakeA, snakeB);
        
        assertEquals(snakeA, snapshot.longestAlive);
        assertEquals(3, snapshot.longestAlive.length());
    }

    @Test
    @DisplayName("Debería mantener consistencia en escenarios complejos")
    void shouldMaintainConsistencyInComplexScenarios() {
        // Marcar algunas serpientes como muertas
        snake1.markDead(2);
        snake3.markDead(1);
        
        List<Snake> snakes = List.of(snake1, snake2, snake3);
        GameSnapshot snapshot = new GameSnapshot(snakes, snake2, snake3);
        
        // Verificar consistencia
        assertEquals(3, snapshot.snakes.size());
        assertTrue(snake2.isAlive()); // snake2 sigue viva
        assertFalse(snake1.isAlive()); // snake1 está muerta
        assertFalse(snake3.isAlive()); // snake3 está muerta
        
        // snake3 es la peor (murió primero)
        assertEquals(snake3, snapshot.worstSnake);
        assertEquals(1, snapshot.worstSnake.deathOrder());
        
        // snake2 es la más larga viva
        assertEquals(snake2, snapshot.longestAlive);
        assertTrue(snapshot.longestAlive.isAlive());
    }

    @Test
    @DisplayName("Debería manejar concurrencia en creación")
    void shouldHandleConcurrencyInCreation() throws InterruptedException {
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        GameSnapshot[] snapshots = new GameSnapshot[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                Snake localSnake = Snake.of(index, index, Direction.RIGHT);
                localSnake.advance(new Position(index + 1, index), true);
                
                List<Snake> localSnakes = List.of(localSnake, snake1);
                snapshots[index] = new GameSnapshot(localSnakes, localSnake, snake1);
            });
        }
        
        // Iniciar todos los hilos
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Esperar a que todos terminen
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verificar que todos los snapshots se crearon correctamente
        for (GameSnapshot snapshot : snapshots) {
            assertNotNull(snapshot);
            assertEquals(2, snapshot.snakes.size());
            assertNotNull(snapshot.longestAlive);
            assertNotNull(snapshot.worstSnake);
        }
    }

    @Test
    @DisplayName("Debería mantener consistencia bajo acceso concurrente")
    void shouldMaintainConsistencyUnderConcurrentAccess() throws InterruptedException {
        List<Snake> snakes = List.of(snake1, snake2, snake3);
        GameSnapshot snapshot = new GameSnapshot(snakes, snake1, snake3);
        
        int numThreads = 20;
        Thread[] threads = new Thread[numThreads];
        
        // Hilos que acceden concurrentemente al snapshot
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                // Acceder a los datos del snapshot
                List<Snake> snakeList = snapshot.snakes;
                Snake longest = snapshot.longestAlive;
                Snake worst = snapshot.worstSnake;
                
                // Verificar consistencia básica
                assertNotNull(snakeList);
                assertEquals(3, snakeList.size());
                
                if (longest != null) {
                    assertTrue(longest.length() >= 1);
                }
                
                if (worst != null) {
                    assertTrue(worst.length() >= 1);
                }
                
                try {
                    Thread.sleep(1); // Pequeña pausa
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // Iniciar todos los hilos
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Esperar a que todos terminen
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verificar que el snapshot original permaneció consistente
        assertEquals(snakes, snapshot.snakes);
        assertEquals(snake1, snapshot.longestAlive);
        assertEquals(snake3, snapshot.worstSnake);
    }
}
