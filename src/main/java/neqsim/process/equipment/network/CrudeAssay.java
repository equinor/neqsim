package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Thermodynamic crude assay on a declared common pseudo-component slate.
 */
public class CrudeAssay implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final String commonSlateId;
  private final String provenance;
  private final String effectiveDate;
  private final Map<String, NetworkMeasuredAttribute> attributes;
  private transient SystemInterface fluid;

  /**
   * Create an assay.
   *
   * @param name assay/grade name
   * @param commonSlateId declared common pseudo-component slate
   * @param fluid characterized fluid
   * @param provenance source
   * @param effectiveDate ISO-8601 effective date
   */
  public CrudeAssay(String name, String commonSlateId, SystemInterface fluid, String provenance, String effectiveDate) {
    this(name, commonSlateId, fluid, provenance, effectiveDate, new LinkedHashMap<String, NetworkMeasuredAttribute>());
  }

  private CrudeAssay(String name, String commonSlateId, SystemInterface fluid, String provenance, String effectiveDate,
      Map<String, NetworkMeasuredAttribute> attributes) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Assay name cannot be empty");
    }
    if (commonSlateId == null || commonSlateId.trim().isEmpty()) {
      throw new IllegalArgumentException("A declared common pseudo-component slate is required");
    }
    if (fluid == null) {
      throw new IllegalArgumentException("Assay fluid cannot be null");
    }
    this.name = name;
    this.commonSlateId = commonSlateId;
    this.fluid = fluid.clone();
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(this.fluid);
      operations.TPflash();
      this.fluid.initProperties();
    } catch (Exception ex) {
      throw new IllegalArgumentException("Assay fluid could not be initialized: " + ex.getMessage(), ex);
    }
    this.provenance = provenance;
    this.effectiveDate = effectiveDate;
    this.attributes = new LinkedHashMap<String, NetworkMeasuredAttribute>(attributes);
  }

  /**
   * Add a governed assay property with an explicit blending rule.
   *
   * @param attributeName attribute name
   * @param value value
   * @param unit unit
   * @param method test method
   * @param blendingRule mass-weighted, volume-weighted, calculate-from-EOS, or no-blend
   */
  public void addMeasuredAttribute(String attributeName, double value, String unit, String method,
      String blendingRule) {
    if (blendingRule == null || blendingRule.trim().isEmpty()) {
      throw new IllegalArgumentException("An explicit assay-property blending rule is required");
    }
    if (!"mass-weighted".equalsIgnoreCase(blendingRule) && !"volume-weighted".equalsIgnoreCase(blendingRule)
        && !"calculate-from-EOS".equalsIgnoreCase(blendingRule) && !"no-blend".equalsIgnoreCase(blendingRule)) {
      throw new IllegalArgumentException("Unsupported assay-property blending rule: " + blendingRule);
    }
    attributes.put(attributeName,
        new NetworkMeasuredAttribute(attributeName, value, unit, method, provenance, effectiveDate, blendingRule));
  }

  /** @return assay name */
  public String getName() {
    return name;
  }

  /** @return common pseudo-component slate identifier */
  public String getCommonSlateId() {
    return commonSlateId;
  }

  /** @return fluid clone */
  public SystemInterface getFluid() {
    if (fluid == null) {
      throw new IllegalStateException("Assay thermodynamic fluid is unavailable after metadata-only deserialization");
    }
    return fluid.clone();
  }

  /** @return provenance */
  public String getProvenance() {
    return provenance;
  }

  /** @return effective date */
  public String getEffectiveDate() {
    return effectiveDate;
  }

  /** @return immutable measured attributes */
  public Map<String, NetworkMeasuredAttribute> getAttributes() {
    return Collections.unmodifiableMap(attributes);
  }

  /**
   * Blend parcels using component molar-flow identity and explicit assay rules.
   *
   * @param blendName resulting assay name
   * @param parcels non-empty parcels
   * @return thermodynamic blend result
   */
  public static CrudeBlendResult blend(String blendName, List<CrudeParcel> parcels) {
    if (parcels == null || parcels.isEmpty()) {
      throw new IllegalArgumentException("At least one crude parcel is required");
    }
    validateCompatibleSlates(parcels);
    Map<String, Double> componentMoles = new LinkedHashMap<String, Double>();
    List<SystemInterface> fluids = new ArrayList<SystemInterface>();
    double totalMass = 0.0;
    double temperatureMass = 0.0;
    double pressureMass = 0.0;

    for (CrudeParcel parcel : parcels) {
      SystemInterface parcelFluid = parcel.getAssay().getFluid();
      fluids.add(parcelFluid);
      double parcelMoles = parcel.getMassKg() / parcelFluid.getMolarMass();
      double[] composition = parcelFluid.getMolarComposition();
      for (int index = 0; index < parcelFluid.getNumberOfComponents(); index++) {
        String componentName = parcelFluid.getPhase(0).getComponent(index).getComponentName();
        Double current = componentMoles.get(componentName);
        componentMoles.put(componentName, (current == null ? 0.0 : current) + parcelMoles * composition[index]);
      }
      totalMass += parcel.getMassKg();
      temperatureMass += parcel.getMassKg() * parcelFluid.getTemperature();
      pressureMass += parcel.getMassKg() * parcelFluid.getPressure("Pa");
    }

    double totalMoles = 0.0;
    for (Double moles : componentMoles.values()) {
      totalMoles += moles;
    }
    SystemInterface blendFluid = fluids.get(0).getEmptySystemClone();
    Map<String, ComponentInterface> componentDefinitions = new LinkedHashMap<String, ComponentInterface>();
    for (SystemInterface parcelFluid : fluids) {
      for (int index = 0; index < parcelFluid.getNumberOfComponents(); index++) {
        ComponentInterface component = parcelFluid.getPhase(0).getComponent(index);
        if (!componentDefinitions.containsKey(component.getComponentName())) {
          componentDefinitions.put(component.getComponentName(), component);
        }
      }
    }
    for (Map.Entry<String, Double> entry : componentMoles.entrySet()) {
      ComponentInterface component = componentDefinitions.get(entry.getKey());
      double moleFraction = entry.getValue() / totalMoles;
      if (component.isIsPlusFraction()) {
        blendFluid.addPlusFraction(entry.getKey(), moleFraction, component.getMolarMass(),
            component.getNormalLiquidDensity());
      } else if (component.isIsTBPfraction()) {
        blendFluid.addTBPfraction(entry.getKey(), moleFraction, component.getMolarMass(),
            component.getNormalLiquidDensity());
      } else {
        blendFluid.addComponent(entry.getKey(), moleFraction);
      }
    }
    blendFluid.setMixingRule(fluids.get(0).getMixingRuleName());
    double[] composition = new double[blendFluid.getNumberOfComponents()];
    for (int index = 0; index < blendFluid.getNumberOfComponents(); index++) {
      String componentName = blendFluid.getPhase(0).getComponent(index).getComponentName();
      Double moles = componentMoles.get(componentName);
      composition[index] = (moles == null ? 0.0 : moles) / totalMoles;
    }
    blendFluid.setMolarComposition(composition);
    blendFluid.setTemperature(temperatureMass / totalMass, "K");
    blendFluid.setPressure(pressureMass / totalMass / 1.0e5, "bara");
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(blendFluid);
      operations.TPflash();
      blendFluid.initProperties();
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to flash crude blend: " + ex.getMessage(), ex);
    }

    Map<String, NetworkMeasuredAttribute> blendedAttributes = blendAttributes(parcels, totalMass);
    CrudeAssay firstAssay = parcels.get(0).getAssay();
    CrudeAssay blendedAssay = new CrudeAssay(blendName, firstAssay.getCommonSlateId(), blendFluid,
        "Blend calculated from " + parcels.size() + " parcels", firstAssay.getEffectiveDate(), blendedAttributes);
    Map<String, Double> componentMass = calculateComponentMass(blendFluid, totalMass);
    double reconstructedMass = 0.0;
    for (Double mass : componentMass.values()) {
      reconstructedMass += mass;
    }
    return new CrudeBlendResult(blendedAssay, totalMass, componentMass, reconstructedMass - totalMass);
  }

  private static void validateCompatibleSlates(List<CrudeParcel> parcels) {
    CrudeAssay reference = parcels.get(0).getAssay();
    Map<String, ComponentInterface> definitions = new LinkedHashMap<String, ComponentInterface>();
    for (CrudeParcel parcel : parcels) {
      CrudeAssay assay = parcel.getAssay();
      if (!reference.getCommonSlateId().equals(assay.getCommonSlateId())) {
        throw new IllegalArgumentException("Incompatible crude slates: " + reference.getCommonSlateId() + " and "
            + assay.getCommonSlateId() + ". Re-cut to a declared common slate before blending.");
      }
      SystemInterface assayFluid = assay.getFluid();
      for (int index = 0; index < assayFluid.getNumberOfComponents(); index++) {
        ComponentInterface component = assayFluid.getPhase(0).getComponent(index);
        ComponentInterface existing = definitions.get(component.getComponentName());
        if (existing != null && (!approximatelyEqual(existing.getMolarMass(), component.getMolarMass())
            || !approximatelyEqual(existing.getNormalLiquidDensity(), component.getNormalLiquidDensity())
            || !approximatelyEqual(existing.getNormalBoilingPoint(), component.getNormalBoilingPoint()))) {
          throw new IllegalArgumentException("Incompatible pseudo-component definition for "
              + component.getComponentName() + ". Re-cut both assays to the same definitions.");
        }
        definitions.put(component.getComponentName(), component);
      }
    }
  }

  private static Map<String, NetworkMeasuredAttribute> blendAttributes(List<CrudeParcel> parcels, double totalMass) {
    Map<String, NetworkMeasuredAttribute> result = new LinkedHashMap<String, NetworkMeasuredAttribute>();
    Map<String, NetworkMeasuredAttribute> reference = parcels.get(0).getAssay().getAttributes();
    for (Map.Entry<String, NetworkMeasuredAttribute> entry : reference.entrySet()) {
      String attributeName = entry.getKey();
      String rule = entry.getValue().getBlendingRule();
      if ("calculate-from-EOS".equalsIgnoreCase(rule)) {
        continue;
      }
      if ("no-blend".equalsIgnoreCase(rule) && parcels.size() > 1) {
        continue;
      }
      boolean available = true;
      double weightedValue = 0.0;
      double totalVolume = 0.0;
      for (CrudeParcel parcel : parcels) {
        NetworkMeasuredAttribute attribute = parcel.getAssay().getAttributes().get(attributeName);
        if (attribute == null || !rule.equalsIgnoreCase(attribute.getBlendingRule())
            || !entry.getValue().getUnit().equalsIgnoreCase(attribute.getUnit())) {
          available = false;
          break;
        }
        if ("mass-weighted".equalsIgnoreCase(rule) || "no-blend".equalsIgnoreCase(rule)) {
          weightedValue += parcel.getMassKg() * attribute.getValue() / totalMass;
        } else if ("volume-weighted".equalsIgnoreCase(rule)) {
          SystemInterface fluid = parcel.getAssay().getFluid();
          double density = fluid.getDensity("kg/m3");
          if (!(density > 0.0) || !Double.isFinite(density)) {
            available = false;
            break;
          }
          double volume = parcel.getMassKg() / density;
          weightedValue += volume * attribute.getValue();
          totalVolume += volume;
        } else {
          available = false;
          break;
        }
      }
      if (available) {
        if ("volume-weighted".equalsIgnoreCase(rule)) {
          if (!(totalVolume > 0.0)) {
            continue;
          }
          weightedValue /= totalVolume;
        }
        result.put(attributeName, new NetworkMeasuredAttribute(attributeName, weightedValue, entry.getValue().getUnit(),
            entry.getValue().getMethod(), "Calculated blend attribute", null, rule));
      }
    }
    return result;
  }

  private static Map<String, Double> calculateComponentMass(SystemInterface fluid, double totalMass) {
    Map<String, Double> componentMass = new LinkedHashMap<String, Double>();
    double averageMolarMass = fluid.getMolarMass();
    double[] composition = fluid.getMolarComposition();
    for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
      ComponentInterface component = fluid.getPhase(0).getComponent(index);
      componentMass.put(component.getComponentName(),
          totalMass * composition[index] * component.getMolarMass() / averageMolarMass);
    }
    return componentMass;
  }

  private static boolean approximatelyEqual(double first, double second) {
    if (!Double.isFinite(first) || !Double.isFinite(second)) {
      return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }
    return Math.abs(first - second) <= 1.0e-8 * Math.max(Math.max(Math.abs(first), Math.abs(second)), 1.0);
  }
}
