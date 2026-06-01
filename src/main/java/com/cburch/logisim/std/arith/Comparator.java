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
import java.awt.Color;
import java.awt.Graphics;
import java.math.BigInteger;

public class Comparator extends InstanceFactory {
    public static final String _ID = "Comparator";

    public static final AttributeOption SIGNED_OPTION =
        new AttributeOption("twosComplement", "twosComplement", S.getter("twosComplementOption"));
    public static final AttributeOption UNSIGNED_OPTION =
        new AttributeOption("unsigned", "unsigned", S.getter("unsignedOption"));
    public static final Attribute<AttributeOption> MODE_ATTR =
        Attributes.forOption("mode", S.getter("comparatorType"), 
        new AttributeOption[] {SIGNED_OPTION, UNSIGNED_OPTION});

    public static final int IN0 = 0;
    public static final int IN1 = 1;
    public static final int GT = 2;
    public static final int EQ = 3;
    public static final int LT = 4;

    public Comparator() {
        super(_ID, S.getter("comparatorComponent"), new ComparatorHdlGeneratorFactory());
        setAttributes(new Attribute[] {StdAttr.WIDTH, MODE_ATTR},
                      new Object[] {BitWidth.create(8), SIGNED_OPTION});
        setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
        setOffsetBounds(Bounds.create(-40, -20, 40, 40));
        setIcon(new ArithmeticIcon("\u2276"));

        Port[] ps = new Port[5];
        ps[IN0] = new Port(-40, -10, Port.INPUT, StdAttr.WIDTH);
        ps[IN1] = new Port(-40, 10, Port.INPUT, StdAttr.WIDTH);
        ps[GT] = new Port(0, -10, Port.OUTPUT, 1);
        ps[EQ] = new Port(0, 0, Port.OUTPUT, 1);
        ps[LT] = new Port(0, 10, Port.OUTPUT, 1);
        setPorts(ps);
    }

    @Override
    protected void configureNewInstance(Instance instance) {
        instance.addAttributeListener();
    }

    @Override
    protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
        instance.fireInvalidated();
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        g.setColor(new Color(AppPreferences.COMPONENT_COLOR.get()));
        painter.drawBounds();
        painter.drawPort(IN0);
        painter.drawPort(IN1);
        painter.drawPort(GT, ">", Direction.WEST);
        painter.drawPort(EQ, "=", Direction.WEST);
        painter.drawPort(LT, "<", Direction.WEST);
    }

    @Override
    public void propagate(InstanceState state) {
        boolean unsigned = state.getAttributeValue(MODE_ATTR).equals(UNSIGNED_OPTION);
        Value a = state.getPortValue(IN0);
        Value b = state.getPortValue(IN1);

        // التعامل مع حالات الخطأ أو القيم غير المحددة (512 بت آمن)
        if (a.isErrorValue() || b.isErrorValue()) {
            state.setPort(GT, Value.ERROR, 1);
            state.setPort(EQ, Value.ERROR, 1);
            state.setPort(LT, Value.ERROR, 1);
            return;
        }
        
        if (!a.isFullyDefined() || !b.isFullyDefined()) {
            state.setPort(GT, Value.UNKNOWN, 1);
            state.setPort(EQ, Value.UNKNOWN, 1);
            state.setPort(LT, Value.UNKNOWN, 1);
            return;
        }

        // التحويل إلى BigInteger لإجراء المقارنة العريضة
        BigInteger valA = a.toBigInteger(unsigned);
        BigInteger valB = b.toBigInteger(unsigned);

        int cmp = valA.compareTo(valB);

        // إخراج النتائج
        int delay = state.getAttributeValue(StdAttr.WIDTH).getWidth() * 2;
        state.setPort(GT, (cmp > 0) ? Value.TRUE : Value.FALSE, delay);
        state.setPort(EQ, (cmp == 0) ? Value.TRUE : Value.FALSE, delay);
        state.setPort(LT, (cmp < 0) ? Value.TRUE : Value.FALSE, delay);
    }
}
