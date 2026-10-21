# Report skeleton and requirement mapping

Order of the generated Word report (`psr_report.ReportBuilder.build`). Main body A4 portrait; PFD pages and
appendices A3 landscape.

| Part | Content | Requirement |
|---|---|---|
| Cover, document control, contents, lists of figures and tables | document number, revision history, issue status, readiness banner, TOC/LOF/LOT fields | 3 |
| 1 Executive summary | objective, principal-results table (numbers via tokens), findings, limitations (data gaps), recommendations | 3 |
| 2 Scope, objectives and operating cases | audience, maturity, boundaries (included, excluded, **unmodelled systems**), cases, acceptance criteria with automatic status | 1, 3 |
| 3 Design basis | sources inspected, conventions, measured/specified/calculated/estimated/unavailable classification, assumptions, exclusions, data gaps with effect | 1 |
| 4 Standards and conventions | standards register summary with edition, status, class and compliance statement | 2 |
| 5 Process description | per area: every unit tag with its numbered inlet and outlet streams; optional narrative per area | 3 |
| 6 Simulation methodology | model/version, EOS and mixing rule, fluid characterisation, pseudo-component table, convergence, balance tolerances, validation against measurements | 1, 3, 5 |
| 7 BFD and PFDs | BFD page, one A3 page per PFD sheet, captions and alt text | 4 |
| 8 Results | boundary streams, unit summaries by category, utilities roll-up, balances (overall, area, exceptions), case comparison charts, sensitivities, constraints, interpretation | 3 |
| 9 Conclusions and recommendations, 10 References | | 3 |
| Appendix A | equipment register and unit results (every unit, every case) | 3, 4 |
| Appendix B | B.1 stream register; B.2 properties per case; B.3 phase table; B.4 complete composition per case; B.5 phase compositions of multiphase streams; B.6 compact mole-% composition with C6+ sum | 5 |
| Appendix C | component and pseudo-component properties (MW, Tc, Pc, acentric factor, Tb, liquid density) | 5 |
| Appendix D | unit-level mass, component and energy balances | 3 |
| Appendix E | full standards register | 2 |
| Appendix F | validation report (machine-readable twin: `validation_report.json`) | 6 |
| Appendix G | requirement compliance matrices (company/project requirement sets) | 2 |

Minimum stream data per stream and case (tables B.2, B.3, B.4, B.6 and the CSV/XLSX/JSON exports): number, name,
source, destination, T, P (bara and barg in the export), total mass and molar flow, liquid mass flow, actual
liquid and vapour volume flow, standard volumes (flash and ideal-gas) with reference conditions, phase state
and phase fractions (mole basis stated; volume fractions at actual conditions), phase flow, density, MW, Z and
viscosity, specific enthalpy and enthalpy flow, complete overall composition, phase compositions of
multiphase streams, component flows (kmol/h and kg/h) in the exports.
