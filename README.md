# vanna-akka

Turn a question into SQL, using training material it has been shown, and try again — using
the error — when the answer it gets is wrong or does not run.

A port of [vanna-ai/vanna](https://github.com/vanna-ai/vanna) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

`vanna-ai/vanna` keeps a store of table definitions, notes and past question/answer pairs,
pulls out what is relevant to a new question, and asks a language model for the SQL that
answers it. It was rebuilt here to find out how precisely a system built around an
unreliable answer — the model's — has to be written down before the parts around that
answer can be rebuilt on a different stack.

Only the training store and the generate-validate-retry loop were rebuilt. Which language
model answers, and how the network call to it is made, is the target SDK's job, not this
port's.

Those written specifications live in a separate repository, `akka-specify-harness`, under
`vanna-port/`. It is private for now.

---

## vanna-ai/vanna → this port

📉 411 Python lines → **788 Java lines**<br>
📁 3 files → **17 files**<br>
⚡ 950 → **3,480** nanoseconds, assembling one prompt's table and documentation budget<br>
🎯 9 answers compared → **9 of 9 agree**<br>
🛡️ 1 way to smuggle a `DROP` past the validity check → **0**<br>
🧪 0 rules broken on purpose to check a test notices → **5**

The 950 and the 3,480 are one pure function timed through an interpreter shell, not either
system's own request path — and the port is larger than the slice it rebuilds, not smaller,
because it adds a bound on retrying, a real SQL execution engine, and an HTTP surface the
source has no equivalent of. Read [`bench/REPORT.md`](bench/REPORT.md) before quoting any
of these numbers elsewhere.

Full method, and the numbers that did not make this list: [`bench/REPORT.md`](bench/REPORT.md).

---

## What it took to build

⏱️ **0.9 hours** from the first command to the published repository, **0.9** of them active<br>
💬 **509** exchanges with the model<br>
✍️ **345,005** tokens written by the model, **145,017,838** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **38** tests

```bash
python toolkit/tokens.py --port vanna
```

The record of every question, and where the time went, lives with the specifications.

---

## What it does

- **Training material — a table definition, a note, or a question and its answer — is kept
  once.** Adding the same one again is recognised and does nothing, because what identifies
  it is a hash of its own content rather than a name somebody chose.
- **A new question is answered using only the training material relevant to it.** Three
  kinds are pulled separately — similar past questions, related table definitions, related
  notes — each ranked against the question and capped at ten.
- **Only a read-only answer is allowed to run.** An answer that would change anything, even
  partly, is refused before it reaches a database — whether it is one statement or several.
- **A wrong or non-running answer is retried automatically, using the error, up to three
  times.** Nobody has to notice the failure and ask again by hand.
- **What was tried, and why it stopped, is kept.** Every attempt, and the reason the last
  one failed if none succeeded, can be read back at any time while it runs and after.

---

## Design decisions

**Content-hashed ids.** Training material is identified by a hash of its own text rather
than a name a caller picks. Adding the same fact twice happens by accident more often than
on purpose, and a hash makes the second attempt harmless instead of a duplicate to clean up
later.

**A bounded retry, not an open-ended one.** A wrong answer is retried with the error folded
into the next attempt, and this stops after three tries rather than continuing forever. A
program cannot ask a person whether it is worth trying again, so it needs a number where a
person would have made a judgement call.

**Every statement must be safe, not just one of them.** An answer is allowed to run only if
none of its statements could change anything — not merely if it contains at least one that
does not. An answer with two statements, one harmless and one not, is exactly the case a
weaker check would miss.

**The retry remembers what it already tried.** Each attempt at one question happens in one
running conversation with the model, so telling it about a new error is enough — it already
has the question and what it tried before, without anyone having to repeat those.

**A fresh, disposable database for every check.** An answer is tested against a real,
small database built from the training material's own table definitions and thrown away
afterwards, rather than a live connection somebody has to keep running. A real syntax
error is a real syntax error either way, and nothing has to be provisioned to find one.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/vanna-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9012.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once
- A key for a model provider — see below

### Build and test

```bash
mvn verify
```

`mvn test` runs the 35 tests that need no runtime; `mvn verify` adds the 3 that start one.

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9012**.

### Use it

Add a table definition, then ask a question:

```bash
curl -X POST http://localhost:9012/training/demo/ddl \
  -H 'Content-Type: application/json' \
  -d '{"ddl": "CREATE TABLE customers (id INT, name VARCHAR(50), region VARCHAR(20))"}'

curl -X POST http://localhost:9012/sql-assist/demo/ask \
  -H 'Content-Type: application/json' \
  -d '{"question": "list all customers"}'
```

The answer carries an attempt id. Poll it to see the SQL, whether it succeeded, and how
many tries it took:

```bash
curl http://localhost:9012/sql-assist/attempts/<attemptId>
```

Add a question and its answer, or a note, and remove any item by the id it was given:

```bash
curl -X POST http://localhost:9012/training/demo/question-sql \
  -H 'Content-Type: application/json' \
  -d '{"question": "how many customers?", "sql": "SELECT COUNT(*) FROM customers"}'

curl -X POST http://localhost:9012/training/demo/documentation \
  -H 'Content-Type: application/json' \
  -d '{"text": "customers.region is a two-letter US state code"}'

curl -X DELETE http://localhost:9012/training/demo/items/<id>

curl http://localhost:9012/training/demo
```

---

## Model providers

**Every port supports every provider the SDK offers.** Not the one that was convenient
while building it — a port wired to a single vendor is a port nobody else can run.
`MODEL_PROVIDER` selects; each provider gets its own section in `application.conf`.

```bash
export MODEL_PROVIDER=anthropic
export ANTHROPIC_API_KEY=...
```

| `MODEL_PROVIDER` | Variables to set | Default model |
|---|---|---|
| `openai` | `OPENAI_API_KEY` | gpt-4o-mini |
| `anthropic` | `ANTHROPIC_API_KEY` | claude-opus-4-6 |
| `googleai-gemini` | `GOOGLE_AI_GEMINI_API_KEY` | gemini-2.5-flash |
| `mistral-ai` | `MISTRAL_AI_API_KEY`, `MODEL_NAME` | none — set `MODEL_NAME` |
| `vertex-ai` | `VERTEX_AI_API_KEY`, `VERTEX_AI_PROJECT_ID`, `VERTEX_AI_LOCATION`, `MODEL_NAME` | none — set `MODEL_NAME` |
| `azure-openai` | `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_DEPLOYMENT` | set by deployment |
| `bedrock` | `AWS_REGION`, `BEDROCK_MODEL_ID`, plus your usual AWS credentials | set by `BEDROCK_MODEL_ID` |
| `hugging-face` | `HUGGING_FACE_ACCESS_TOKEN`, `HUGGING_FACE_MODEL_ID` | set by `HUGGING_FACE_MODEL_ID` |
| `ollama` | `MODEL_NAME`, optionally `OLLAMA_BASE_URL` | no key needed |
| `local-ai` | `MODEL_NAME`, optionally `LOCAL_AI_BASE_URL` | no key needed |

Tests never call a real model. `SqlGenerationWorkflowTest` and
`SqlAssistEndpointIntegrationTest` wire the SDK's own `TestModelProvider` in place of the
selected provider, so `mvn verify` runs with no key at all — only `akka local run` needs one.

---

## Configuration

Everything that is not a model provider.

| Variable | Default | Notes |
|---|---|---|
| none | — | Nothing about this service is set from the outside. The retry bound (3 attempts) and the retrieval limit (10 items per kind) are in the code, because changing either changes what the service promises. |

---

## Where it differs from vanna-ai/vanna

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **What gates whether an answer is allowed to run.** `vanna-ai/vanna`'s `is_sql_valid`
  accepts a string if *any* statement inside it parses as a `SELECT`, so a string carrying a
  harmless `SELECT` and a destructive `DROP` together passes. This port requires *every*
  statement to be read-only. A check meant to keep an unreviewed answer from changing
  anything that lets a mixed statement through protects against nothing; tightening it does
  not touch training-store retrieval or the loop's shape, which is what this port rebuilds.
- **Whether a wrong answer is retried automatically.** `vanna-ai/vanna` tries once inside
  `ask()`; the retry that exists, `/fix_sql`, is a step a person has to trigger by hand, as
  many or as few times as they choose, with no limit. This port makes the retry automatic
  and stops it at three attempts, because a program cannot ask a person whether trying again
  is worth it — some bound has to be chosen, and three is small enough to fail fast, large
  enough that one bad attempt is not the end of it.
- **How training material is ranked against a question.** `vanna-ai/vanna`'s default is
  cosine distance over embeddings from a downloaded model. This port ranks by how much a
  question and an item's text overlap as sets of words — a plain, offline comparison with
  no model call and nothing to download, so the port's own tests do not depend on either.
- **Where a retrieved question-and-answer example is written down.** `vanna-ai/vanna` gives
  the model each one as its own turn in the conversation. This port writes them into the
  same instructions the table definitions and notes go into, because the target's model
  component takes one instruction and one question per call rather than an arbitrary list
  of past turns supplied by the caller.
- **What runs the SQL, and against what.** `vanna-ai/vanna` runs an answer against a
  database connection a caller already set up and manages. This port builds a fresh,
  disposable database from the training profile's own table definitions for every attempt,
  because the port is meant to run and be tested with nothing external provisioned; a real
  deployment pointed at a real warehouse is a different, larger piece of work this port does
  not attempt.
- **Whether adding the same training material twice is possible.** `vanna-ai/vanna`'s
  default store (ChromaDB) happens to make this a no-op, because the id it assigns is
  already a hash of the content — checked by running it, not assumed. This port relies on
  the same content-hash approach on purpose, so the behaviour is not an accident of one
  backend's id scheme but the rule the training store is built around.
- **Everything downstream of a successful answer** — a chart, a follow-up question, a
  summary of the result. `vanna-ai/vanna` can generate all three from a successful query.
  `not attempted` — this port's slice ends once an answer runs.
- **Every non-ChromaDB training store and every LLM integration** `vanna-ai/vanna` ships.
  `not attempted` — the evidence behind this port's training-store behaviour was read from
  the one reference implementation, `ChromaDB_VectorStore`, not from the thirty-plus others.

---

## Licence

`vanna-ai/vanna` is MIT, © 2024 Vanna.AI. This port reimplements the behaviour without
copied source; see `ACKNOWLEDGEMENTS.md`.
