package neqsim.util.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents a parameter for AI consumption.
 * 
 * <p>
 * Provides structured metadata about method parameters that AI agents can parse to understand valid
 * inputs, units, and constraints.
 * </p>
 * 
 * <h2>Usage:</h2>
 * 
 * <pre>
 * {@code
 * public void setTemperature(
 *     &#64;AIParameter(
 *       name = "temperature",
 *       description = "Temperature value",
 *       unit = "K",
 *       minValue = 0.0,
 *       maxValue = 1000.0
 *     ) double temperature
 * ) { ... }
 * }
 * </pre>
 * 
 * @author NeqSim
 * @version 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface AIParameter {

  /**
   * Parameter name.
   * 
   * @return parameter name
   */
  String name() default "";

  /**
   * Human-readable description.
   * 
   * @return description text
   */
  String description() default "";

  /**
   * Physical unit (SI preferred).
   * 
   * <p>
   * Examples: "K", "bar", "kg/hr", "mol/s", "J/mol", "Pa.s"
   * </p>
   * 
   * @return unit string
   */
  String unit() default "";

  /**
   * Minimum valid value.
   * 
   * @return minimum value
   */
  double minValue() default Double.NEGATIVE_INFINITY;

  /**
   * Maximum valid value.
   * 
   * @return maximum value
   */
  double maxValue() default Double.POSITIVE_INFINITY;

  /**
   * Default value if not specified.
   * 
   * @return default value as string
   */
  String defaultValue() default "";

  /**
   * Whether this parameter is required.
   * 
   * @return true if required
   */
  boolean required() default true;

  /**
   * Valid options for enum-like parameters.
   * 
   * @return array of valid options
   */
  String[] options() default {};
}
