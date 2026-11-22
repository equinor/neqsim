# Standards verification record (checked 2026-10-20)

Purpose: keep the editions and evidence limits of the standards used by `process-study-report` honest.
**Re-verify before every issue**; editions, corrigenda and status change. Never claim compliance beyond
the text actually read.

| Designation | Edition checked | Status found | Evidence used | Availability |
|---|---|---|---|---|
| ISO 10628-1 | 2014, "Specification of diagrams" | Listed as current by an accredited reseller; no withdrawal notice found; iso.org catalogue page could not be retrieved (JavaScript catalogue, search paths disallowed by robots.txt) | Reseller product page (en-standard.eu) with scope text: classification, content and representation of flow diagrams, drafting rules; "collective application standard of ISO 15519" | Paywalled |
| ISO 10628-2 | 2012, "Graphical symbols" | Listed as current by the same reseller; same limitation | Reseller page; scope text states it is a collective application standard of the ISO 14617 series and does not apply to electrotechnical diagrams | Paywalled |
| ISO 14617-1 | 2005 (edition 2) | Listed as current; parts 2 to 15 not individually checked | Reseller page | Paywalled |
| NORSOK P-002 | 2023+AC:2024 (edition 2, 2024 corrigendum, published 2024-12-20; supersedes P-002:2023; the pre-2023 text dated from August 2014) | Current | Standards Norway sector page and revision-information PDF; webshop page; cover page and foreword of a licensed copy. The foreword lists six editorial corrections (a reference to API 520, "production pipeline", Table 2 footnote numbering, two note wordings, the title of system 46). Normative references do not include ISO 10628, ISO 14617 or IEC 62424 | Paywalled |
| DEXPI | 2.0.1 | Current public specification (replaces 2.0); not an ISO/IEC standard | dexpi.org news | Public |
| IEC 62424 | not verified | edition and status could not be retrieved | none | unknown |

URLs fetched: https://www.en-standard.eu/ (ISO 10628-1, ISO 10628-2, ISO 14617-1 pages),
https://standard.no/en/sectors/petroleum/norsok-standards/p-process/,
https://online.standard.no/nb/norsok-p-002-2023, https://dexpi.org/.

Consequences for reports:

1. ISO 10628-1/-2 and ISO 14617: state "conventions applied, conformance not claimed" unless the licensed
   text was read and a clause-by-clause review was done.
2. NORSOK P-002 is a process-system **design** standard (design pressure and temperature, process safety
   system design, line sizing, isolation, insulation and heat tracing). A process-simulation report can only
   claim compliance for the items it has data for; without a line list or design pressures write "not assessed".
3. Internal repository tables that label NORSOK P-002 with a revision string should be reconciled with
   "NORSOK P-002:2023+AC:2024" before being cited.
4. Company or project requirement documents (for example a PFD/P&ID technical requirement) are `mandatory`
   rows in the register and, when supplied, are handled as a requirement matrix with per-item evidence.
