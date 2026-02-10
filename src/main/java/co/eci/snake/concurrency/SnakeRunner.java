package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameStats;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.BooleanSupplier;

public final class SnakeRunner implements Runnable {
  private final Snake snake;
  private final Board board;
  private final GameStats stats;
  private final ReadWriteLock lock;

  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;
  private final BooleanSupplier pausedSupplier;

  public SnakeRunner(Snake snake, Board board, GameStats stats, ReadWriteLock lock, BooleanSupplier pausedSupplier) {
    this.snake = snake;
    this.board = board;
    this.stats = stats;
    this.lock = lock;
    this.pausedSupplier = pausedSupplier;
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted() && snake.isAlive()) {

        while (pausedSupplier.getAsBoolean()) {
          Thread.sleep(10);
        }

        lock.readLock().lock();
        try {
          maybeTurn();
          var res = board.step(snake);

          if (res == Board.MoveResult.HIT_OBSTACLE) {
            long order = stats.registerDeath(snake);
            snake.markDead(order);
            break;
          } else if (res == Board.MoveResult.ATE_TURBO) {
            turboTicks = 100;
          }
        } finally {
          lock.readLock().unlock();
        }

        int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
        if (turboTicks > 0) turboTicks--;
        Thread.sleep(sleep);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }


  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
  }

  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }
}
