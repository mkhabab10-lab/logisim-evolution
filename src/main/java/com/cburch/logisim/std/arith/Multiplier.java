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
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Graphics;
import java.math.BigInteger;

public class Multiplier extends InstanceFactory {
    public static final String _ID = "Multiplier";

    static Value[] computeProduct(BitWidth width, Value a, Value b, Value c_in, boolean unsigned) {
        int w = width.getWidth();
        if (c_in == Value.NIL || c_in.isUnknown()) c_in = Value.createKnown(width, 0);

        // تحويل كافة المدخلات إلى BigInteger للتعامل مع الـ 512 بت
        BigInteger aa = a.toBigInteger(unsigned);
        BigInteger bb = b.toBigInteger(unsigned);
        BigInteger cc = c_in.toBigInteger(unsigned);
        
        // إجراء عملية الضرب والجمع بدقة رياضية مطلقة
        BigInteger rr = aa.multiply(bb).add(cc);

        // إنشاء قناع بتات (Mask) بحجم الـ Width المختار (مثلاً 512 بت)
        BigInteger mask = BigInteger.ONE.shiftLeft(w).subtract(BigInteger.ONE);
        
        // استخراج الناتج السفلي (LO) والعلوي (HI)
        BigInteger lo = rr.and(mask);
        BigInteger hi = rr.shiftRight(w).and(mask);

        if (a.isFullyDefined() && b.isFullyDefined() && c_in.isFullyDefined()) {
            return new Value[] {
                Value.createKnown(width, lo), 
                Value.createKnown(width, hi)
            };
        } else {
            // معالجة حالات القيم غير المعروفة (Unknown/Error) بدقة
            Value[] avals = a.getAll();
            Value[] bvals = b.getAll();
            Value[] cvals = c_in.getAll();
            
            int known = Math.min(Math.min(findUnknown(avals), findUnknown(bvals)), findUnknown(cvals));
            int error = Math.min(Math.min(findError(avals), findError(bvals)), findError(cvals));

            Value[] bits = new Value[w];
            for (int i = 0; i < w; i++) {
                if (i < known) {
                    bits[i] = (lo.testBit(i) ? Value.TRUE : Value.FALSE);
                } else if (i < error) {
                    bits[i] = Value.UNKNOWN;
                } else {
                    bits[i] = Value.ERROR;
                }
            }
            return new Value[] {
                Value.create(bits), 
                error < w ? Value.createError(width) : Value.createUnknown(width)
            };
        }
    }

    private static int findError(Value[] vals) {
        for (int i = 0; i < vals.length; i++) if (vals[i].isErrorValue()) return i;
        return vals.length;
    }

    private static int findUnknown(Value[] vals) {
        for (int i = 0; i < vals.length; i++) if (!vals[i].isFullyDefined()) return i;
        return vals.length;
    }

    static final int PER_DELAY = 1;
    public static final int IN0 = 0;
    public static final int IN1 = 1;
    public static final int OUT = 2;
    public static final int C_IN = 3;
    public static final int C_OUT = 4;

    public Multiplier() {
        super(_ID, S.getter("multiplierComponent"), new MultiplierHdlGeneratorFactory());
        setAttributes(new Attribute[] {StdAttr.WIDTH, Comparator.MODE_ATTR},
                      new Object[] {BitWidth.create(8), Comparator.UNSIGNED_OPTION});
        setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
        setOffsetBounds(Bounds.create(-40, -20, 40, 40));
        setIcon(new ArithmeticIcon("\u00d7"));

        Port[] ps = new Port[5];
        ps[IN0] = new Port(-40, -10, Port.INPUT, StdAttr.WIDTH);
        ps[IN1] = new Port(-40, 10, Port.INPUT, StdAttr.WIDTH);
        ps[OUT] = new Port(0, 0, Port.OUTPUT, StdAttr.WIDTH);
        ps[C_IN] = new Port(-20, -20, Port.INPUT, StdAttr.WIDTH);
        ps[C_OUT] = new Port(-20, 20, Port.OUTPUT, StdAttr.WIDTH);
        setPorts(ps);
    }

    @Override
    protected void configureNewInstance(Instance instance) {
        instance.addAttributeListener();
    }

    @Override
    protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
        if (attr == Comparator.MODE_ATTR) instance.fireInvalidated();
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        g.setColor(new Color(AppPreferences.COMPONENT_COLOR.get()));
        painter.drawBounds();
        painter.drawPorts();
        
        Location loc = painter.getLocation();
        int x = loc.getX(); int y = loc.getY();
        GraphicsUtil.switchToWidth(g, 2);
        g.drawLine(x - 15, y - 5, x - 5, y + 5);
        g.drawLine(x - 15, y + 5, x - 5, y - 5);
    }

    @Override
    public void propagate(InstanceState state) {
        BitWidth dataWidth = state.getAttributeValue(StdAttr.WIDTH);
        boolean unsigned = state.getAttributeValue(Comparator.MODE_ATTR).equals(Comparator.UNSIGNED_OPTION);

        Value a = state.getPortValue(IN0);
        Value b = state.getPortValue(IN1);
        Value c_in = state.getPortValue(C_IN);
        
        Value[] outs = computeProduct(dataWidth, a, b, c_in, unsigned);

        int delay = dataWidth.getWidth() * (dataWidth.getWidth() + 2) * PER_DELAY;
        state.setPort(OUT, outs[0], delay);
        state.setPort(C_OUT, outs[1], delay);
    }
}
