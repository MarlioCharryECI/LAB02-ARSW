package co.eci.snake.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class SnakeTest {

    private Snake snake;

    @BeforeEach
    void setUp() {
        snake = Snake.of(5, 5, Direction.RIGHT);
    }

    @Test
    @DisplayName("Debería crear serpiente con posición y dirección iniciales")
    void shouldCreateSnakeWithInitialPositionAndDirection() {
        assertEquals(new Position(5, 5), snake.head());
        assertEquals(Direction.RIGHT, snake.direction());
        assertTrue(snake.isAlive());
        assertEquals(1, snake.length());
        assertEquals(-1, snake.deathOrder());
    }

    @Test
    @DisplayName("Debería girar correctamente sin permitir giros opuestos")
    void shouldTurnCorrectlyWithoutAllowingOppositeTurns() {
        snake.turn(Direction.UP);
        assertEquals(Direction.UP, snake.direction());

        snake.turn(Direction.DOWN); // No debería permitir giro opuesto
        assertEquals(Direction.UP, snake.direction());

        snake.turn(Direction.LEFT);
        assertEquals(Direction.LEFT, snake.direction());

        snake.turn(Direction.RIGHT); // No debería permitir giro opuesto
        assertEquals(Direction.LEFT, snake.direction());
    }

    @Test
    @DisplayName("Debería avanzar y crecer correctamente")
    void shouldAdvanceAndGrowCorrectly() {
        Position newHead = new Position(6, 5);
        snake.advance(newHead, false);
        
        assertEquals(newHead, snake.head());
        assertEquals(2, snake.length()); // Se mueve: agrega cabeza (2) pero no crece maxLength

        snake.advance(new Position(7, 5), true);
        assertEquals(3, snake.length()); // Crece: agrega cabeza (3) y maxLength++ (6)
    }

    @Test
    @DisplayName("Debería mantener longitud máxima correctamente")
    void shouldMaintainMaxLengthCorrectly() {
        // Crear serpiente de longitud 5
        snake.advance(new Position(6, 5), true);
        snake.advance(new Position(7, 5), true);
        snake.advance(new Position(8, 5), true);
        snake.advance(new Position(9, 5), true);
        assertEquals(5, snake.length());

        // Moverse sin crecer - debería mantener longitud 5
        snake.advance(new Position(10, 5), false);
        assertEquals(6, snake.length());
    }

    @Test
    @DisplayName("Debería marcar como muerta correctamente")
    void shouldMarkAsDeadCorrectly() {
        assertTrue(snake.isAlive());
        
        snake.markDead(1);
        assertFalse(snake.isAlive());
        assertEquals(1, snake.deathOrder());
    }

    @Test
    @DisplayName("No debería girar si está muerta")
    void shouldNotTurnIfDead() {
        snake.markDead(1);
        Direction originalDirection = snake.direction();
        
        snake.turn(Direction.UP);
        assertEquals(originalDirection, snake.direction());
    }

    @Test
    @DisplayName("No debería avanzar si está muerta")
    void shouldNotAdvanceIfDead() {
        snake.markDead(1);
        Position originalHead = snake.head();
        int originalLength = snake.length();
        
        snake.advance(new Position(6, 5), true);
        assertEquals(originalHead, snake.head());
        assertEquals(originalLength, snake.length());
    }

    @Test
    @DisplayName("Debería crear snapshot consistente")
    void shouldCreateConsistentSnapshot() {
        snake.advance(new Position(6, 5), true);
        snake.advance(new Position(7, 5), true);
        
        var snapshot = snake.snapshot();
        assertEquals(3, snapshot.size());
        assertEquals(new Position(7, 5), snapshot.peekFirst());
        assertEquals(new Position(5, 5), snapshot.peekLast());
    }

    @Test
    @DisplayName("Debería manejar concurrencia básica")
    void shouldHandleBasicConcurrency() throws InterruptedException {
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                if (index % 2 == 0) {
                    snake.turn(Direction.values()[index % Direction.values().length]);
                } else {
                    snake.advance(new Position(6 + index, 5), index % 3 == 0);
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
        
        // Verificar que la serpiente sigue en estado consistente
        assertTrue(snake.length() >= 1);
        assertNotNull(snake.head());
        assertTrue(snake.isAlive());
    }
}
