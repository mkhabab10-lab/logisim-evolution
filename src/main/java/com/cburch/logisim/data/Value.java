/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.data;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.circuit.CircuitWires.BusConnection;
import com.cburch.logisim.util.Cache;
import com.cburch.logisim.util.MiniFloat;

import java.awt.Color;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

public final class Value {

  // تحويل المصنع الداخلي ليتعامل مع BigInteger لدعم الـ 512 بت بالكامل
  private static Value create(int width, BigInteger error, BigInteger unknown, BigInteger value) {
    if (width == 0) {
      return Value.NIL;
    } else if (width == 1) {
      if (error.testBit(0)) return Value.ERROR;
      else if (unknown.testBit(0)) return Value.UNKNOWN;
      else if (value.testBit(0)) return Value.TRUE;
      else return Value.FALSE;
    } else {
      BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
      error = error.and(mask);
      unknown = unknown.and(mask).andNot(error);
      value = value.and(mask).andNot(unknown).andNot(error);

      final var hashCode = Value.hashcode(width, error, unknown, value);
      Object cached = cache.get(hashCode);
      if (cached != null) {
        Value val = (Value) cached;
        if (val.width == width
            && val.errorObj.equals(error)
            && val.unknownObj.equals(unknown)
            && val.valueObj.equals(value)) return val;
      }
      final var ret = new Value(width, error, unknown, value);
      cache.put(hashCode, ret);
      return ret;
    }
  }

  // الحفاظ على توافق الدوال القديمة التي تستقبل long من خلال تحويلها تلقائياً إلى BigInteger
  private static Value create(int width, long error, long unknown, long value) {
    return create(width, BigInteger.valueOf(error), BigInteger.valueOf(unknown), BigInteger.valueOf(value));
  }

  public static Value create_unsafe(int width, long error, long unknown, long value) {
    return create_unsafe(width, BigInteger.valueOf(error), BigInteger.valueOf(unknown), BigInteger.valueOf(value));
  }

  private static Value create_unsafe(int width, BigInteger error, BigInteger unknown, BigInteger value) {
    int hashCode = Value.hashcode(width, error, unknown, value);
    Object obj = cache.get(hashCode);
    if (obj != null) {
      Value val = (Value) obj;
      if (val.width == width && val.errorObj.equals(error) && val.unknownObj.equals(unknown) && val.valueObj.equals(value)) {
        return val;
      }
    }
    Value ret = new Value(width, error, unknown, value);
    cache.put(hashCode, ret);
    return ret;
  }

  public static Value create(Value[] values) {
    if (values.length == 0) return NIL;
    if (values.length == 1) return values[0];
    if (values.length > MAX_WIDTH) {
      throw new RuntimeException("Cannot have more than " + MAX_WIDTH + " bits in a value");
    }

    final var width = values.length;
    BigInteger value = BigInteger.ZERO;
    BigInteger unknown = BigInteger.ZERO;
    BigInteger error = BigInteger.ZERO;
    
    for (var i = 0; i < values.length; i++) {
      if (values[i] == TRUE) value = value.setBit(i);
      else if (values[i] == FALSE) /* do nothing */ ;
      else if (values[i] == UNKNOWN) unknown = unknown.setBit(i);
      else if (values[i] == ERROR) error = error.setBit(i);
      else {
        throw new RuntimeException("unrecognized value " + values[i]);
      }
    }
    return Value.create(width, error, unknown, value);
  }

  public static Value createError(BitWidth bits) {
    BigInteger mask = BigInteger.ONE.shiftLeft(bits.getWidth()).subtract(BigInteger.ONE);
    return Value.create(bits.getWidth(), mask, BigInteger.ZERO, BigInteger.ZERO);
  }

  public static Value createUnknown(BitWidth bits) {
    BigInteger mask = BigInteger.ONE.shiftLeft(bits.getWidth()).subtract(BigInteger.ONE);
    return Value.create(bits.getWidth(), BigInteger.ZERO, mask, BigInteger.ZERO);
  }

  public static Value createKnown(BitWidth bits, long value) {
    return Value.create(bits.getWidth(), BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(value));
  }

  public static Value createKnown(float value) {
    return Value.create(32, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(Float.floatToIntBits(value)));
  }

  public static Value createKnown(double value) {
    return Value.create(64, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(Double.doubleToLongBits(value)));
  }

