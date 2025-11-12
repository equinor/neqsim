package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.exception.IsNaNException;

/**
 * <p>
 * bubblePointPressureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class BubblePointPressureFlash extends ConstantDutyPressureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BubblePointPressureFlash.class);

  /**
   * <p>
   * Constructor for bubblePointPressureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public BubblePointPressureFlash(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    system.init(0);
    boolean singleComponent = system.getPhase(0).getNumberOfComponents() == 1;
    double minPurePressure = Double.NaN;
    double maxPurePressure = Double.NaN;
    if (singleComponent) {
      ComponentInterface comp = system.getPhase(0).getComponent(0);
      double tc = comp.getTC();
      if (system.getTemperature() >= tc * (1.0 - 1e-10)) {
        throw new IllegalStateException("System is supercritical");
      }
      double pc = comp.getPC();
      double t = system.getTemperature();
      double pGuess = comp.getAntoineVaporPressure(t);
      minPurePressure = comp.getTriplePointPressure();
      if (!Double.isFinite(minPurePressure) || minPurePressure <= 0.0) {
        minPurePressure = 1e-6;
      }
      maxPurePressure = 0.999 * pc;
      double minGuess = minPurePressure;
      double maxGuess = maxPurePressure;
      if (Double.isNaN(pGuess) || pGuess < minGuess || pGuess > maxGuess) {
        // Fall back to a Clausius-Clapeyron style extrapolation using the component heat of
        // vaporisation.
        double referenceTemperature = Math.max(comp.getTriplePointTemperature(), tc - 10.0);
        double refPressure = comp.getAntoineVaporPressure(referenceTemperature);
        if (Double.isNaN(refPressure) || refPressure <= 0) {
          refPressure = Math.max(minGuess, Math.min(maxGuess, system.getPressure()));
        }
        double dhvap = comp.getPureComponentHeatOfVaporization(referenceTemperature);
        if (Double.isNaN(dhvap) || dhvap == 0.0) {
          dhvap = comp.getPureComponentHeatOfVaporization(t);
        }
        double r = neqsim.thermo.ThermodynamicConstantsInterface.R;
        double invTRef = 1.0 / referenceTemperature;
        double invT = 1.0 / t;
        double exponent = -dhvap / r * (invT - invTRef);
        pGuess = refPressure * Math.exp(exponent);
      }
      pGuess = Math.max(minGuess, Math.min(maxGuess, pGuess));
      system.setPressure(pGuess);
    }

    int iterations = 0;
    int maxNumberOfIterations = 500;
    double yold = 0;
    double ytotal = 1;
    double residual = Double.NaN;
    // double deriv = 0, funk = 0;
    boolean chemSolved = true;
    // logger.info("starting");
    // system.setPressure(1.0);
    system.init(0);
    system.setNumberOfPhases(2);
    system.setBeta(1, 1.0 - 1e-3);
    system.setBeta(0, 1e-3);

    double oldPres = 0;
    double oldChemPres = Math.max(1e-4, system.getPressure());
    double lowerPressure = Double.NaN;
    double upperPressure = Double.NaN;
    double lastStablePressure = system.getPressure();

    // For multicomponent systems, ensure we have a reasonable starting pressure
    // that's not too high (which could be supercritical or cause NaN)
    if (!singleComponent) {
      double currentPressure = system.getPressure();

      // Analyze fluid composition to improve initial guess
      boolean hasWater = false;
      boolean hasTBPfractions = false;
      boolean hasHeavyComponents = false;
      double lightMoleFraction = 0.0;
      double heavyMoleFraction = 0.0;
      double avgPc = 0.0;
      double avgAntoinePressure = 0.0;
      int antoineCount = 0;
      int count = 0;

      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        ComponentInterface comp = system.getPhases()[0].getComponent(i);
        double zi = comp.getz();
        if (zi > 1e-10) {
          String compName = comp.getComponentName().toLowerCase();

          // Check for water
          if (compName.contains("water") || compName.equals("h2o")) {
            hasWater = true;
          }

          // Check for TBP fractions (typically have "tbp" or "plus" in name, or are added via
          // addTBPfraction)
          if (compName.contains("tbp") || compName.contains("plus") || comp.isIsTBPfraction()) {
            hasTBPfractions = true;
          }

          // Classify components by molecular weight
          double mw = comp.getMolarMass();
          if (mw < 50.0) {
            lightMoleFraction += zi;
          } else if (mw > 150.0) {
            heavyMoleFraction += zi;
            hasHeavyComponents = true;
          }

          avgPc += zi * comp.getPC();
          count++;

          // Try to get Antoine vapor pressure for initial guess
          try {
            double antoineP = comp.getAntoineVaporPressure(system.getTemperature());
            if (Double.isFinite(antoineP) && antoineP > 0.0 && antoineP < 1000.0) {
              avgAntoinePressure += zi * antoineP;
              antoineCount++;
            }
          } catch (Exception e) {
            // Skip if Antoine fails
          }
        }
      }

      if (count > 0) {
        avgPc /= count;
      }

      // Determine a better initial pressure guess based on fluid characteristics
      double initialGuess = currentPressure;

      // For complex fluids with TBP fractions, water, or heavy components, use a conservative
      // approach
      if (hasTBPfractions || hasWater || hasHeavyComponents) {
        // Use Antoine-based estimate if available
        if (antoineCount > 0 && avgAntoinePressure > 0.0) {
          // Weight Antoine pressure more heavily for light components
          double antoineFactor = Math.max(0.3, lightMoleFraction);
          initialGuess = avgAntoinePressure * (1.0 + antoineFactor);
          initialGuess = Math.min(initialGuess, 0.4 * avgPc); // Cap at 40% of pseudo-critical
        } else {
          // Fallback: use a fraction of pseudo-critical pressure based on composition
          if (heavyMoleFraction > 0.3) {
            // Heavy oil-like mixture: very conservative guess
            initialGuess = Math.min(currentPressure, 0.15 * avgPc);
          } else {
            // Mixed composition: moderate guess
            initialGuess = Math.min(currentPressure, 0.25 * avgPc);
          }
        }

        // For water-containing systems, ensure pressure is reasonable for aqueous phase
        if (hasWater) {
          double waterSatP = 0.0;
          for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
            ComponentInterface comp = system.getPhases()[0].getComponent(i);
            String compName = comp.getComponentName().toLowerCase();
            if (compName.contains("water") || compName.equals("h2o")) {
              try {
                waterSatP = comp.getAntoineVaporPressure(system.getTemperature());
                if (Double.isFinite(waterSatP) && waterSatP > 0.0) {
                  // Don't start below water saturation pressure for water-containing systems
                  initialGuess = Math.max(initialGuess, waterSatP * 0.5);
                }
              } catch (Exception e) {
                // Continue with current guess
              }
              break;
            }
          }
        }
      } else if (currentPressure > 100.0) {
        // Standard multicomponent case with high initial pressure
        initialGuess = Math.max(currentPressure * 0.5, Math.min(currentPressure, 0.3 * avgPc));
      }

      // Apply bounds to ensure physically reasonable pressure
      initialGuess = Math.max(1e-4, Math.min(initialGuess, 0.95 * avgPc));

      // Only update if we computed a better guess
      if (Math.abs(initialGuess - currentPressure) / Math.max(currentPressure, 1.0) > 0.01) {
        system.setPressure(initialGuess);
        lastStablePressure = system.getPressure();
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Adjusted initial pressure for complex fluid: {} bar (TBP={}, water={}, heavy={})",
              initialGuess, hasTBPfractions, hasWater, hasHeavyComponents);
        }
      }
    }
    if (system.isChemicalSystem()) {
      system.getChemicalReactionOperations().solveChemEq(1, 0);
      system.getChemicalReactionOperations().solveChemEq(1, 1);
    }

    for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
      system.getPhases()[1].getComponent(i).setx(system.getPhases()[0].getComponent(i).getz());
      if (system.getPhases()[0].getComponent(i).getIonicCharge() != 0) {
        system.getPhases()[0].getComponent(i).setx(1e-40);
      } else {
        system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getK()
            * system.getPhases()[1].getComponent(i).getz());
      }
    }

    ytotal = 0.0;
    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      ytotal += system.getPhases()[0].getComponent(i).getx();
    }
    residual = ytotal - 1.0;

    double ktot = 0.0;
    int chemIter = 0;

    chemLoop: do {
      chemIter++;
      oldChemPres = system.getPressure();
      iterations = 0;
      do {
        iterations++;
        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          system.getPhases()[0].getComponent(i)
              .setx(system.getPhases()[0].getComponent(i).getx() / ytotal);
        }
        boolean initSucceeded = false;
        int initAttempts = 0;
        RuntimeException lastInitError = null;
        double attemptPressure = system.getPressure();
        while (!initSucceeded && initAttempts < 10) {
          try {
            system.init(3);
            initSucceeded = true;
            lastStablePressure = system.getPressure();
          } catch (RuntimeException initException) {
            if (!isCausedByNaN(initException)) {
              throw initException;
            }
            lastInitError = initException;
            initAttempts++;

            double fallbackPressure;
            if (Double.isFinite(lowerPressure) && Double.isFinite(upperPressure)
                && lowerPressure < upperPressure) {
              fallbackPressure = 0.5 * (lowerPressure + upperPressure);
            } else if (Double.isFinite(lastStablePressure)) {
              fallbackPressure = 0.5 * (lastStablePressure + attemptPressure);
            } else if (Double.isFinite(lowerPressure)) {
              fallbackPressure = lowerPressure;
            } else if (Double.isFinite(upperPressure)) {
              fallbackPressure = upperPressure;
            } else {
              fallbackPressure = attemptPressure * 0.5;
            }

            if (singleComponent) {
              double lowerBound = Double.isFinite(minPurePressure) ? minPurePressure : 1e-8;
              double upperBound =
                  Double.isFinite(maxPurePressure) ? maxPurePressure : Double.POSITIVE_INFINITY;
              fallbackPressure = Math.max(lowerBound, Math.min(upperBound, fallbackPressure));
            }

            fallbackPressure = Math.max(1e-8, fallbackPressure);
            if (Math.abs(fallbackPressure - attemptPressure) <= 1e-12
                * Math.max(1.0, attemptPressure)) {
              fallbackPressure = Math.max(1e-8, 0.5 * fallbackPressure);
            }

            attemptPressure = fallbackPressure;
            if (logger.isDebugEnabled()) {
              logger.debug("Recovering from NaN molar volume at pressure {} bara, attempt {}",
                  attemptPressure, initAttempts);
            }
            system.setPressure(attemptPressure);
          }
        }

        if (!initSucceeded) {
          throw lastInitError;
        }

        oldPres = system.getPressure();
        ktot = 0.0;
        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
          do {
            yold = system.getPhases()[0].getComponent(i).getx();
            if (!Double.isNaN(
                Math.exp(Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient())
                    - Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient())))) {
              if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                  || system.getPhase(0).getComponent(i).isIsIon()) {
                system.getPhases()[0].getComponent(i).setK(1e-40);
              } else {
                system.getPhases()[0].getComponent(i).setK(Math.exp(
                    Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient()) - Math
                        .log(system.getPhases()[0].getComponent(i).getFugacityCoefficient())));
              }
            }
            system.getPhases()[1].getComponent(i)
                .setK(system.getPhases()[0].getComponent(i).getK());
            system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getK()
                * system.getPhases()[1].getComponent(i).getz());
            // logger.info("y err " +
            // Math.abs(system.getPhases()[0].getComponent(i).getx()-yold));
          } while (Math.abs(system.getPhases()[0].getComponent(i).getx() - yold) / yold > 1e-8);
          ktot += Math.abs(system.getPhases()[1].getComponent(i).getK() - 1.0);
        }
        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          if (!Double.isNaN(system.getPhases()[0].getComponent(i).getK())) {
            system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getK()
                * system.getPhases()[1].getComponent(i).getz());
          } else {
            system.init(0);
            logger.error("k err. : nan");
          }
        }

        ytotal = 0.0;
        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          ytotal += system.getPhases()[0].getComponent(i).getx();
        }

        residual = ytotal - 1.0;
        if (Double.isFinite(residual)) {
          if (residual > 0.0) {
            lowerPressure = Double.isNaN(lowerPressure) ? system.getPressure()
                : Math.max(lowerPressure, system.getPressure());
          } else if (residual < 0.0) {
            upperPressure = Double.isNaN(upperPressure) ? system.getPressure()
                : Math.min(upperPressure, system.getPressure());
          }
          if (singleComponent) {
            if (Double.isFinite(lowerPressure)) {
              lowerPressure = Math.max(lowerPressure, minPurePressure);
            }
            if (Double.isFinite(upperPressure)) {
              upperPressure = Math.min(upperPressure, maxPurePressure);
            }
          }
        }

        double newPressure = system.getPressure();
        double derivative = 0.0;
        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
          derivative += system.getPhases()[1].getComponent(i).getz()
              * system.getPhases()[0].getComponent(i).getK()
              * (system.getPhases()[1].getComponent(i).logfugcoefdP(system.getPhase(1))
                  - system.getPhases()[0].getComponent(i).logfugcoefdP(system.getPhase(0)));
        }

        boolean usedNewton = false;
        if (Double.isFinite(residual) && Math.abs(derivative) > 1e-20) {
          double step = -residual / derivative;
          double trial = system.getPressure() + step;
          if (trial > 0.0 && (!Double.isFinite(lowerPressure) || trial > lowerPressure)
              && (!Double.isFinite(upperPressure) || trial < upperPressure)) {
            newPressure = trial;
            usedNewton = true;
          } else {
            double damping = 1.0;
            while (damping > 1e-6) {
              trial = system.getPressure() + damping * step;
              if (trial > 0.0 && (!Double.isFinite(lowerPressure) || trial > lowerPressure)
                  && (!Double.isFinite(upperPressure) || trial < upperPressure)) {
                newPressure = trial;
                usedNewton = true;
                break;
              }
              damping *= 0.5;
            }
          }
        }

        if (!usedNewton) {
          if (!Double.isFinite(residual)) {
            newPressure = Math.max(1e-6, system.getPressure() * 1.1);
          } else if (Double.isFinite(lowerPressure) && Double.isFinite(upperPressure)
              && lowerPressure < upperPressure) {
            newPressure = 0.5 * (lowerPressure + upperPressure);
          } else if (Double.isFinite(lowerPressure) && residual > 0.0) {
            newPressure = Math.max(lowerPressure * 1.05, system.getPressure() * 1.1);
          } else if (Double.isFinite(upperPressure) && residual < 0.0) {
            newPressure = Math.min(upperPressure * 0.95, system.getPressure() * 0.9);
          } else {
            newPressure =
                Math.max(1e-6, system.getPressure() * (1.0 - Math.signum(residual) * 0.1));
          }
        }

        newPressure = Math.max(1e-6, newPressure);
        if (singleComponent) {
          double lowerBound = Double.isFinite(minPurePressure) ? minPurePressure : 1e-6;
          double upperBound =
              Double.isFinite(maxPurePressure) ? maxPurePressure : Double.POSITIVE_INFINITY;
          newPressure = Math.max(lowerBound, Math.min(upperBound, newPressure));
        }
        system.setPressure(newPressure);
        if (system.getPressure() < 0) {
          system.setPressure(oldChemPres / 2.0);
          continue chemLoop;
        }
        if (!Double.isFinite(system.getPressure())) {
          system.setPressure(oldChemPres);
          continue chemLoop;
        }
        // logger.info("iter in bub calc " + iterations + " pres " +
        // system.getPressure()+ " ytot " + ytotal + " chem iter " + chemIter);
      } while (((((Math.abs(ytotal - 1.0)) > 1e-7)
          || Math.abs(oldPres - system.getPressure()) / oldPres > 1e-6)
          && (iterations < maxNumberOfIterations)) || iterations < 5);

      if (system.isChemicalSystem()) { // && (iterations%3)==0 && iterations<50){
        chemSolved = system.getChemicalReactionOperations().solveChemEq(1, 1);
        system.setBeta(1, 1.0 - 1e-3);
        system.setBeta(0, 1e-3);
      }
      // logger.info("iter in bub calc " + iterations + " pres " +
      // system.getPressure()+ " chem iter " + chemIter);
    } while ((Math.abs(oldChemPres - system.getPressure()) / oldChemPres > 1e-6 || chemIter < 2
        || !chemSolved) && chemIter < 20);
    // if(system.getPressure()>300) system.setPressure(300.0);
    // logger.info("iter in bub calc " + iterations + " pres " +
    // system.getPressure()+ " chem iter " + chemIter);
    // logger.info("iter " + iterations + " XTOT " +ytotal + " ktot " +ktot);
    system.init(1);
    double pseudoTc = system.getPhase(0).getPseudoCriticalTemperature();
    double pseudoPc = system.getPhase(0).getPseudoCriticalPressure();
    boolean nearCritical =
        system.getTemperature() >= 0.999 * pseudoTc && system.getPressure() >= 0.999 * pseudoPc;
    boolean converged = Double.isFinite(residual) && Math.abs(residual) <= 1e-7
        && Math.abs(oldPres - system.getPressure()) / system.getPressure() <= 1e-6;
    boolean mixtureCollapsed = ktot < 1e-4 && system.getPhase(0).getNumberOfComponents() > 1;
    setSuperCritical(!converged && !nearCritical && mixtureCollapsed);
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  private static boolean isCausedByNaN(Throwable throwable) {
    while (throwable != null) {
      if (throwable instanceof IsNaNException) {
        return true;
      }
      throwable = throwable.getCause();
    }
    return false;
  }
}
