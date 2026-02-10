package co.eci.snake.core.engine;

import co.eci.snake.core.Snake;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class GameStats {

    private final AtomicLong deathCounter = new AtomicLong(0);
    private final ConcurrentHashMap<Snake, Long> deaths = new ConcurrentHashMap<>();

    public long registerDeath(Snake snake) {
        long order = deathCounter.incrementAndGet();
        deaths.putIfAbsent(snake, order);
        return order;
    }

    public Snake worstSnake() {
        return deaths.entrySet()
                .stream()
                .min((a, b) -> Long.compare(a.getValue(), b.getValue()))
                .map(e -> e.getKey())
                .orElse(null);
    }
}
