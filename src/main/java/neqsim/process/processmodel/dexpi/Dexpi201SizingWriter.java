package neqsim.process.processmodel.dexpi;

import java.util.Map;
import neqsim.process.engineering.pid.PidSizing;
import neqsim.process.engineering.pid.PidSizing.Dimension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Maps drawing sizes onto their official DEXPI 2.0.1 property owners and units.
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class Dexpi201SizingWriter {
  private Dexpi201SizingWriter() {
  }

  /**
   * Adds the nominal designation and class at this exact segment. A screened size is on hold for review.
   *
   * @param segment native piping network segment
   * @param tag material stream or controlled auxiliary line
   * @param sizes case sizing values, or null for the legacy writer path
   */
  public static void line(Element segment, String tag, PidSizing sizes) {
    if (sizes == null || tag == null) {
      return;
    }
    Document document = segment.getOwnerDocument();
    String nominal = sizes.nominal(tag);
    if (!nominal.isEmpty()) {
      Dexpi20XmlWriter.data(document, segment, "NominalDiameterRepresentation", nominal);
      if (nominal.startsWith("DN ") || nominal.startsWith("NPS ")) {
        int space = nominal.indexOf(' ');
        Dexpi20XmlWriter.data(document, segment, "NominalDiameterTypeRepresentation", nominal.substring(0, space));
        Dexpi20XmlWriter.data(document, segment, "NominalDiameterNumericalValueRepresentation",
            nominal.substring(space + 1));
      }
    }
    if (!sizes.pipingClass(tag).isEmpty()) {
      Dexpi20XmlWriter.data(document, segment, "PipingClassCode", sizes.pipingClass(tag));
    }
    if (!sizes.calculatedSchedule(tag).isEmpty()) {
      Element reference = document.createElement("DataReference");
      reference.setAttribute("data", "Plant/Enumerations.OnHoldClassification.OnHold");
      property(segment, "OnHold").appendChild(reference);
    }
  }

  /**
   * Writes the measured quantities on the appropriate equipment, chamber or inlet impeller.
   * 
   * @param equipment native equipment object
   * @param tag equipment tag
   * @param sizes case sizing values, or null for legacy output
   */
  static void equipment(Element equipment, String tag, PidSizing sizes) {
    if (sizes == null || sizes.dimensions(tag).isEmpty()) {
      return;
    }
    Map<Dimension, Double> dimensions = sizes.dimensions(tag);
    Document source = equipment.getOwnerDocument();
    Element description = source.createElement("AggregatedDataValue");
    description.setAttribute("type", "Core/DataTypes.MultiLanguageString");
    property(equipment, "EquipmentDescription").appendChild(description);
    Element english = source.createElement("AggregatedDataValue");
    english.setAttribute("type", "Core/DataTypes.SingleLanguageString");
    property(description, "SingleLanguageStrings").appendChild(english);
    Dexpi20XmlWriter.data(source, english, "Language", "en");
    Dexpi20XmlWriter.data(source, english, "Value",
        "Preliminary mechanical sizing at the simulated operating point. Engineering review required.");
    Element owner = equipment;
    if (dimensions.containsKey(Dimension.INSIDE_DIAMETER)) {
      owner = part(equipment, "Chambers", "Chamber");
    } else if (dimensions.containsKey(Dimension.IMPELLER)) {
      owner = part(equipment, "Impellers", "Impeller");
    }
    for (Map.Entry<Dimension, Double> entry : dimensions.entrySet()) {
      Dimension dimension = entry.getKey();
      Document document = equipment.getOwnerDocument();
      Element quantity = document.createElement("AggregatedDataValue");
      quantity.setAttribute("type", "Core/PhysicalQuantities.PhysicalQuantity");
      property(owner, dimension.getProperty()).appendChild(quantity);
      Element reference = document.createElement("DataReference");
      reference.setAttribute("data", dimension.getUnit());
      property(quantity, "Unit").appendChild(reference);
      Element number = document.createElement("Double");
      number.setTextContent(entry.getValue().toString());
      property(quantity, "Value").appendChild(number);
    }
  }

  /**
   * Adds an equipment-owned physical part.
   * 
   * @param equipment owner
   * @param relationship official composition
   * @param type official part class
   * @return the new part
   */
  private static Element part(Element equipment, String relationship, String type) {
    Document document = equipment.getOwnerDocument();
    Element parts = document.createElement("Components");
    parts.setAttribute("property", relationship);
    Element part = document.createElement("Object");
    part.setAttribute("id", equipment.getAttribute("id") + type);
    part.setAttribute("type", "Plant/ProcessEquipment." + type);
    parts.appendChild(part);
    equipment.appendChild(parts);
    return part;
  }

  /**
   * Adds a native data property.
   * 
   * @param owner native owner
   * @param name data property name
   * @return new property
   */
  private static Element property(Element owner, String name) {
    Element data = owner.getOwnerDocument().createElement("Data");
    data.setAttribute("property", name);
    owner.appendChild(data);
    return data;
  }
}
