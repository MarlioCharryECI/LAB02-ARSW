package co.eci.snake.core.engine;

import co.eci.snake.core.Snake;

import java.util.List;

public final class GameSnapshot {

    public final List<Snake> snakes;
    public final Snake longestAlive;
    public final Snake worstSnake;

    public GameSnapshot(List<Snake> snakes, Snake longestAlive, Snake worstSnake) {
        this.snakes = snakes;
        this.longestAlive = longestAlive;
        this.worstSnake = worstSnake;
    }
}
