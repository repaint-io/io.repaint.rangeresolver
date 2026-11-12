package io.repaint.rangeresolver.lang;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class Memoizer<T, U> {
  private final Map<T, U> cache = new ConcurrentHashMap<>();

  private Memoizer() {}

  private Function<T, U> doMemoize(Function<T, U> function) {
    return input -> cache.computeIfAbsent(input, function);
  }

  public static <T, U> Function<T, U> memo(Function<T, U> function) {
    return new Memoizer<T, U>().doMemoize(function);
  }
}
