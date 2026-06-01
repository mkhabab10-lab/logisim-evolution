/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.gates;

import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.StdAttr;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class GateAttributes extends AbstractAttributeSet {
    static final int MAX_INPUTS = 512; 

    // تعريف Attribute خاص للـ Negated يدعم BigInteger للـ 512 بت
    public static final Attribute<BigInteger> ATTR_NEGATED = 
        Attributes.forHex("negated", S.getter("gateNegatedAttr"));

    static final AttributeOption SIZE_NARROW = new AttributeOption(30, S.getter("gateSizeNarrowOpt"));
    static final AttributeOption SIZE_MEDIUM = new AttributeOption(50, S.getter("gateSizeNormalOpt"));
    static final AttributeOption SIZE_WIDE = new AttributeOption(70, S.getter("gateSizeWideOpt"));
    
    public static final Attribute<AttributeOption> ATTR_SIZE =
        Attributes.forOption("size", S.getter("gateSizeAttr"), 
        new AttributeOption[] {SIZE_NARROW, SIZE_MEDIUM, SIZE_WIDE});

    public static final Attribute<Integer> ATTR_INPUTS =
        Attributes.forIntegerRange("inputs", S.getter("gateInputsAttr"), 2, MAX_INPUTS);

    // الخصائص
    Direction facing = Direction.EAST;
    BitWidth width = BitWidth.ONE;
    AttributeOption size = SIZE_MEDIUM;
    int inputs = 2;
    BigInteger negated = BigInteger.ZERO; // دعم 512 بت
    private final boolean isXor;

    public GateAttributes(boolean isXor) {
        this.isXor = isXor;
    }

    @Override
    protected void initAttributes() {}

    @Override
    public List<Attribute<?>> getAttributes() {
        List<Attribute<?>> ret = new ArrayList<>();
        ret.add(StdAttr.FACING);
        ret.add(StdAttr.WIDTH);
        ret.add(ATTR_SIZE);
        ret.add(ATTR_INPUTS);
        if (isXor) ret.add(Gate.ATTR_XOR);
        ret.add(Gate.ATTR_OUTPUT);
        ret.add(ATTR_NEGATED); // إضافة خاصية النفي للواجهة
        return ret;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> V getValue(Attribute<V> attr) {
        if (attr == StdAttr.FACING) return (V) facing;
        if (attr == StdAttr.WIDTH) return (V) width;
        if (attr == ATTR_SIZE) return (V) size;
        if (attr == ATTR_INPUTS) return (V) Integer.valueOf(inputs);
        if (attr == ATTR_NEGATED) return (V) negated;
        return null;
    }

    @Override
    public <V> void setValue(Attribute<V> attr, V value) {
        if (attr == StdAttr.FACING) facing = (Direction) value;
        else if (attr == StdAttr.WIDTH) {
            width = (BitWidth) value;
            // تصحيح منطق الـ Mask: التأكد أن النفي لا يتجاوز عرض البتات الجديد
            BigInteger mask = BigInteger.ONE.shiftLeft(width.getWidth()).subtract(BigInteger.ONE);
            negated = negated.and(mask);
        } else if (attr == ATTR_SIZE) size = (AttributeOption) value;
        else if (attr == ATTR_INPUTS) inputs = (Integer) value;
        else if (attr == ATTR_NEGATED) negated = (BigInteger) value;
        else {
            super.setValue(attr, value);
            return;
        }
        fireAttributeValueChanged(attr, value);
    }
}
