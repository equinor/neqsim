# Independent cubic-EOS gradient reference

This fixture qualifies the finite 20-step gradient algorithm used by
`TPgradientFlash`, for the five-component gas in `inputs.json`. It does not
qualify a continuous-depth integration or thermal diffusion.

The data records the existing NeqSim SRK and PR cubic coefficients, molar masses,
and binary interactions at each of the 21 temperatures. They were extracted from
PR #4577 head `02917efba52dc87e412dcdbad8d222c6db5fc8d1`; no fitted parameters were
changed. Values are stored as decimal strings to preserve their supplied digits.
`ExtractGradientInputs.java.txt` shows the extraction API and writes the original
CSV fields; pass an output path and add a second argument to select PR.

Run from any working directory with standard-library Python 3:

```text
python3 src/test/resources/neqsim/thermodynamicoperations/flashops/gradient-cubic-reference/reproduce.py
```

The reproducer implements the explicit SRK/PR cubic roots and log-fugacity
coefficients directly. It solves the composition and pressure with centered
finite-difference Jacobians and pivoted Gaussian elimination; it calls neither
NeqSim nor its analytical derivatives. At increment $s$, its equations are

$$\ln[\phi_i(T_s,P_s,x_s)x_{i,s}P_s]-\ln[\phi_i(T_{s-1},P_{s-1},x_{s-1})x_{i,s-1}P_{s-1}]=\frac{M_i g\Delta h}{RT_{s-1}}$$

and $\sum_i x_{i,s}=1$. Temperature advances from 360 to 390 K in 20 steps,
pressure starts at 250 bara, and depth advances by 150 m per step. Both models
have a single pressure-valid gas root throughout this case.

`reference.json` retains the independent 60- and 80-digit results. The final
pressure and composition agree between these precisions to more than 50 decimal
places. The final pressures are approximately 279.7907442694809963 bara (SRK)
and 280.0017890516890656 bara (PR). `TPgradientFlashTest` retains its original
absolute acceptance limits of `1e-12` bara and `1e-14` mole fraction.

## Reproduced defect and work count

The original solver divided logarithmic fugacity derivatives by the fugacity
coefficient a second time. The centered-difference Jacobian regressions fail
against the original solver for both EOS. For SRK component nitrogen, the
pressure derivative was about 0.004211735079/bara versus the independent
residual difference 0.004401945748/bara; PR similarly gave 0.004099214148 versus
0.004217131701/bara.

The old SRK endpoint, 279.7907442693575 bara, retained approximately `1.24e-10`
bara of finite-iteration error. Its prior golden value differed by only a few
floating-point units, while both differed from the independently solved
reference. The reference was therefore recomputed from the equations, rather
than copied from the new Java output or accepted through wider tolerances.

A counting subclass measured 341 calls to `init(3)` for the old SRK calculation
and 101 for the corrected one, including step finalization. This is a work count
for this one gas fixture, not a general timing or performance guarantee.
