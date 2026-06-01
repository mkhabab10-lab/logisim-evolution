/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

/**
 * Allows immutable objects to be cached in memory in order to reduce the creation of duplicate
 * objects.
 * Optimized to support massive datasets such as 512-bit values in Logisim.
 */
public class Cache {
  private final int mask;
  private final Object[] data;

  // تم رفع الحجم الافتراضي من 8 (256 عنصر) إلى 11 (2048 عنصر) لتفادي التضارب المبكر
  public Cache() {
    this(11);
  }

  public Cache(int logSize) {
    // تم رفع الحد الأقصى من 12 (4096 عنصر) إلى 16 (65,536 عنصر)
    // هذا الحجم مثالي جداً لاستيعاب دفق بيانات الـ 512 بت دون استهلاك مفرط للـ RAM
    if (logSize > 16) logSize = 16;
    if (logSize < 4) logSize = 4; // حد أدنى آمن

    data = new Object[1 << logSize];
    mask = data.length - 1;
  }

  public Object get(int hashCode) {
    // استخدام دالة تحسين إضافية (bitwise spread) لتوزيع الـ Hash السلبي والموجب بشكل عادل على المصفوفة
    return data[(hashCode ^ (hashCode >>> 16)) & mask];
  }

  public Object get(Object value) {
    if (value == null) return null;
    int hashCode = value.hashCode();
    int code = (hashCode ^ (hashCode >>> 16)) & mask;
    final var ret = data[code];
    if (ret != null && ret.equals(value)) {
      return ret;
    } else {
      data[code] = value;
      return value;
    }
  }

  public void put(int hashCode, Object value) {
    if (value != null) {
      data[(hashCode ^ (hashCode >>> 16)) & mask] = value;
    }
  }
}
