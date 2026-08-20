# Acknowledgements

This project is a port of **[vanna-ai/vanna](https://github.com/vanna-ai/vanna)**.

## Licence

`vanna-ai/vanna` is **MIT**, © 2024 Vanna.AI — read from its own `LICENSE` file, not from a
badge.

## Was anything copied verbatim?

**No.** No file, no fragment of a file, no fixture and no test corpus. Every Java file here
was written for this port. Three probes (`probes/probe_01_is_sql_valid.py`,
`probes/probe_02_chroma_dupe.py`, `probes/probe_03_prompt_budget.py`) and one cross-check
(`probes/probe_06_prompt_budget_cross_check.py`) do copy short functions verbatim from
`legacy/base/base.py` — `is_sql_valid`, `add_ddl_to_prompt`, `str_to_approx_token_count` — but
that copy exists to run the source's own behaviour as evidence, in `vanna-port/`, and is not
part of the published `vanna-akka` code.

## Is behaviour derived even where no text was copied?

**Yes, and that is the whole point of it.** Three things in particular were established by
running `vanna-ai/vanna`'s own code (or the real dependency behind it) and are reproduced
here to answer the same way:

- **Adding identical training content twice is a silent no-op**, because the id is derived
  from the content's own hash. Checked by running a real `chromadb.EphemeralClient` in
  `probes/probe_02_chroma_dupe.py`, reproduced in `TrainingProfileState`'s dedup guard.
- **A prompt's token budget is checked per item as it is considered, not as a running total
  that stops the scan once exceeded** — an oversized item is skipped and a smaller item
  listed after it can still be included. Checked against the source's own function in
  `probes/probe_03_prompt_budget.py` and cross-run against the port's compiled class in
  `probes/probe_06_prompt_budget_cross_check.py` (9/9 agree). Reproduced in
  `PromptAssembly.appendWithBudget`.
- **`is_sql_valid` accepts a string if any one statement in it is a `SELECT`**, including one
  that also contains a `DROP`. Checked in `probes/probe_01_is_sql_valid.py`. **Not**
  reproduced — SPEC-001 OD-2 tightens this deliberately, and the difference is listed in
  `vanna-akka/README.md` under *Where it differs from vanna-ai/vanna*.

Everything else in this port is its own design, and the places it deliberately answers
differently from the source are listed in that same README section.

## What licence that forces on this project

MIT permits a derived work under other terms provided the notice above is kept. Nothing here
is a modified copy of an MIT file, so no file carries that licence forward file-by-file; the
attribution is kept anyway because retrieval and prompt-assembly behaviour is derived from
having run the source. `vanna-akka` ships under MIT itself — the simplest permissive licence,
and the one the source already uses.

## Also used

- Akka — `akka-javasdk` 3.6.3
- H2 Database Engine — embedded, in-memory SQL execution for the generate-validate-retry
  loop (Eclipse Public License / Mozilla Public License 2.0, dual-licensed; used as a
  runtime dependency, not modified or redistributed as source)
