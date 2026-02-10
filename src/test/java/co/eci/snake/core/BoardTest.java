package co.eci.snake.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    private Board board;
    private Snake snake;

    @BeforeEach
    void setUp() {
        board = new Board(10, 10);
        snake = Snake.of(5, 5, Direction.RIGHT);
    }

    /**
     * Helper method to wait for board state to stabilize in concurrent tests
     */
    private void waitForBoardStabilization() throws InterruptedException {
        Thread.sleep(50); // Small delay to let any pending operations complete
    }

    @Test
    @DisplayName("Debería crear tablero con dimensiones correctas")
    void shouldCreateBoardWithCorrectDimensions() {
        assertEquals(10, board.width());
        assertEquals(10, board.height());
    }

    @Test
    @DisplayName("Debería inicializar con elementos correctos")
    void shouldInitializeWithCorrectElements() {
        assertFalse(board.mice().isEmpty());
        assertFalse(board.obstacles().isEmpty());
        assertFalse(board.turbo().isEmpty());
        assertFalse(board.teleports().isEmpty());
        
        // Verificar que los teletransportadores están en pares
        assertEquals(board.teleports().size() % 2, 0);
    }

    @Test
    @DisplayName("Debería mover serpiente normalmente")
    void shouldMoveSnakeNormally() {
        var result = board.step(snake);
        assertEquals(Board.MoveResult.MOVED, result);
        assertNotEquals(new Position(5, 5), snake.head());
    }

    @Test
    @DisplayName("Debería detectar colisión con obstáculo")
    void shouldDetectObstacleCollision() {
        // Obtener posición de un obstáculo
        Position obstaclePos = board.obstacles().iterator().next();
        
        // Mover serpiente a posición adyacente al obstáculo
        snake = Snake.of(obstaclePos.x() - 1, obstaclePos.y(), Direction.RIGHT);
        
        var result = board.step(snake);
        assertEquals(Board.MoveResult.HIT_OBSTACLE, result);
    }

    @Test
    @DisplayName("Debería permitir comer ratón")
    void shouldAllowEatingMouse() {
        // Obtener posición de un ratón
        Position mousePos = board.mice().iterator().next();
        
        // Mover serpiente a posición adyacente al ratón
        snake = Snake.of(mousePos.x() - 1, mousePos.y(), Direction.RIGHT);
        
        var result = board.step(snake);
        assertEquals(Board.MoveResult.ATE_MOUSE, result);
        
        // Verificar que el ratón fue removido y se agregó uno nuevo
        assertFalse(board.mice().contains(mousePos));
        assertEquals(6, board.mice().size()); // 6 iniciales - 1 comido + 1 nuevo (el conteo de mice siempre se mantiene)
    }

    @Test
    @DisplayName("Debería permitir comer turbo")
    void shouldAllowEatingTurbo() {
        // Obtener posición de un turbo
        Position turboPos = board.turbo().iterator().next();
        
        // Mover serpiente a posición adyacente al turbo
        snake = Snake.of(turboPos.x() - 1, turboPos.y(), Direction.RIGHT);
        
        var result = board.step(snake);
        assertEquals(Board.MoveResult.ATE_TURBO, result);
        
        // Verificar que el turbo fue removido
        assertFalse(board.turbo().contains(turboPos));
    }

    @Test
    @DisplayName("Debería permitir teletransportación")
    void shouldAllowTeleportation() {
        // Obtener par de teletransportadores
        var teleports = board.teleports();
        var entry = teleports.entrySet().iterator().next();
        Position from = entry.getKey();
        Position to = entry.getValue();
        
        // Mover serpiente a posición adyacente al teletransportador de entrada
        snake = Snake.of(from.x() - 1, from.y(), Direction.RIGHT);
        
        var result = board.step(snake);
        assertEquals(Board.MoveResult.TELEPORTED, result);
        assertEquals(to, snake.head());
    }

    @Test
    @DisplayName("Debería manejar wrap-around en bordes")
    void shouldHandleWrapAroundAtEdges() {
        snake = Snake.of(9, 5, Direction.RIGHT);
        var result = board.step(snake);
        
        // Verificar que el wrap-around funcionó (la posición x debería ser 0)
        assertEquals(0, snake.head().x());
        assertEquals(5, snake.head().y());
        
        // El resultado puede variar dependiendo de qué haya en la nueva posición
        assertTrue(result == Board.MoveResult.MOVED || 
                  result == Board.MoveResult.ATE_MOUSE || 
                  result == Board.MoveResult.ATE_TURBO || 
                  result == Board.MoveResult.TELEPORTED,
                  "Expected a valid move result, got: " + result);
        
        // Probar borde inferior
        snake = Snake.of(5, 9, Direction.DOWN);
        result = board.step(snake);
        
        // Verificar que el wrap-around funcionó (la posición y debería ser 0)
        assertEquals(5, snake.head().x());
        assertEquals(0, snake.head().y());
        
        // El resultado puede variar dependiendo de qué haya en la nueva posición
        assertTrue(result == Board.MoveResult.MOVED || 
                  result == Board.MoveResult.ATE_MOUSE || 
                  result == Board.MoveResult.ATE_TURBO || 
                  result == Board.MoveResult.TELEPORTED,
                  "Expected a valid move result, got: " + result);
    }

    @Test
    @DisplayName("Debería crear copias defensivas en accesores")
    void shouldCreateDefensiveCopiesInAccessors() {
        var mice = board.mice();
        var obstacles = board.obstacles();
        var turbo = board.turbo();
        var teleports = board.teleports();
        
        // Modificar las colecciones retornadas
        int originalMiceSize = mice.size();
        int originalObstaclesSize = obstacles.size();
        int originalTurboSize = turbo.size();
        int originalTeleportsSize = teleports.size();
        
        mice.add(new Position(0, 0));
        obstacles.add(new Position(1, 1));
        turbo.add(new Position(2, 2));
        teleports.put(new Position(3, 3), new Position(4, 4));
        
        // Verificar que las colecciones originales no se modificaron
        assertEquals(originalMiceSize, board.mice().size());
        assertEquals(originalObstaclesSize, board.obstacles().size());
        assertEquals(originalTurboSize, board.turbo().size());
        assertEquals(originalTeleportsSize, board.teleports().size());
        
        // Verificar que las colecciones originales no contienen los elementos agregados
        assertFalse(board.mice().contains(new Position(0, 0)));
        assertFalse(board.obstacles().contains(new Position(1, 1)));
        assertFalse(board.turbo().contains(new Position(2, 2)));
        assertFalse(board.teleports().containsKey(new Position(3, 3)));
    }

    @Test
    @DisplayName("Debería lanzar excepción para dimensiones inválidas")
    void shouldThrowExceptionForInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new Board(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new Board(10, 0));
        assertThrows(IllegalArgumentException.class, () -> new Board(-1, 10));
    }

    @Test
    @DisplayName("Debería lanzar excepción para serpiente nula")
    void shouldThrowExceptionForNullSnake() {
        assertThrows(NullPointerException.class, () -> board.step(null));
    }

    @Test
    @DisplayName("Debería manejar concurrencia en step()")
    void shouldHandleConcurrencyInStep() throws InterruptedException {
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        Snake[] snakes = new Snake[numThreads];
        
        // Crear serpientes en diferentes posiciones para minimizar colisiones
        for (int i = 0; i < numThreads; i++) {
            snakes[i] = Snake.of(i, 5, Direction.RIGHT);
        }
        
        // Iniciar hilos que mueven serpientes concurrentemente
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 3; j++) { // Reducir número de movimientos
                        board.step(snakes[index]);
                        Thread.sleep(10);
                    }
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
        
        // Esperar estabilización
        waitForBoardStabilization();
        
        // Verificar que el tablero sigue en estado consistente
        // Los conteos pueden variar debido a la aleatoriedad, pero deben estar en rangos razonables
        assertTrue(board.mice().size() >= 4 && board.mice().size() <= 8, 
                   "Mice count should be reasonable: " + board.mice().size());
        assertTrue(board.obstacles().size() >= 4 && board.obstacles().size() <= 10,
                   "Obstacles count should be reasonable: " + board.obstacles().size());
        assertTrue(board.turbo().size() >= 0 && board.turbo().size() <= 8,
                   "Turbo count should be non-negative: " + board.turbo().size());
        assertEquals(4, board.teleports().size()); // Teleports son fijos
    }
}
