/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 */

package com.cburch.logisim.std.arith;

import static com.cburch.logisim.std.Strings.S;
import com.cburch.logisim.data.*;
import com.cburch.logisim.gui.icons.ArithmeticIcon;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.key.*;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Graphics;
import java.math.BigInteger; // للعمليات الحسابية الآمنة لـ 512 بت

public class BitAdder extends InstanceFactory {
  public static final String _ID = "BitAdder";
  
  // رفع الحد الأقصى لدعم النظام الجديد
  static final Attribute<Integer> NUM_INPUTS =
      Attributes.forIntegerRange("inputs", S.getter("gateInputsAttr"), 1, 512);

  public BitAdder() {
    super(_ID, S.getter("bitAdderComponent"));
    setAttributes(
        new Attribute[] {StdAttr.WIDTH, NUM_INPUTS},
        new Object[] {BitWidth.create(8), 1});
    setKeyConfigurator(
        JoinedConfigurator.create(
            new IntegerConfigurator(NUM_INPUTS, 1, 512, 0), // تحديث الكيبورد
            new BitWidthConfigurator(StdAttr.WIDTH)));
    setIcon(new ArithmeticIcon("#"));
  }

  // تحسين حساب عرض الناتج باستخدام BigInteger لضمان الدقة في المدى العريض
  private int computeOutputBits(int width, int inputs) {
    BigInteger maxBits = BigInteger.valueOf(width).multiply(BigInteger.valueOf(inputs));
    return maxBits.bitLength();
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    configurePorts(instance);
    instance.addAttributeListener();
  }

  private void configurePorts(Instance instance) {
    BitWidth inWidth = instance.getAttributeValue(StdAttr.WIDTH);
    int inputs = instance.getAttributeValue(NUM_INPUTS);
    int outWidth = computeOutputBits(inWidth.getWidth(), inputs);

    Port[] ps = new Port[inputs + 1];
    ps[0] = new Port(0, 0, Port.OUTPUT, BitWidth.create(outWidth));
    for (int i = 0; i < inputs; i++) {
      ps[i + 1] = new Port(-40, getPortY(inputs, i), Port.INPUT, inWidth);
    }
    instance.setPorts(ps);
  }

  private int getPortY(int inputs, int index) {
      int y = inputs < 4 ? 0 : (((inputs - 1) / 2) * -10);
      return y + (index * 10);
  }

  @Override
  public void propagate(InstanceState state) {
    int inputs = state.getAttributeValue(NUM_INPUTS);
    
    // استخدام BigInteger لعدّ الآحاد بدلاً من int لتجنب الـ Overflow
    BigInteger minCount = BigInteger.ZERO;
    BigInteger maxCount = BigInteger.ZERO;

    for (int i = 1; i <= inputs; i++) {
      Value v = state.getPortValue(i);
      for (Value b : v.getAll()) {
        if (b == Value.TRUE) minCount = minCount.add(BigInteger.ONE);
        if (b != Value.FALSE) maxCount = maxCount.add(BigInteger.ONE);
      }
    }

    // حساب الـ Mask باستخدام منطق بتات BigInteger
    BigInteger unknownMask = maxCount.xor(minCount);
    
    int outWidth = computeOutputBits(state.getAttributeValue(StdAttr.WIDTH).getWidth(), inputs);
    Value[] out = new Value[outWidth];
    
    for (int i = 0; i < outWidth; i++) {
      if (unknownMask.testBit(i)) {
        out[outWidth - 1 - i] = Value.ERROR;
      } else if (minCount.testBit(i)) {
        out[outWidth - 1 - i] = Value.TRUE;
      } else {
        out[outWidth - 1 - i] = Value.FALSE;
      }
    }

    state.setPort(0, Value.create(out), outWidth * 2);
  }
  
  // ... (تم ترك الدوال الرسومية كما هي لأنها تعتمد على الأبعاد وليست المنطق)
}
