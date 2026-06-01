package neqsim.process.research;

/**
 * Material node used by process-network candidate generation.
 *
 * <p>
 * A material node names an intermediate or product state in a process synthesis graph. It can refer
 * to a physical stream class, a dominant component, or a higher-level material state such as
 * syngas, dry gas, liquid hydrocarbon, or purified product.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class MaterialNode {
  private final String name;
  private final String description;
  private final String componentName;

  /**
   * Creates a material node.
   *
   * @param name node name; must be non-empty
   * @param description short engineering description; may be empty
   * @param componentName dominant component name; may be null
   */
  public MaterialNode(String name, String description, String componentName) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Material node name cannot be empty");
    }
    this.name = name;
    this.description = description == null ? "" : description;
    this.componentName = componentName;
  }

  /**
   * Gets the material node name.
   *
   * @return material node name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the material description.
   *
   * @return material description, possibly empty
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the dominant component name.
   *
   * @return component name, or null when not component-specific
   */
  public String getComponentName() {
    return componentName;
  }
}
