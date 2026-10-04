# Bounded flare-radiation screening contract

`runFlareNetwork` delegates to the canonical
`neqsim.process.equipment.flare.Flare` model. Inventory 1.46 promotes the tool to
`CONTRACT_TESTED` for bounded software-contract behavior only.

Qualified evidence:

- exactly one caller-supplied heat-duty basis (`heatDuty_MW` or `heatDuty_W`);
- requests no larger than 16,384 UTF-8 bytes;
- one to 200 positive finite distances, each no greater than 100,000 m;
- positive finite heat duty, flame height, and radiant fraction with explicit upper bounds;
- deterministic radiation-profile and reference-threshold contour envelopes;
- stable fail-closed error codes;
- direct Java tests and real packaged MCP STDIO qualification.

The output is screening evidence. It does not establish dispersion or weather effects,
terrain or shielding, multi-flare interaction, mechanical design, project-specific siting,
safe operating limits, API 521/API 537 or other standards conformance, regulatory
acceptance, plant authority, certification, or accountable engineering approval.