  public static Value createKnown(int bits, long value) {
    return Value.create(bits, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(value));
  }

  public static Value createKnown(BitWidth bits, double value) {
    return createKnown(bits.getWidth(), value);
  }

  public static Value createKnown(int bits, double value) {
    return switch (bits) {
      case 8 -> Value.createKnown(8, MiniFloat.floatToMiniFloat143((float) value));
      case 16 -> Value.createKnown(16, Float.floatToFloat16((float) value));
      case 32 -> Value.createKnown((float) value);
      case 64 -> Value.createKnown(value);
      default -> Value.ERROR;
    };
  }

  public static Value fromLogString(BitWidth width, String t) throws Exception {
    final var sb = new StringBuilder(t.length());
    for (int i = 0; i < t.length(); i++) {
      final var c = t.charAt(i);
      if (c != '_') {
        sb.append(c);
      }
    }
    final var cleaned = sb.toString();
    
    final var radix = radixOfLogString(width, cleaned);
    int offset;

    if (radix == 16 && cleaned.startsWith("0x")) offset = 2;
    else if (radix == 8 && cleaned.startsWith("0o")) offset = 2;
    else if (radix == 2 && cleaned.startsWith("0b")) offset = 2;
    else if (radix == 10 && cleaned.startsWith("-")) offset = 1;
    else offset = 0;

    int n = cleaned.length();
    if (n <= offset) throw new Exception("expected digits");

    int w = width.getWidth();
    BigInteger value = BigInteger.ZERO;
    BigInteger unknown = BigInteger.ZERO;
    BigInteger bigRadix = BigInteger.valueOf(radix);

    for (var i = offset; i < n; i++) {
      final var c = cleaned.charAt(i);
      int d;

      if (c == 'x' && radix != 10) d = -1;
      else if ('0' <= c && c <= '9') d = c - '0';
      else if ('a' <= c && c <= 'f') d = 0xa + (c - 'a');
      else if ('A' <= c && c <= 'F') d = 0xA + (c - 'A');
      else
        throw new Exception("Unexpected character '" + cleaned.charAt(i) + "' in \"" + t + "\"");

      if (d >= radix)
        throw new Exception("Unexpected character '" + cleaned.charAt(i) + "' in \"" + t + "\"");

      value = value.multiply(bigRadix);
      unknown = unknown.multiply(bigRadix);

      if (radix != 10) {
        if (d == -1) unknown = unknown.or(BigInteger.valueOf(radix - 1));
        else value = value.or(BigInteger.valueOf(d));
      } else {
        if (d == -1) unknown = unknown.add(BigInteger.valueOf(radix - 1));
        else value = value.add(BigInteger.valueOf(d));
      }
    }
    if (radix == 10 && cleaned.charAt(0) == '-') {
      value = value.negate();
    }

    // التحقق الآمن من حجم البتات المسموح بها عن طريق طول البتات الفعلي
    if (value.bitLength() > w && !(radix == 10 && value.signum() < 0 && value.negate().minus(BigInteger.ONE).bitLength() <= w - 1)) {
      int actualBits = value.bitLength();
      if (radix == 10) actualBits++; // خانة الإشارة
      throw new Exception("Too many bits in \"" + t + "\" expected " + w + " bits. Did you mean [" + actualBits + "]?");
    }

    BigInteger mask = BigInteger.ONE.shiftLeft(w).subtract(BigInteger.ONE);
    unknown = unknown.and(mask);
    value = value.and(mask);

    return create(w, BigInteger.ZERO, unknown, value);
  }

  public static int radixOfLogString(BitWidth width, String t) {
    if (t.startsWith("0x")) return 16;
    if (t.startsWith("0o")) return 8;
    if (t.startsWith("0b")) return 2;
    if (t.length() == width.getWidth()) return 2;
    return 10;
  }

  public static Value repeat(Value base, BitWidth width) {
    return repeat(base, width.getWidth());
  }

  public static Value repeat(Value base, int bits) {
    if (base.getWidth() != 1) {
      throw new IllegalArgumentException("first parameter must be one bit");
    }
    if (bits == 1) {
      return base;
    } else {
      final var ret = new Value[bits];
      Arrays.fill(ret, base);
      return create(ret);
    }
  }

