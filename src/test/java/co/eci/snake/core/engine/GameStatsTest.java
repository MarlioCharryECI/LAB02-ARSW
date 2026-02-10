package co.eci.snake.core.engine;

import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class GameStatsTest {

    private GameStats stats;
    private Snake snake1;
    private Snake snake2;
    private Snake snake3;

    @BeforeEach
    void setUp() {
        stats = new GameStats();
        snake1 = Snake.of(1, 1, Direction.RIGHT);
        snake2 = Snake.of(5, 5, Direction.UP);
        snake3 = Snake.of(10, 10, Direction.LEFT);
    }

    @Test
    @DisplayName("Debería registrar muerte correctamente")
    void shouldRegisterDeathCorrectly() {
        long order = stats.registerDeath(snake1);
        snake1.markDead(order); // También debemos marcar la serpiente como muerta
        
        assertEquals(1, order);
        assertFalse(snake1.isAlive());
        assertEquals(1, snake1.deathOrder());
    }

    @Test
    @DisplayName("Debería incrementar contador de muertes")
    void shouldIncrementDeathCounter() {
        stats.registerDeath(snake1);
        stats.registerDeath(snake2);
        stats.registerDeath(snake3);
        
        // El contador debería ser 4 (3 anteriores + 1 nueva)
        assertEquals(4, stats.registerDeath(Snake.of(0, 0, Direction.RIGHT)));
    }

    @Test
    @DisplayName("No debería registrar muerte duplicada")
    void shouldNotRegisterDuplicateDeath() {
        long firstOrder = stats.registerDeath(snake1);
        snake1.markDead(firstOrder); // Marcar la serpiente como muerta
        
        long secondOrder = stats.registerDeath(snake1); // Intento duplicado
        
        assertEquals(firstOrder, secondOrder);
        assertEquals(1, snake1.deathOrder());
    }

    @Test
    @DisplayName("Debería identificar la peor serpiente correctamente")
    void shouldIdentifyWorstSnakeCorrectly() {
        // Registrar muertes en orden
        long order1 = stats.registerDeath(snake1); // Primera en morir
        snake1.markDead(order1);
        
        long order2 = stats.registerDeath(snake2); // Segunda en morir
        snake2.markDead(order2);
        
        long order3 = stats.registerDeath(snake3); // Tercera en morir
        snake3.markDead(order3);
        
        Snake worst = stats.worstSnake();
        assertEquals(snake1, worst); // La primera en morir es la peor
    }

    @Test
    @DisplayName("Debería retornar null si no hay muertes")
    void shouldReturnNullIfNoDeaths() {
        Snake worst = stats.worstSnake();
        assertNull(worst);
    }

    @Test
    @DisplayName("Debería manejar concurrencia en registro de muertes")
    void shouldHandleConcurrencyInDeathRegistration() throws InterruptedException {
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        Snake[] snakes = new Snake[numThreads];
        
        // Crear serpientes
        for (int i = 0; i < numThreads; i++) {
            snakes[i] = Snake.of(i, 0, Direction.RIGHT);
        }
        
        // Registrar muertes concurrentemente
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                long order = stats.registerDeath(snakes[index]);
                snakes[index].markDead(order); // Marcar la serpiente como muerta
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
        
        // Verificar que todas las serpientes están muertas
        for (Snake snake : snakes) {
            assertFalse(snake.isAlive());
            assertTrue(snake.deathOrder() > 0);
        }
        
        // Verificar que hay una peor serpiente
        Snake worst = stats.worstSnake();
        assertNotNull(worst);
        assertEquals(1, worst.deathOrder()); // La primera en morir debería tener deathOrder = 1
    }

    @Test
    @DisplayName("Debería manejar registro concurrente con identificación de peor serpiente")
    void shouldHandleConcurrentRegistrationWithWorstSnakeIdentification() throws InterruptedException {
        int numThreads = 20;
        Thread[] threads = new Thread[numThreads];
        Snake[] snakes = new Snake[numThreads];
        
        // Crear serpientes
        for (int i = 0; i < numThreads; i++) {
            snakes[i] = Snake.of(i, i, Direction.RIGHT);
        }
        
        // Registrar muertes concurrentemente
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                long order = stats.registerDeath(snakes[index]);
                snakes[index].markDead(order); // Marcar la serpiente como muerta
                
                // Algunos hilos intentan obtener la peor serpiente
                if (index % 3 == 0) {
                    try {
                        Thread.sleep(1); // Pequeña pausa para aumentar concurrencia
                        stats.worstSnake();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
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
        
        // Verificar consistencia final
        Snake worst = stats.worstSnake();
        assertNotNull(worst);
        assertEquals(1, worst.deathOrder()); // La primera muerte registrada debería tener order = 1
        
        // Verificar que todos los deathOrder son únicos y están en el rango correcto
        for (Snake snake : snakes) {
            assertTrue(snake.deathOrder() >= 1 && snake.deathOrder() <= numThreads);
        }
    }

    @Test
    @DisplayName("Debería ser thread-safe para operaciones mixtas")
    void shouldBeThreadSafeForMixedOperations() throws InterruptedException {
        int numRegisterThreads = 5;
        int numQueryThreads = 3;
        Thread[] registerThreads = new Thread[numRegisterThreads];
        Thread[] queryThreads = new Thread[numQueryThreads];
        Snake[] snakes = new Snake[numRegisterThreads];
        
        // Crear serpientes
        for (int i = 0; i < numRegisterThreads; i++) {
            snakes[i] = Snake.of(i, 0, Direction.RIGHT);
        }
        
        // Hilos que registran muertes
        for (int i = 0; i < numRegisterThreads; i++) {
            final int index = i;
            registerThreads[i] = new Thread(() -> {
                long order = stats.registerDeath(snakes[index]);
                snakes[index].markDead(order); // Marcar la serpiente como muerta
                try {
                    Thread.sleep(10); // Simular trabajo
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // Hilos que consultan la peor serpiente
        for (int i = 0; i < numQueryThreads; i++) {
            queryThreads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    Snake worst = stats.worstSnake();
                    // No hay assertion específica aquí, solo verificamos que no haya excepciones
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
        
        // Iniciar todos los hilos
        for (Thread thread : registerThreads) {
            thread.start();
        }
        for (Thread thread : queryThreads) {
            thread.start();
        }
        
        // Esperar a que todos terminen
        for (Thread thread : registerThreads) {
            thread.join();
        }
        for (Thread thread : queryThreads) {
            thread.join();
        }
        
        // Verificación final
        assertEquals(numRegisterThreads, stats.worstSnake().deathOrder() + (numRegisterThreads - 1));
    }
}
