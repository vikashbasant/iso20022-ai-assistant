# ISO 20022 AI Assistant

A Spring Boot + **Spring AI** service for SWIFT payment messages. It validates MT103s with
deterministic rules, converts them to ISO 20022 **pacs.008**, and uses an LLM with
**RAG (retrieval-augmented generation)** to explain messages and answer questions about
MT and ISO 20022 fields.

Built from my day-to-day work on SWIFT MT103 / ISO 20022 MX payment messaging in post-trade systems.

![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green) ![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-blue) ![Redis](https://img.shields.io/badge/Redis-cache-red) ![Kafka](https://img.shields.io/badge/Kafka-optional-black) ![Docker](https://img.shields.io/badge/Docker-compose-2496ED)

## What it does

| Endpoint | What happens | Uses the LLM? |
|---|---|---|
| `POST /api/mt103/validate` | Parses the MT103 and runs field-format and cross-field rules (mandatory tags, 32A date/amount format, BIC format, 71A/71F/71G charge rules, 35-char lines) | No |
| `POST /api/mt103/convert` | Maps the MT103 to a pacs.008.001.08 XML message, with notes on anything it could not map | No |
| `POST /api/mt103/explain` | Explains the payment in plain English and explains each validator finding and how to fix it | Yes |
| `POST /api/ask` | Answers questions about MT103 / ISO 20022 from the local knowledge notes (RAG) | Yes |

**Design choice:** rules decide, the LLM explains. Validation and conversion are deterministic Java
code with unit tests; the model only explains results and answers questions grounded in the
retrieved notes, so it can't invent a validation result.

## Architecture

```
            ┌──────────────── Spring Boot app ────────────────┐
 MT103 ───► │ Mt103Parser ─► Mt103Validator ─► ValidationIssue │──► JSON
            │      │                                           │
            │      └──────► Mt103ToPacs008Mapper ──────────────│──► pacs.008 XML
            │                                                  │
 Question ─►│ PaymentsAssistant (Spring AI ChatClient)         │──► Answer
            │   └ QuestionAnswerAdvisor ◄─ SimpleVectorStore   │
            │                ▲  (embeddings of knowledge/*.md) │
            │   Redis cache for repeated questions             │
            │   Optional Kafka listener: topic in ─► results   │
            └──────────────────────────────────────────────────┘
```

## Tech stack

Java 21 · Spring Boot 3.4 · Spring AI 1.0 (OpenAI-compatible chat + embeddings, `QuestionAnswerAdvisor`,
`SimpleVectorStore`) · Redis (Spring Cache) · Apache Kafka (optional streaming mode) · Docker / Docker Compose ·
JUnit 5 + AssertJ · GitHub Actions CI

## Run it

```bash
# 1. With Docker (app + Redis)
export OPENAI_API_KEY=sk-...
docker compose up --build

# 2. Or locally with Maven (in-memory cache)
OPENAI_API_KEY=sk-... mvn spring-boot:run
```

Without an API key the app still starts, and `/validate` and `/convert` work; only `/explain` and `/ask` need a model.

**Free local model with Ollama** (any OpenAI-compatible server works):

```bash
ollama pull llama3.1 && ollama pull nomic-embed-text
OPENAI_API_KEY=ollama OPENAI_BASE_URL=http://localhost:11434 \
CHAT_MODEL=llama3.1 EMBEDDING_MODEL=nomic-embed-text mvn spring-boot:run
```

**Kafka streaming mode:** `KAFKA_ENABLED=true docker compose --profile kafka up --build`, then produce raw
MT103s to `payments.mt103.in`; results are published as JSON to `payments.mt103.validated`.

## Try it

```bash
curl -s -X POST localhost:8080/api/mt103/validate -H 'Content-Type: text/plain' --data-binary @samples/mt103-invalid.txt
curl -s -X POST localhost:8080/api/mt103/convert  -H 'Content-Type: text/plain' --data-binary @samples/mt103-valid.txt
curl -s -X POST localhost:8080/api/mt103/explain  -H 'Content-Type: text/plain' --data-binary @samples/mt103-invalid.txt
curl -s -X POST localhost:8080/api/ask -H 'Content-Type: application/json' \
     -d '{"question":"Which pacs.008 element does MT103 field 71A map to, and what does SHA become?"}'
```

Example validator output for `samples/mt103-invalid.txt` (abridged):

```json
{ "valid": false,
  "issues": [
    { "tag": "20",  "severity": "ERROR", "message": "Sender's reference must not start or end with '/' or contain '//'" },
    { "tag": "32A", "severity": "ERROR", "message": "Value date '261340' is not a valid YYMMDD date" },
    { "tag": "71F", "severity": "ERROR", "message": "When 71A is BEN, at least one 71F (sender's charges) is required" },
    { "tag": "57A", "severity": "ERROR", "message": "'CITI33' is not a valid BIC (8 or 11 characters)" } ] }
```

## Tests

```bash
mvn test
```

Unit tests cover the parser, every validation rule group, and the pacs.008 mapping (checked with XPath).

## Scope and limits

- Validation covers the common reject causes, not the full SWIFT network validation rule set.
- The pacs.008 mapping covers the core fields; it is not a certified CBPR+ translator (no structured
  address translation, block 3 UETR not read yet).
- The knowledge base is a small set of hand-written notes in `src/main/resources/knowledge/` — add more
  `.md` files to extend what `/ask` can answer.

## Roadmap

- Read block 3 field 121 (UETR) and carry it into `PmtId/UETR`
- pacs.008 → MT103 reverse mapping
- XSD validation of the generated XML
- Swap `SimpleVectorStore` for PGVector

## License

MIT
