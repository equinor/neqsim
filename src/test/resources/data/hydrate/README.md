# Semenov methane hydrate dissociation assessment

Semenov, Gong, Medvedev, Stoporev, Istomin, Vinokurov and Li (2023) measured complete dissociation in a stirred 600 mL GHA350 cell. The importer reads Table 1 of the Data in Brief article directly from the publisher's JATS XML (CC BY-NC-ND 4.0). Table 1 pressures are absolute. The public XML remains upstream (SHA-256 `33aa2b3ce6357dfe7d15e3fb466e59d5813b9f8d736a5fed782178d3425c5715`). Each output point number is the one-based row within the named solution series. The authors report expanded uncertainties of 0.1 K and 0.02 MPa at k = 2. Every included run used a heating rate of 0.1 K/h except Me50 point 3 at 0.5 K/h. [Experimental endpoints](https://doi.org/10.1016/j.dib.2023.108892) · [Companion research article](https://doi.org/10.1016/j.ces.2022.118387)

The mass columns in [semenov_2023_charges.csv](semenov_2023_charges.csv) are transcribed in grams from Anton Semenov's CC BY 4.0 raw data, version 2, archive **CH4 hydrate equilibrium measurements.zip**, SHA-256 `1d7cfed71520b66c93734cc788a4e4f766cf6d9606f527586449504eb05e911f`. Cells D2:F3 of each inhibitor workbook identify the solute and water, measured masses and derived mass percentages. The available workbooks within each concentration series have identical charge entries. The DW control uses the article's 350 g water charge. Only the ten solution recipes and control are distributed here. [Raw data](https://doi.org/10.17632/zwpgb24f9j.2) · [Raw-data license](https://creativecommons.org/licenses/by/4.0/)

From the repository root, with Python, JPype1 and a Java runtime compatible with the built classes:

```bash
./mvnw -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/semenov-classpath.txt
curl -fsSLo /tmp/PMC9876827.1.xml https://pmc-oa-opendata.s3.amazonaws.com/PMC9876827.1/PMC9876827.1.xml
python devtools/assess_semenov_hydrates.py --article /tmp/PMC9876827.1.xml \
  --classpath "target/classes:$(cat target/semenov-classpath.txt)" --output /tmp/semenov.csv
```

The output contains every run, including diagnostic status on failure. The grouped statistics use the 59 baseline inventories only. Each row reports calculated minus measured temperature, structure, fluid phases, solute mole fraction in water, fraction of the charged species in vapor, relative hydrate water fugacity residual and maximum component balance residual. For DW the reported species is water. Each baseline includes independent fixed-sI and fixed-sII temperatures. The automatic result must equal the warmer root within 0.0002 K. The two additional inventory runs per observation quantify sensitivity to the inferred methane charge. All concentrations were evaluated with default parameters. [Hydrate guide and summary](../../../../../docs/thermo/hydrate_models.md#methane-hydrate-dissociation-with-methanol-and-meg)
