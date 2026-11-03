package neqsim.process.processmodel.diagram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Command;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Page;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Point;

/**
 * Authored vector symbol families for the ISO 10628 professional drawing workflow.
 *
 * <p>
 * Equipment, valve function, actuator and instrument location are separate semantics. The catalogue records its
 * standards basis without assigning a fabricated ISO registration number or claiming an independent standards review.
 * Unknown equipment fails closed instead of silently becoming a generic box. No licensed standard artwork is embedded.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class ProfessionalPidSymbolLibrary {
  /** Standards basis for process-equipment and piping symbol review. */
  public static final String EQUIPMENT_STANDARD = "ISO 10628-2:2012 / ISO 14617";
  /** Standards basis for process measuring/control requests. */
  public static final String INSTRUMENT_STANDARD = "IEC 62424:2016 / ANSI/ISA-5.1:2024";

  /** Function-specific symbols; actuator and function-location decorations remain explicit. */
  public enum Family {
    VERTICAL_VESSEL, HORIZONTAL_VESSEL, COMPRESSOR, PUMP, HEAT_EXCHANGER, CONTROL_VALVE, ISOLATION_VALVE, CHECK_VALVE,
    RELIEF_VALVE, FIELD_INSTRUMENT, CONTROL_FUNCTION, SAFETY_FUNCTION, NOZZLE, TERMINAL, TERMINAL_NORTH,
    PNEUMATIC_OPERATED_VALVE, UNMAPPED
  }

  /** Prevents utility construction. */
  private ProfessionalPidSymbolLibrary() {
  }

  /**
   * Selects a supported family from source type and actual separator orientation.
   *
   * @param equipment source equipment
   * @return supported family or UNMAPPED
   */
  public static Family family(ProcessEquipmentInterface equipment) {
    if (equipment instanceof Separator) {
      return "horizontal".equalsIgnoreCase(((Separator) equipment).getOrientation()) ? Family.HORIZONTAL_VESSEL
          : Family.VERTICAL_VESSEL;
    }
    if (equipment instanceof neqsim.process.equipment.expander.Expander) {
      return Family.UNMAPPED;
    }
    if (equipment instanceof Compressor) {
      return Family.COMPRESSOR;
    }
    if (equipment instanceof Pump) {
      return Family.PUMP;
    }
    if (equipment instanceof Heater || equipment instanceof HeatExchanger) {
      return Family.HEAT_EXCHANGER;
    }
    if (equipment instanceof neqsim.process.equipment.valve.CheckValve) {
      return Family.CHECK_VALVE;
    }
    if (equipment instanceof neqsim.process.equipment.valve.SafetyValve
        || equipment instanceof neqsim.process.equipment.valve.SafetyReliefValve) {
      return Family.RELIEF_VALVE;
    }
    if (equipment instanceof neqsim.process.equipment.valve.ESDValve
        || equipment instanceof neqsim.process.equipment.valve.PSDValve
        || equipment instanceof neqsim.process.equipment.valve.HIPPSValve
        || equipment instanceof neqsim.process.equipment.valve.BlowdownValve) {
      return Family.ISOLATION_VALVE;
    }
    if (equipment instanceof neqsim.process.equipment.valve.RuptureDisk) {
      return Family.UNMAPPED;
    }
    if (equipment instanceof ThrottlingValve) {
      return Family.CONTROL_VALVE;
    }
    return Family.UNMAPPED;
  }

  /**
   * Adds paper-millimetre primitives to the shared native drawing scene.
   *
   * @param page native page
   * @param family function-specific symbol
   * @param x symbol centre x
   * @param y symbol centre y
   * @param width symbol width
   * @param height symbol height
   * @param tag controlled tag
   */
  static void draw(Page page, Family family, double x, double y, double width, double height, String tag) {
    int first = page.commands.size();
    switch (family) {
    case VERTICAL_VESSEL:
    case HORIZONTAL_VESSEL:
      vessel(page, x, y, width, height, tag);
      break;
    case COMPRESSOR:
      circle(page, x, y, width / 2.0, tag);
      poly(page, tag,
          new double[][] {{x - width * .28, y - height * .28}, {x + width * .28, y - height * .13},
              {x + width * .28, y + height * .13}, {x - width * .28, y + height * .28},
              {x - width * .28, y - height * .28}});
      break;
    case PUMP:
      circle(page, x, y, width / 2.0, tag);
      poly(page, tag, new double[][] {{x - width * .25, y + height * .25}, {x + width * .3, y},
          {x - width * .25, y - height * .25}});
      break;
    case HEAT_EXCHANGER:
      circle(page, x, y, width / 2.0, tag);
      poly(page, tag, new double[][] {{x - width * .36, y + height * .2}, {x - width * .16, y - height * .2},
          {x + width * .16, y + height * .2}, {x + width * .36, y - height * .2}});
      break;
    case CONTROL_VALVE:
    case PNEUMATIC_OPERATED_VALVE:
    case ISOLATION_VALVE:
    case CHECK_VALVE:
    case RELIEF_VALVE:
      valve(page, family, x, y, width, height, tag);
      break;
    case FIELD_INSTRUMENT:
    case CONTROL_FUNCTION:
    case SAFETY_FUNCTION:
      circle(page, x, y, width / 2.0, tag);
      if (family != Family.FIELD_INSTRUMENT) {
        page.commands.add(Command.line(x - width / 2.0, y, x + width / 2.0, y, "#111111", .25, tag, ""));
      }
      if (family == Family.SAFETY_FUNCTION) {
        page.commands.add(Command.rect(x - width / 2.0 - 2.0, y - height / 2.0 - 2.0, width + 4.0, height + 4.0,
            "#111111", "none", .25, tag, ""));
      }
      break;
    case NOZZLE:
      page.commands.add(Command.rect(x - 1.5, y - 2.0, 3.0, 4.0, "#111111", "#ffffff", .35, tag, ""));
      break;
    case TERMINAL_NORTH:
      poly(page, tag,
          new double[][] {{x, y - height / 2}, {x + width / 2, y - height / 2 + 4}, {x + width / 2, y + height / 2},
              {x - width / 2, y + height / 2}, {x - width / 2, y - height / 2 + 4}, {x, y - height / 2}});
      break;
    case TERMINAL:
      poly(page, tag,
          new double[][] {{x - width / 2, y - height / 2}, {x + width / 2 - 4, y - height / 2}, {x + width / 2, y},
              {x + width / 2 - 4, y + height / 2}, {x - width / 2, y + height / 2}, {x - width / 2, y - height / 2}});
      break;
    default:
      throw new IllegalArgumentException("No reviewed symbol family mapping for " + tag);
    }
    for (int index = first; index < page.commands.size(); index++) {
      page.commands.get(index).svgGroupAttribute = "data-tag";
      page.commands.get(index).svgGroupValue = tag;
      page.commands.get(index).svgSymbolFamily = family.name();
    }
  }

  /**
   * Draws a vessel with dished heads and its actual orientation.
   *
   * @param page native page
   * @param x centre x
   * @param y centre y
   * @param width width
   * @param height height
   * @param tag controlled identity
   */
  private static void vessel(Page page, double x, double y, double width, double height, String tag) {
    page.commands.add(Command.polygon(vesselOutline(x, y, width, height), "#111111", "#ffffff", .5, tag));
  }

  /**
   * Returns the same polygon used for vessel drawing and physical sensing attachments.
   *
   * @param x centre x
   * @param y centre y
   * @param width symbol width
   * @param height symbol height
   * @return closed hull vertices in drawing units
   */
  static List<Point> vesselOutline(double x, double y, double width, double height) {
    List<Point> points = new ArrayList<Point>();
    double radius = Math.min(width, height) / 2.0;
    boolean vertical = height >= width;
    for (int head = 0; head < 2; head++) {
      for (int index = 0; index <= 20; index++) {
        double angle = (vertical ? Math.PI + head * Math.PI : -Math.PI / 2 + head * Math.PI) + index * Math.PI / 20;
        double px = radius * Math.cos(angle);
        double py = radius * Math.sin(angle);
        if (vertical) {
          py += head == 0 ? -(height / 2 - radius) : height / 2 - radius;
        } else {
          px += head == 0 ? width / 2 - radius : -(width / 2 - radius);
        }
        points.add(new Point(x + px, y + py));
      }
    }
    return points;
  }

  /**
   * Intersects a sensing ordinate with the actually drawn dished vessel hull.
   *
   * @param x vessel centre x
   * @param y vessel centre y
   * @param width symbol width
   * @param height symbol height
   * @param tapY requested sensing ordinate
   * @return actual right-side hull point, not its bounding rectangle
   * @throws IllegalArgumentException if the ordinate does not intersect the vessel
   */
  static Point vesselSensingTap(double x, double y, double width, double height, double tapY) {
    List<Point> hull = vesselOutline(x, y, width, height);
    double right = -Double.MAX_VALUE;
    for (int index = 0; index < hull.size(); index++) {
      Point a = hull.get(index);
      Point b = hull.get((index + 1) % hull.size());
      if (tapY < Math.min(a.y, b.y) - 1e-9 || tapY > Math.max(a.y, b.y) + 1e-9) {
        continue;
      }
      if (Math.abs(b.y - a.y) < 1e-9) {
        right = Math.max(right, Math.max(a.x, b.x));
      } else {
        right = Math.max(right, a.x + (tapY - a.y) * (b.x - a.x) / (b.y - a.y));
      }
    }
    if (right == -Double.MAX_VALUE) {
      throw new IllegalArgumentException("Sensing ordinate lies outside the drawn vessel");
    }
    return new Point(right, tapY);
  }

  /**
   * Draws a function-specific valve, retaining the distinction between relief and throttling.
   *
   * @param page native page
   * @param family valve function
   * @param x centre x
   * @param y centre y
   * @param width width
   * @param height body height
   * @param tag controlled tag
   */
  private static void valve(Page page, Family family, double x, double y, double width, double height, String tag) {
    if (family == Family.CHECK_VALVE) {
      poly(page, tag, new double[][] {{x - width / 2, y - height / 2}, {x + width / 2, y},
          {x - width / 2, y + height / 2}, {x - width / 2, y - height / 2}});
      page.commands
          .add(Command.line(x + width / 2, y - height / 2, x + width / 2, y + height / 2, "#111111", .5, tag, ""));
      return;
    }
    poly(page, tag, new double[][] {{x - width / 2, y - height / 2}, {x + width / 2, y + height / 2},
        {x + width / 2, y - height / 2}, {x - width / 2, y + height / 2}, {x - width / 2, y - height / 2}});
    if (family == Family.CONTROL_VALVE || family == Family.PNEUMATIC_OPERATED_VALVE) {
      page.commands.add(Command.line(x, y, x, y - 13, "#111111", .3, tag, ""));
      if (family != Family.PNEUMATIC_OPERATED_VALVE) {
        return; // Actuator power/type remains HOLD; do not silently select a diaphragm.
      }
      List<Point> diaphragm = new ArrayList<Point>();
      for (int index = 0; index <= 16; index++) {
        double angle = Math.PI + index * Math.PI / 16.0;
        diaphragm.add(new Point(x + 5 * Math.cos(angle), y - 13 + 3 * Math.sin(angle)));
      }
      diaphragm.add(new Point(x - 5, y - 13));
      page.commands.add(Command.polygon(diaphragm, "#111111", "#ffffff", .3, tag));
    }
    if (family == Family.RELIEF_VALVE) {
      page.commands.add(Command.line(x, y, x, y - 8, "#111111", .3, tag, ""));
      poly(page, tag, new double[][] {{x - 4, y - 8}, {x + 4, y - 10}, {x - 4, y - 12}, {x + 4, y - 14},
          {x - 4, y - 16}, {x + 4, y - 18}});
      page.commands.add(Command.line(x - 5, y - 18, x + 5, y - 18, "#111111", .3, tag, ""));
    }
  }

  /**
   * Adds a closed vector circle without a bitmap or external font dependency.
   *
   * @param page native page
   * @param x centre x
   * @param y centre y
   * @param radius radius
   * @param tag identity
   */
  private static void circle(Page page, double x, double y, double radius, String tag) {
    List<Point> points = new ArrayList<Point>();
    for (int index = 0; index <= 48; index++) {
      double angle = index * Math.PI / 24.0;
      points.add(new Point(x + radius * Math.cos(angle), y + radius * Math.sin(angle)));
    }
    page.commands.add(Command.polygon(points, "#111111", "#ffffff", .35, tag));
  }

  /**
   * Adds an unfilled symbol stroke.
   *
   * @param page native page
   * @param tag identity
   * @param coordinates stroke coordinates
   */
  private static void poly(Page page, String tag, double[][] coordinates) {
    List<Point> points = new ArrayList<Point>();
    for (double[] point : coordinates) {
      points.add(new Point(point[0], point[1]));
    }
    page.commands.add(Command.polyline(points, "#111111", .5, "", tag, false));
  }
}
