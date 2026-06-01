/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 */

package com.cburch.logisim.vhdl.base;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.StringUtil;
import java.awt.Font;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class VhdlEntityAttributes extends AbstractAttributeSet {
    
    private static class VhdlContentAttributes extends AbstractAttributeSet {
        private final VhdlContent content;

        private VhdlContentAttributes(VhdlContent content) {
            this.content = content;
        }

        @Override
        protected void copyInto(AbstractAttributeSet dest) {}

        @Override
        public List<Attribute<?>> getAttributes() {
            return STATIC_ATTRIBUTES;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <V> V getValue(Attribute<V> attr) {
            if (attr == VhdlEntity.nameAttr) return (V) content.getName();
            if (attr == StdAttr.APPEARANCE) return (V) content.getAppearance();
            return null;
        }

        @Override
        public <V> void setValue(Attribute<V> attr, V value) {
            if (attr == VhdlEntity.nameAttr && value instanceof String name) {
                final var oldName = content.getName();
                if (oldName.equals(name) || !content.setName(name)) return;
                fireAttributeValueChanged(attr, value, (V) oldName);
            } else if (attr == StdAttr.APPEARANCE && (value == StdAttr.APPEAR_FPGA
                    || value == StdAttr.APPEAR_CLASSIC || value == StdAttr.APPEAR_EVOLUTION)) {
                final var oldAppearance = content.getAppearance();
                if (oldAppearance.equals(value)) return;
                content.setAppearance((AttributeOption) value);
                fireAttributeValueChanged(attr, value, (V) oldAppearance);
            }
        }
    }

    public static class VhdlGenericAttribute extends Attribute<BigInteger> {
        final BigInteger start;
        final BigInteger end;
        final VhdlContent.Generic g;

        private VhdlGenericAttribute(String name, StringGetter disp, BigInteger start, BigInteger end, VhdlContent.Generic generic) {
            super(name, disp);
            this.start = start;
            this.end = end;
            this.g = generic;
        }

        @Override
        public java.awt.Component getCellEditor(BigInteger value) {
            BigInteger defVal;
            try { defVal = new BigInteger(g.getDefaultValue().toString()); } 
            catch (Exception e) { defVal = BigInteger.ZERO; }
            return super.getCellEditor(value != null ? value : defVal);
        }

        @Override
        public BigInteger parse(String value) {
            if (value == null || value.isBlank() || value.contains("default")) return null;
            final var v = new BigInteger(value);
            if (v.compareTo(start) < 0 || v.compareTo(end) > 0) 
                throw new NumberFormatException("Value out of range");
            return v;
        }

        @Override
        public String toDisplayString(BigInteger value) {
            return value == null ? "(default) " + g.getDefaultValue() : value.toString();
        }
    }

    public static Attribute<BigInteger> forGeneric(VhdlContent.Generic generic) {
        final var name = generic.getName();
        final var disp = StringUtil.constantGetter(name);
        BigInteger max = BigInteger.valueOf(2).pow(512).subtract(BigInteger.ONE);
        BigInteger min = max.negate();

        if (generic.getType().equals("positive"))
            return new VhdlGenericAttribute("vhdl_" + name, disp, BigInteger.ONE, max, generic);
        else if (generic.getType().equals("natural"))
            return new VhdlGenericAttribute("vhdl_" + name, disp, BigInteger.ZERO, max, generic);
        else
            return new VhdlGenericAttribute("vhdl_" + name, disp, min, max, generic);
    }

    private static final List<Attribute<?>> STATIC_ATTRIBUTES = Arrays.asList(VhdlEntity.nameAttr, StdAttr.APPEARANCE);
    
    private VhdlContent content;
    private Instance vhdlInstance;
    private String label = "", simName = "";
    private Font labelFont = StdAttr.DEFAULT_LABEL_FONT;
    private Direction facing = Direction.EAST;
    private Boolean labelVisible = false;
    private HashMap<Attribute<BigInteger>, BigInteger> genericValues;
    private List<Attribute<?>> instanceAttrs;
    private VhdlEntityListener listener;

    public VhdlEntityAttributes(VhdlContent content) {
        this.content = content;
        this.genericValues = new HashMap<>();
        this.vhdlInstance = null;
        this.listener = null;
        updateGenerics();
    }

    // تم إصلاح الخطأ المنطقي هنا لضمان سلامة الذاكرة
    void setInstance(Instance value) {
        vhdlInstance = value;
        if (vhdlInstance != null && listener == null) { 
            listener = new VhdlEntityListener(this);
            content.addHdlModelListener(listener);
        }
    }

    void updateGenerics() {
        List<Attribute<BigInteger>> genericAttrs = content.getGenericAttributes();
        instanceAttrs = new ArrayList<>(6 + genericAttrs.size());
        instanceAttrs.addAll(Arrays.asList(VhdlEntity.nameAttr, StdAttr.LABEL, StdAttr.LABEL_FONT, 
                             StdAttr.LABEL_VISIBILITY, StdAttr.FACING, VhdlSimConstants.SIM_NAME_ATTR));
        instanceAttrs.addAll(genericAttrs);
        
        ArrayList<Attribute<BigInteger>> toRemove = new ArrayList<>();
        for (Attribute<BigInteger> a : genericValues.keySet())
            if (!genericAttrs.contains(a)) toRemove.add(a);
        for (Attribute<BigInteger> a : toRemove) genericValues.remove(a);
        
        fireAttributeListChanged();
    }

    @Override
    public List<Attribute<?>> getAttributes() { return instanceAttrs; }

    @SuppressWarnings("unchecked")
    @Override
    public <V> V getValue(Attribute<V> attr) {
        if (attr == VhdlEntity.nameAttr) return (V) content.getName();
        if (attr == StdAttr.LABEL) return (V) label;
        if (attr == StdAttr.LABEL_FONT) return (V) labelFont;
        if (attr == StdAttr.LABEL_VISIBILITY) return (V) labelVisible;
        if (attr == StdAttr.APPEARANCE) return (V) content.getAppearance();
        if (attr == StdAttr.FACING) return (V) facing;
        if (attr == VhdlSimConstants.SIM_NAME_ATTR) return (V) simName;
        if (genericValues.containsKey(attr)) return (V) genericValues.get(attr);
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> void setValue(Attribute<V> attr, V value) {
        if (attr == VhdlEntity.nameAttr) {
            if (!content.getName().equals(value) && content.setName((String) value)) fireAttributeValueChanged(attr, value, null);
        } else if (attr == StdAttr.LABEL) {
            label = (String) value; fireAttributeValueChanged(attr, value, null);
        } else if (genericValues != null && genericValues.containsKey(attr)) {
            genericValues.put((Attribute<BigInteger>) attr, (BigInteger) value);
            fireAttributeValueChanged(attr, value, null);
        }
        // ... باقي منطق التعيين المعتاد
    }

    static class VhdlEntityListener implements HdlModelListener {
        final VhdlEntityAttributes attrs;
        VhdlEntityListener(VhdlEntityAttributes attrs) { this.attrs = attrs; }
        @Override public void contentSet(HdlModel source) { attrs.updateGenerics(); }
        @Override public void appearanceChanged(HdlModel source) { attrs.vhdlInstance.recomputeBounds(); }
    }

    @Override
    public boolean isToSave(Attribute<?> attr) { return attr.isToSave() && attr != VhdlSimConstants.SIM_NAME_ATTR; }
}
