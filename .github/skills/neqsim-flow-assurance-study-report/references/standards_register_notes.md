# Standards register notes

The shipped seed register (`devtools/fa_templates/standards_register.yaml`) holds public publisher
metadata only (designation, title, edition or status as shown on a publisher page, check date).
Standard texts are not available to the toolkit and are never copied.

`assessment_status` values: `CONVENTION ADOPTED` (a documentation convention such as title-block fields),
`NOT APPLIED`, `NOT ASSESSED` (text not read, no compliance statement), `UNVERIFIED` (status could not
be confirmed). The gate `FA-STD-VERIFY` is a WARN while any cited standard is unverified; the report
lists the status in Appendix B.

Rules:

- Re-verify edition and status before issue (`neqsim-standards-lookup`); a carried-over entry is not verified.
- A project criterion (C-xxx) is not a code requirement unless its source says so.
- Never state compliance with a standard: the toolkit screens against project criteria only.
- ISO 10628-2 and the ASME B31 entries are UNVERIFIED in the seed register.
- DNV-RP-O501 (erosion) is NOT implemented; the erosional-velocity criterion is a project C-factor check.
