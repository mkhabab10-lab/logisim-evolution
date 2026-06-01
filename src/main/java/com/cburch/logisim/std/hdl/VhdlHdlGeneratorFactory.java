/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.hdl;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.file.FileWriter;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.instance.Port;
import java.util.ArrayList;

/**
 * Copies VHDL entities into generated output.
 * Optimized to correctly fetch port widths up to 512-bit without truncation.
 */
public class VhdlHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  public static final String HDL_DIRECTORY = "circuit";

  public VhdlHdlGeneratorFactory() {
    super(HDL_DIRECTORY);
    getWiresPortsDuringHDLWriting = true;
  }

  @Override
  public void getGenerationTimeWiresPorts(Netlist theNetlist, AttributeSet attrs) {
    final var contents = attrs.getValue(VhdlEntityComponent.CONTENT_ATTR);
    if (contents == null) return; // حماية إضافية لمنع الـ NullPointerException
    
    final var inputs = contents.getInputs();
    final var outputs = contents.getOutputs();
    var portId = 0;
    
    for (final var input : inputs) {
      // جلب العرض والتحقق من أنه لا يتجاوز الـ 512 بت المسموحة في نظامنا الجديد
      int width = input.getWidthInt();
      if (width > 512) width = 512; // كبح آمن متوافق مع نظام الـ 512 بت الجديد
      myPorts.add(Port.INPUT, input.getName(), width, portId++);
    }
    
    for (final var output : outputs) {
      int width = output.getWidthInt();
      if (width > 512) width = 512; // كبح آمن متوافق مع نظام الـ 512 بت الجديد
      myPorts.add(Port.OUTPUT, output.getName(), width, portId++);
    }
  }

  @Override
  public ArrayList<String> getArchitecture(
      Netlist theNetlist, AttributeSet attrs, String componentName) {
    ArrayList<String> contents =
        new ArrayList<>(FileWriter.getGenerateRemark(componentName, theNetlist.projName()));

    VhdlContentComponent content = attrs.getValue(VhdlEntityComponent.CONTENT_ATTR);
    if (content != null) {
      contents.add(content.getLibraries());
      contents.add(content.getArchitecture());
    }

    return contents;
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    return Hdl.isVhdl();
  }
}