  private static int hashcode(int width, BigInteger error, BigInteger unknown, BigInteger value) {
    var hashCode = width;
    hashCode = 31 * hashCode + error.hashCode();
    hashCode = 31 * hashCode + unknown.hashCode();
    hashCode = 31 * hashCode + value.hashCode();
    return hashCode;
  }

  private static int hashcode(int width, long error, long unknown, long value) {
    return hashcode(width, BigInteger.valueOf(error), BigInteger.valueOf(unknown), BigInteger.valueOf(value));
  }

  public static char TRUECHAR = AppPreferences.TRUE_CHAR.get().charAt(0);
  public static char FALSECHAR = AppPreferences.FALSE_CHAR.get().charAt(0);
  public static char UNKNOWNCHAR = AppPreferences.UNKNOWN_CHAR.get().charAt(0);
  public static char ERRORCHAR = AppPreferences.ERROR_CHAR.get().charAt(0);
  public static char DONTCARECHAR = AppPreferences.DONTCARE_CHAR.get().charAt(0);
  
  public static final Value FALSE = new Value(1, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
  public static final Value TRUE = new Value(1, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE);
  public static final Value UNKNOWN = new Value(1, BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO);
  public static final Value ERROR = new Value(1, BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO);
  public static final Value NIL = new Value(0, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
  
  // رفع الحد الأقصى رسمياً إلى 512 بت
  public static final int MAX_WIDTH = 512;

  public static Color falseColor = new Color(AppPreferences.FALSE_COLOR.get());
  public static Color trueColor = new Color(AppPreferences.TRUE_COLOR.get());
  public static Color unknownColor = new Color(AppPreferences.UNKNOWN_COLOR.get());
  public static Color errorColor = new Color(AppPreferences.ERROR_COLOR.get());
  public static Color nilColor = new Color(AppPreferences.NIL_COLOR.get());
  public static Color strokeColor = new Color(AppPreferences.STROKE_COLOR.get());
  public static Color multiColor = new Color(AppPreferences.BUS_COLOR.get());
  public static Color widthErrorColor = new Color(AppPreferences.WIDTH_ERROR_COLOR.get());
  public static Color widthErrorCaptionColor = new Color(AppPreferences.WIDTH_ERROR_CAPTION_COLOR.get());
  public static Color widthErrorHighlightColor = new Color(AppPreferences.WIDTH_ERROR_HIGHLIGHT_COLOR.get());
  public static Color widthErrorCaptionBgcolor = new Color(AppPreferences.WIDTH_ERROR_BACKGROUND_COLOR.get());
  public static Color clockFrequencyColor = new Color(AppPreferences.CLOCK_FREQUENCY_COLOR.get());

  private static final Cache cache = new Cache();

  private final int width;
  private final BigInteger errorObj;
  private final BigInteger unknownObj;
  private final BigInteger valueObj;

  private Value(int width, BigInteger error, BigInteger unknown, BigInteger value) {
    this.width = width;
    this.errorObj = error;
    this.unknownObj = unknown;
    this.valueObj = value;
  }

  public Value and(Value other) {
    if (other == null) return this;
    if (this.width == 1 && other.width == 1) {
      if (this == FALSE || other == FALSE) return FALSE;
      if (this == TRUE && other == TRUE) return TRUE;
      return ERROR;
    } else {
      BigInteger false0 = this.valueObj.or(this.errorObj).or(this.unknownObj).not();
      BigInteger false1 = other.valueObj.or(other.errorObj).or(other.unknownObj).not();
      BigInteger falses = false0.or(false1);
      return Value.create(
          Math.max(this.width, other.width),
          this.errorObj.or(other.errorObj).or(this.unknownObj).or(other.unknownObj).andNot(falses),
          BigInteger.ZERO,
          this.valueObj.and(other.valueObj));
    }
  }

  public Value controls(Value other) {
    if (other == null) return null;
    if (this.width == 1) {
      if (this == FALSE) return Value.createUnknown(BitWidth.create(other.width));
      if (this == TRUE || this == UNKNOWN) return other;
      return Value.createError(BitWidth.create(other.width));
    } else if (this.width != other.width) {
      return Value.createError(BitWidth.create(other.width));
    } else {
      BigInteger enabled = this.valueObj.or(this.unknownObj).andNot(this.errorObj);
      BigInteger disabled = this.valueObj.or(this.unknownObj).or(this.errorObj).not();
      return Value.create(other.width,
          this.errorObj.or(other.errorObj.andNot(disabled)),
          disabled.or(other.unknownObj),
          enabled.and(other.valueObj));
    }
  }

  public Value combine(Value other) {
    if (other == null) return this;
    if (this == NIL) return other;
    if (other == NIL) return this;
    if (this.width == 1 && other.width == 1) {
      if (this == other) return this;
      if (this == UNKNOWN) return other;
      if (other == UNKNOWN) return this;
      return ERROR;
    } else if (this.width == other.width) {
      BigInteger disagree = this.valueObj.xor(other.valueObj).andNot(this.unknownObj.or(other.unknownObj));
      return Value.create(
          width,
          this.errorObj.or(other.errorObj).or(disagree),
          this.unknownObj.and(other.unknownObj),
          this.valueObj.or(other.valueObj));
    } else {
      BigInteger thisMask = BigInteger.ONE.shiftLeft(this.width).subtract(BigInteger.ONE);
      BigInteger otherMask = BigInteger.ONE.shiftLeft(other.width).subtract(BigInteger.ONE);
      BigInteger thisKnown = this.unknownObj.not().and(thisMask);
      BigInteger otherKnown = other.unknownObj.not().and(otherMask);
      BigInteger disagree = this.valueObj.xor(other.valueObj).and(thisKnown).and(otherKnown);
      return Value.create(
          Math.max(this.width, other.width),
          this.errorObj.or(other.errorObj).or(disagree),
          thisKnown.not().and(otherKnown.not()),
          this.valueObj.or(other.valueObj));
    }
  }

  public static Value combineLikeWidths(int width, BusConnection[] vals) {
    int n = vals.length;
    for (int i = 0; i < n; i++) {
      Value v = vals[i].drivenValue;
      if (v != null && v != NIL) {
        BigInteger error = v.errorObj;
        BigInteger unknown = v.unknownObj;
        BigInteger value = v.valueObj;
        for (int j = i + 1; j < n; j++) {
          v = vals[j].drivenValue;
          if (v == null || v == NIL) continue;
          if (v.width != width) {
            throw new IllegalArgumentException("INTERNAL ERROR: mismatched widths in Value.combineLikeWidths");
          }
          BigInteger disagree = value.xor(v.valueObj).andNot(unknown.or(v.unknownObj));
          error = error.or(v.errorObj).or(disagree);
          unknown = unknown.and(v.unknownObj);
          value = value.or(v.valueObj);
        }
        return Value.create(width, error, unknown, value);
      }
    }
    return Value.createUnknown(BitWidth.create(width));
  }

  public boolean compatible(Value other) {
    return (this.width == other.width
        && this.errorObj.equals(other.errorObj)
        && this.valueObj.equals(other.valueObj.andNot(this.unknownObj))
        && this.unknownObj.equals(other.unknownObj.or(this.unknownObj)));
  }

  @Override
  public boolean equals(Object otherObj) {
    return (otherObj instanceof Value other)
           ? this.width == other.width
              && this.errorObj.equals(other.errorObj)
              && this.unknownObj.equals(other.unknownObj)
              && this.valueObj.equals(other.valueObj)
           : false;
  }

  public Value extendWidth(int newWidth, Value others) {
    if (width == newWidth) return this;
    BigInteger maskInverse = BigInteger.ONE.shiftLeft(newWidth).subtract(BigInteger.ONE).andNot(BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE));
    if (others == Value.ERROR) {
      return Value.create(newWidth, errorObj.or(maskInverse), unknownObj, valueObj);
    } else if (others == Value.FALSE) {
      return Value.create(newWidth, errorObj, unknownObj, valueObj);
    } else if (others == Value.TRUE) {
      return Value.create(newWidth, errorObj, unknownObj, valueObj.or(maskInverse));
    } else {
      return Value.create(newWidth, errorObj, unknownObj.or(maskInverse), valueObj);
    }
  }

  public Value get(int which) {
    if (which < 0 || which >= width) return ERROR;
    if (errorObj.testBit(which)) return ERROR;
    else if (unknownObj.testBit(which)) return UNKNOWN;
    else if (valueObj.testBit(which)) return TRUE;
    else return FALSE;
  }

  public Value[] getAll() {
    final var ret = new Value[width];
    for (var i = 0; i < ret.length; i++) {
      ret[i] = get(i);
    }
    return ret;
  }

  public BitWidth getBitWidth() {
    return BitWidth.create(width);
  }

  public Color getColor() {
    if (!errorObj.equals(BigInteger.ZERO)) {
      return errorColor;
    } else if (width == 0) {
      return nilColor;
    } else if (width == 1) {
      if (this == UNKNOWN) return unknownColor;
      else if (this == TRUE) return trueColor;
      else return falseColor;
    } else {
      return multiColor;
    }
  }

  public int getWidth() {
    return width;
  }

  @Override
  public int hashCode() {
    return Value.hashcode(width, errorObj, unknownObj, valueObj);
  }

  public boolean isErrorValue() {
    return !errorObj.equals(BigInteger.ZERO);
  }

  public boolean isFullyDefined() {
    return width > 0 && errorObj.equals(BigInteger.ZERO) && unknownObj.equals(BigInteger.ZERO);
  }

  public boolean isUnknown() {
    BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
    return errorObj.equals(BigInteger.ZERO) && unknownObj.equals(mask);
  }

  public Value not() {
    if (width <= 1) {
      if (this == TRUE) return FALSE;
      if (this == FALSE) return TRUE;
      return ERROR;
    } else {
      return Value.create(this.width, this.errorObj.or(this.unknownObj), BigInteger.ZERO, this.valueObj.not());
    }
  }

  public Value or(Value other) {
    if (other == null) return this;
    if (this.width == 1 && other.width == 1) {
      if (this == TRUE || other == TRUE) return TRUE;
      if (this == FALSE && other == FALSE) return FALSE;
      return ERROR;
    } else {
      BigInteger true0 = this.valueObj.andNot(this.errorObj).andNot(this.unknownObj);
      BigInteger true1 = other.valueObj.andNot(other.errorObj).andNot(other.unknownObj);
      BigInteger trues = true0.or(true1);
      return Value.create(
          Math.max(this.width, other.width),
          this.errorObj.or(other.errorObj).or(this.unknownObj).or(other.unknownObj).andNot(trues),
          BigInteger.ZERO,
          this.valueObj.or(other.valueObj));
    }
  }

  public Value set(int which, Value val) {
    if (val.width != 1) {
      throw new RuntimeException("Cannot set multiple values");
    } else if (which < 0 || which >= width) {
      throw new RuntimeException("Attempt to set outside value's width");
    } else if (width == 1) {
      return val;
    } else {
      BigInteger mask = BigInteger.ONE.shiftLeft(which).not();
      BigInteger eBit = val.errorObj.testBit(0) ? BigInteger.ONE.shiftLeft(which) : BigInteger.ZERO;
      BigInteger uBit = val.unknownObj.testBit(0) ? BigInteger.ONE.shiftLeft(which) : BigInteger.ZERO;
      BigInteger vBit = val.valueObj.testBit(0) ? BigInteger.ONE.shiftLeft(which) : BigInteger.ZERO;
      return Value.create(
          this.width,
          this.errorObj.and(mask).or(eBit),
          this.unknownObj.and(mask).or(uBit),
          this.valueObj.and(mask).or(vBit));
    }
  }

  public String toBinaryString() {
    switch (width) {
      case 0:
        return Character.toString(DONTCARECHAR);
      case 1:
        if (!errorObj.equals(BigInteger.ZERO)) return Character.toString(ERRORCHAR);
        else if (!unknownObj.equals(BigInteger.ZERO)) return Character.toString(UNKNOWNCHAR);
        else if (!valueObj.equals(BigInteger.ZERO)) return Character.toString(TRUECHAR);
        else return Character.toString(FALSECHAR);
      default:
        final var ret = new StringBuilder();
        for (int i = width - 1; i >= 0; i--) {
          ret.append(get(i).toString());
        }
        return ret.toString();
    }
  }

  public String toDecimalString(boolean signed) {
    if (width == 0) return Character.toString(DONTCARECHAR);
    if (isErrorValue()) return Character.toString(ERRORCHAR);
    if (!isFullyDefined()) return Character.toString(UNKNOWNCHAR);

    BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
    BigInteger val = valueObj.and(mask);

    if (signed && val.testBit(width - 1)) {
      val = val.subtract(BigInteger.ONE.shiftLeft(width));
    }
    return val.toString();
  }

  public String toDisplayString() {
    switch (width) {
      case 0:
        return Character.toString(DONTCARECHAR);
      case 1:
        if (!errorObj.equals(BigInteger.ZERO)) return Character.toString(ERRORCHAR);
        else if (!unknownObj.equals(BigInteger.ZERO)) return Character.toString(UNKNOWNCHAR);
        else if (!valueObj.equals(BigInteger.ZERO)) return Character.toString(TRUECHAR);
        else return Character.toString(FALSECHAR);
      default:
        final var ret = new StringBuilder();
        for (var i = width - 1; i >= 0; i--) {
          ret.append(get(i).toString());
          if (i % 4 == 0 && i != 0) ret.append(" ");
        }
        return ret.toString();
    }
  }

  public String toDisplayString(int radix) {
    switch (radix) {
      case 2:
        return toDisplayString();
      case 8:
        return toOctalString();
      case 16:
        return toHexString();
      default:
        if (width == 0) return Character.toString(DONTCARECHAR);
        if (isErrorValue()) return Character.toString(ERRORCHAR);
        if (!isFullyDefined()) return Character.toString(UNKNOWNCHAR);
        return valueObj.toString(radix);
    }
  }

  public String toHexString() {
    if (width <= 1) {
      return toString();
    } else {
      final var vals = getAll();
      final var c = new char[(vals.length + 3) / 4];
      for (var i = 0; i < c.length; i++) {
        final var k = c.length - 1 - i;
        final var frst = 4 * k;
        final var last = Math.min(vals.length, 4 * (k + 1));
        var v = 0;
        c[i] = ' ';
        for (var j = last - 1; j >= frst; j--) {
          if (vals[j] == Value.ERROR) {
            c[i] = ERRORCHAR;
            break;
          }
          if (vals[j] == Value.UNKNOWN) {
            c[i] = UNKNOWNCHAR;
            break;
          }
          v = 2 * v;
          if (vals[j] == Value.TRUE) v++;
        }
        if (c[i] == ' ') c[i] = Character.forDigit(v, 16);
      }
      return new String(c);
    }
  }

  public long toLongValue() {
    if (!errorObj.equals(BigInteger.ZERO)) return -1L;
    if (!unknownObj.equals(BigInteger.ZERO)) return -1L;
    return valueObj.longValue();
  }

  public long toSignExtendedLongValue() {
    if (!errorObj.equals(BigInteger.ZERO)) return -1L;
    if (!unknownObj.equals(BigInteger.ZERO)) return -1L;
    if (width >= 64) return valueObj.longValue();
    final var shift = 64 - width;
    return (valueObj.longValue() << shift) >> shift;
  }

  public BigInteger toBigInteger(boolean unsigned) {
    BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
    BigInteger value = this.valueObj.and(mask);
    if (unsigned) {
      return value;
    }
    if (width > 0 && value.testBit(width - 1)) {
      value = value.subtract(BigInteger.ONE.shiftLeft(width));
    }
    return value;
  }

  public float toFloatValue() {
    if (!errorObj.equals(BigInteger.ZERO) || !unknownObj.equals(BigInteger.ZERO) || width != 32) return Float.NaN;
    return Float.intBitsToFloat(valueObj.intValue());
  }

  public double toDoubleValue() {
    if (!errorObj.equals(BigInteger.ZERO) || !unknownObj.equals(BigInteger.ZERO) || width != 64) return Double.NaN;
    return Double.longBitsToDouble(valueObj.longValue());
  }

  public float toFloatValueFromFP16() {
    if (!errorObj.equals(BigInteger.ZERO) || !unknownObj.equals(BigInteger.ZERO) || width != 16) return Float.NaN;
    return Float.float16ToFloat(valueObj.shortValue());
  }

  public float toFloatValueFromFP8() {
    if (!errorObj.equals(BigInteger.ZERO) || !unknownObj.equals(BigInteger.ZERO) || width != 8) return Float.NaN;
    return MiniFloat.miniFloat143ToFloat(valueObj.byteValue());
  }

  public double toDoubleValueFromAnyFloat() {
    return switch (width) {
      case 8 -> toFloatValueFromFP8();
      case 16 -> toFloatValueFromFP16();
      case 32 -> toFloatValue();
      case 64 -> toDoubleValue();
      default -> Double.NaN;
    };
  }

  public String toStringFromFloatValue() {
    return switch (getWidth()) {
      case 8 -> Float.toString(toFloatValueFromFP8());
      case 16 -> Float.toString(toFloatValueFromFP16());
      case 32 -> Float.toString(toFloatValue());
      case 64 -> Double.toString(toDoubleValue());
      default -> "NaN";
    };
  }

  public String toOctalString() {
    if (width <= 1) {
      return toString();
    } else {
      final var vals = getAll();
      final var c = new char[(vals.length + 2) / 3];
      for (var i = 0; i < c.length; i++) {
        final var k = c.length - 1 - i;
        final var frst = 3 * k;
        final var last = Math.min(vals.length, 3 * (k + 1));
        var v = 0;
        c[i] = ' ';
        for (var j = last - 1; j >= frst; j--) {
          if (vals[j] == Value.ERROR) {
            c[i] = ERRORCHAR;
            break;
          }
          if (vals[j] == Value.UNKNOWN) {
            c[i] = UNKNOWNCHAR;
            break;
          }
          v = 2 * v;
          if (vals[j] == Value.TRUE) v++;
        }
        if (c[i] == ' ') c[i] = Character.forDigit(v, 8);
      }
      return new String(c);
    }
  }

  @Override
  public String toString() {
    switch (width) {
      case 0:
        return Character.toString(DONTCARECHAR);
      case 1:
        if (!errorObj.equals(BigInteger.ZERO)) return Character.toString(ERRORCHAR);
        else if (!unknownObj.equals(BigInteger.ZERO)) return Character.toString(UNKNOWNCHAR);
        else if (!valueObj.equals(BigInteger.ZERO)) return Character.toString(TRUECHAR);
        else return Character.toString(FALSECHAR);
      default:
        final var ret = new StringBuilder();
        for (var i = width - 1; i >= 0; i--) {
          ret.append(get(i).toString());
          if (i % 4 == 0 && i != 0) ret.append(" ");
        }
        return ret.toString();
    }
  }

  public Value xor(Value other) {
    if (other == null) return this;
    if (this.width <= 1 && other.width <= 1) {
      if (this == ERROR || other == ERROR) return ERROR;
      if (this == UNKNOWN || other == UNKNOWN) return ERROR;
      if (this == NIL || other == NIL) return ERROR;
      if ((this == TRUE) == (other == TRUE)) return FALSE;
      return TRUE;
    } else {
      return Value.create(
          Math.max(this.width, other.width),
          this.errorObj.or(other.errorObj).or(this.unknownObj).or(other.unknownObj),
          BigInteger.ZERO,
          this.valueObj.xor(other.valueObj));
    }
  }

  public static boolean equal(Value a, Value b) {
    if ((a == null || a == Value.NIL) && (b == null || b == Value.NIL)) {
      return true;
    }
    if (a != null && b != null && a.equals(b)) {
      return true;
    }
    return false;
  }

  public Value pullTowardsBits(Value other) {
    if (width <= 0 || unknownObj.equals(BigInteger.ZERO) || other.width <= 0) return this;
    BigInteger e = errorObj.or(unknownObj.and(other.errorObj));
    BigInteger v = valueObj.or(unknownObj.and(other.valueObj));
    BigInteger otherMask = BigInteger.ONE.shiftLeft(other.width).subtract(BigInteger.ONE);
    BigInteger u = unknownObj.and(other.unknownObj.or(otherMask.not()));
    return Value.create(width, e, u, v);
  }

  public Value pullEachBitTowards(Value bit) {
    if (width <= 0 || unknownObj.equals(BigInteger.ZERO) || bit.width <= 0) return this;
    if (bit == ERROR) {
      return Value.create(width, errorObj.or(unknownObj), BigInteger.ZERO, valueObj);
    } else if (bit == TRUE) {
      return Value.create(width, errorObj, BigInteger.ZERO, valueObj.or(unknownObj));
    } else if (bit == FALSE) {
      return Value.create(width, errorObj, BigInteger.ZERO, valueObj);
    } else if (bit == UNKNOWN) {
      return this;
    } else {
      throw new IllegalArgumentException("pull value must be 1, 0, X, or E");
    }
  }
}
