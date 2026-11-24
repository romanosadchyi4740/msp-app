---
name: split-service
description: Split an oversized Java service (too many dependencies, several jobs) into focused classes by responsibility without changing behaviour, moving method bodies with a script and splitting its tests to match. Use when the owner says a service or class has too many dependencies or responsibilities, or asks to refactor, decouple or split it (as done for TelemetryService, TrackService, PlaylistService and ChatService).
---

# Splitting a service by responsibility

Goal: each new class has one job and few dependencies (aim for at most 4-5), endpoints and responses unchanged,
every existing test kept, and the untested logic now tested. Combine with `kan-ticket` for the PR (often the owner
asks for it inside the current PR; then commit it separately with its own message and update the PR body).

## 1. Inventory
List each public method with the dependencies it uses and the exception types and messages it throws. Group methods
by the job they serve (e.g. upload / catalogue reads / likes / recommendations). Look for:
- repeated snippets (find-or-404, ownership checks, builders) -> a private helper, or a small helper class next to
  the services when several new classes need it (`ChatMemberships`). **Never put logic on an entity.**
- a single use case that still needs many dependencies -> extract the input checks into a `...Validator` or the
  lookup of the target into a `...Resolver` (`TrackUploadValidator`, `MessageChatResolver`, `DirectChatResolver`);
- side channels (STOMP delivery, files) -> a small component (`ChatMessageNotifier`).

Show the owner a table before writing code:

| Class | Job | Dependencies |
|---|---|---|

Name classes after the job (`TrackLikeService`, `GroupChatService`); keep the old name for the core job if one
remains, so the most-used class does not change name.

## 2. Move the code, do not retype it
Write a spec and run the splitter from the repo root. It copies whole methods (with annotations and Javadoc) into the
new classes; members not moved and not listed in `drop` are printed so nothing is lost:
```
python .claude/scripts/java/split_class.py spec.json
python .claude/scripts/java/prune_imports.py <new and changed files>
```
Headers follow the house style: Javadoc saying what the class is for, `@Service @RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)`, fields without modifiers. A target may be the source
file itself (listed last). Members you rewrite by hand go in `drop`, and you write them in the new class yourself.
Put the spec in the scratchpad.

Then rewire the callers (controllers) to the class that owns each method, delete the old class if empty, and compile.

## 3. Behaviour
Keep exception types, messages and status codes. Allowed simplifications are those with identical results (merging
duplicated branches, streams instead of loops, `HexFormat` for the same hex string). Anything that changes order,
timing, number of queries or error paths goes in the PR body under "Behaviour", stated exactly. Pre-existing bugs
(e.g. plain `RuntimeException` -> 500) are not fixed silently: list them, or fix them only if the owner agrees.

## 4. Tests
1. Split the old test class with the same script (members are test methods). Use one spec per test class; give each
   target the mocks its class needs, or a `@BeforeEach` that builds the class with real collaborators made from mocks
   (e.g. a real validator or resolver inside the service) so the moved tests keep testing the same flow.
2. Remove unused mocks and static imports; every original test must still exist and pass.
3. Add tests for logic that had none, especially the extracted validators and resolvers (each rejection path), and
   any branch the refactoring touched.
4. `./mvnw test`, then `python .claude/scripts/java/java_style.py <changed files>`.

## 5. PR
Add a section with the table of classes (job, dependencies), what was moved unchanged versus rewritten, the
behaviour notes, and the test counts (kept, new, total from `./mvnw test`).
