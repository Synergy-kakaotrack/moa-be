# Copilot Code Review Instructions

You are reviewing a Spring Boot backend project.
Focus on **correctness, consistency, and production safety** rather than style nitpicks.

---

## 1. Transaction & Data Integrity

- Check that write operations are wrapped in appropriate `@Transactional` boundaries.
- Verify that multi-step operations (e.g. draft → commit → scrap save) are **atomic**.
- Point out cases where partial failure could leave inconsistent data.
- Prefer rollback-safe designs over best-effort logic.

---

## 2. Error Handling & API Responses

- Ensure exceptions are mapped to consistent API error responses.
- Avoid leaking internal exception messages to clients.
- Verify that business exceptions and system exceptions are clearly separated.

---

## 3. Logging Rules

- Check that important state transitions are logged (commit, rollback, delete).
- Avoid excessive debug logs in production paths.
- Ensure logs include enough context (userId, projectId, scrapId if applicable).

---

## 4. Architecture & Boundaries

- Watch for leakage between layers (controller ↔ repository).
- Prefer service-level orchestration over controller-heavy logic.
- Flag unclear responsibilities or overly complex methods.

---

## 5. What NOT to focus on

- Do not comment on formatting or naming unless it affects readability.
- Do not suggest speculative refactors unrelated to the change.
