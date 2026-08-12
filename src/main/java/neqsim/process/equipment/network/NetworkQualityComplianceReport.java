package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Point-specific quality compliance report.
 */
public class NetworkQualityComplianceReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String nodeName;
  private final String profileName;
  private final String profileVersion;
  private final boolean compliant;
  private final List<String> namedExceptions;
  private final List<NetworkQualityResult> results;

  /**
   * Create a compliance report.
   *
   * @param nodeName named network point
   * @param profile quality profile
   * @param results metric results
   */
  public NetworkQualityComplianceReport(String nodeName, NetworkQualityProfile profile,
      List<NetworkQualityResult> results) {
    this.nodeName = nodeName;
    this.profileName = profile.getName();
    this.profileVersion = profile.getVersion();
    this.namedExceptions = new ArrayList<String>(profile.getNamedExceptions());
    this.results = new ArrayList<NetworkQualityResult>(results);
    boolean allPass = true;
    for (NetworkQualityResult result : results) {
      if (result.getStatus() != NetworkQualityResult.Status.PASS) {
        allPass = false;
        break;
      }
    }
    compliant = allPass;
  }

  /** @return named network point */
  public String getNodeName() {
    return nodeName;
  }

  /** @return profile name */
  public String getProfileName() {
    return profileName;
  }

  /** @return profile version */
  public String getProfileVersion() {
    return profileVersion;
  }

  /** @return true only when every required metric passes */
  public boolean isCompliant() {
    return compliant;
  }

  /** @return named exceptions */
  public List<String> getNamedExceptions() {
    return Collections.unmodifiableList(namedExceptions);
  }

  /** @return immutable metric results */
  public List<NetworkQualityResult> getResults() {
    return Collections.unmodifiableList(results);
  }

  /** @return stable JSON representation */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }

  /**
   * Restore a report from JSON.
   *
   * @param json serialized report
   * @return report
   */
  public static NetworkQualityComplianceReport fromJson(String json) {
    return new Gson().fromJson(json, NetworkQualityComplianceReport.class);
  }
}
