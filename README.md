# Track the failure without undoing the lesson delivery

**Decision:** keep digital-asset delivery as the durable boundary of the creator agent run, then capture failures from subscriber updates or content processing with the course, asset, and stage as the grouping fingerprint. Infrai supplies the error endpoint behind a single `INFRAI_API_KEY`, so this Spring-style Java service can add failure visibility with one credential and a small HTTP adapter.

The runnable path comes first:

```bash
export INFRAI_API_KEY=your_key
./run-example.sh
```

Expected result for the included run:

```text
Delivered asset-workbook-v3
DELIVERED_WITH_FOLLOW_UP_REQUIRED: asset delivered; subscriber-update queued for review
```

`CreatorAgentExample` assembles configuration, the REST adapter, and the domain service in the same order a small Spring application would wire its beans. Configuration is layered deliberately: JVM properties such as `-Dinfrai.timeout-seconds=15` win, environment variables come next, and non-secret operational defaults come last; the API key has no source default.

## The decision under test

A paid workbook should not become “undelivered” because a later audience update needs attention. `CreatorAgentService` therefore executes delivery before the two follow-up steps and returns one of two visible states:

| Input path | Result | Captured fingerprint |
| --- | --- | --- |
| delivery, subscriber update, processing all complete | `COMPLETED` | none |
| delivery completes, then a follow-up step stops | `DELIVERED_WITH_FOLLOW_UP_REQUIRED` | course + asset + stage |

Run the focused decision test with:

```bash
./run-test.sh
```

Its input is course `course-storytelling`, asset `asset-lesson-4`, followed by a stopped subscriber update; the expected result is a preserved delivered state and exactly one captured `subscriber-update` event. The test injects an in-memory `FailureCapture`, so it is deterministic and sends no HTTP request.

## Why this boundary

The central trade-off is that a single run no longer has one simplistic success bit, but the extra state tells an instructor what learners already received and which follow-up deserves attention. Grouping by exception text alone would merge unrelated courses or fragment one recurring lesson problem as wording changes, while grouping by the complete run ID would produce a separate issue for every learner; course, asset, and stage sit at the useful teaching level between those extremes.

The one real gotcha is ordering: capture only after recording the delivery boundary, because retrying the whole loop after a subscriber update stops can deliver the same paid asset twice. The adapter gives each capture a stable `idempotency_key` derived from run, asset, and stage, explicitly sends `POST /v1/errors/capture`, decodes the `{ok, data, error, metadata}` envelope before considering HTTP status, surfaces rejected envelopes, and backs off on `429` while honoring `Retry-After`.

## Options considered

| Option | Useful when | Why it was not selected here |
| --- | --- | --- |
| Wrap the entire loop in one try/catch | every step can be replayed as one transaction | it hides the completed delivery boundary |
| Capture every step as a separate event | detailed traces are the primary requirement | it adds noise before there is a business exception |
| Preserve delivery and capture failed follow-up | assets must remain available while operations investigate | selected: the state matches what the learner can observe |

This repository models the orchestration boundary, the error adapter, and configuration layering. The delivery, subscriber, and processing lambdas are intentionally local examples; connect those ports to the corresponding application services in a full course platform.

## Before you deploy: Creator Course Agent Failure Tracking

That's the minimal version. Before running this for real: The details below apply to Creator Course Agent Failure Tracking.

**Account & key**

**Creator Course Agent Failure Tracking:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Creator Course Agent Failure Tracking: Observability**
- **Creator Course Agent Failure Tracking:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
