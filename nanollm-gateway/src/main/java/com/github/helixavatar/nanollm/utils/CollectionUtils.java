package com.github.helixavatar.nanollm.utils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

public class CollectionUtils {

  public static <T> T randomOne(List<T> list, java.util.Random random) {
    return list.get(random.nextInt(list.size()));
  }

  public static <T> T randomOne(List<T> list) {
    return randomOne(list, ThreadLocalRandom.current());
  }

  public static boolean isEmpty(@Nullable Collection<? extends @Nullable Object> collection) {
    return (collection == null || collection.isEmpty());
  }

  public static boolean isNotEmpty(@Nullable Collection<? extends @Nullable Object> collection) {
    return (collection != null && !collection.isEmpty());
  }
}
