---
name: modulith-extract
description: Extract a Spring Modulith module (users, storage, music, playlists, chats, telemetry, search, or a new SyncChord module) from the legacy layered packages of the msp-app backend into <module>.internal and <module>.external, with UUID references, cleanup hooks, batched lookups and ArchitectureTest rules. Use for the KAN-64 epic tickets (KAN-69, KAN-70, KAN-71) or when asked to create, extract or restructure a backend module.
---

# Extracting a Spring Modulith module

Read CLAUDE.md section 4 first; it is the rulebook. This skill is the procedure that worked for `users` (KAN-66),
`storage` (KAN-67) and `music` (KAN-68). Combine it with the `kan-ticket` skill for branch, PR and Jira.

Base package: `com.romanosadchyi.smp.music_app`. Legacy packages: `config`, `exception`, `model.*`, `repository`,
`rest`, `service`, `service.mapping`.

## 1. Inventory (no edits yet)
1. List the classes that belong to the module: entities, enums, repositories, services, mappers, controllers, DTOs,
   exceptions, seeders, and their tests.
2. Dependencies **out of** the module: for each class, grep its imports for legacy packages. Each one is either
   moved together, replaced by another module's `external` interface, or inverted (step 4).
3. Dependencies **into** the module: grep the rest of the code for the module's types. Decide what goes into
   `external`: only what other code actually needs.
4. JPA associations crossing the boundary (`@ManyToOne`, `@ManyToMany`, `@OneToMany` to another module's entity).
   Check the Flyway migrations for the foreign keys and their `ON DELETE` rules; they decide what deletion must do.
5. Write the plan for the owner: target packages, the `external` API, cross-module links that stay for now and the
   ticket that removes them, and anything that changes behaviour.

## 2. Packages
```
<module>/
  external/   package-info.java with @NamedInterface("external"); interfaces + records/enums/exceptions in their signatures
  internal/   entity, repository, service, controller, dto, mapping, config (seeders), exception
```
- Implementations of `external` interfaces live in `internal.service` and are package-private where possible
  (e.g. `class MusicLookupService implements TrackLookup, AlbumLookup`).
- Response records embedded in other modules' responses (`TrackResponseDto`, `UserResponseDto`) and exceptions that
  other modules throw (`TrackNotFoundException`) go into `external`.
- An enum used in an external record (e.g. `UserRole` in `UserSummary`) goes into `external`.

## 3. Move the classes
Write the moves as JSON and run the script from the repo root; it does `git mv`, fixes package lines, imports,
same-package uses and wildcard imports across main and test sources:
```
python .claude/scripts/java/move_classes.py backend moves.json   # [[old FQN, new FQN, "main"|"test"], ...]
cd backend && ./mvnw -q clean test-compile                        # clean: stale classes hide errors
python .claude/scripts/java/prune_imports.py <changed files>
```
Tests move to mirror the main packages. Put the JSON in the scratchpad, not the repo.

## 4. Replace cross-module links
- **References become UUIDs on the same column**: `@ManyToOne User author` -> `@Column(name = "author_id") UUID authorId`.
  Builders `.author(x)` -> `.authorId(id)`, `getAuthor().getId()` -> `getAuthorId()`, JPQL `m.user.id` -> `m.userId`.
  Derived queries like `findAllByAuthorId` keep working. No Flyway migration.
- **A join table between two modules** is owned by one module, mapped as `@ElementCollection` of UUIDs on the owner
  (e.g. `Track.likedByUserIds` over `user_liked_tracks`). Lists with `@OrderColumn` must be changed through the
  entity, never with a native `DELETE`, or the order column gets a gap that loads as `null`.
- **Reads of another module's data** go through its `external` lookup (`UserLookup`, `TrackLookup`, `AlbumLookup`).
  Lists and pages resolve users in **one batched call** (`findSummaries`) via the mappers' `mapAll`/`mapPage`,
  never one lookup per row.
- **Flows that point the wrong way are inverted with an interface** in the owning module's `external`, implemented by
  the modules that hold the data, and called as `List<...>` of beans:
  - `users.external.UserDataCleanup`: account deletion; every module with user data implements it.
  - `music.external.TrackDeletionCleanup`: deleting a track; modules referencing tracks drop their references.
  - Until a module is extracted, a `service.Legacy...` class implements the hook for it, with Javadoc naming the
    ticket that moves it.
- An endpoint whose URL belongs to one module but whose data belongs to another moves to the data's module with the
  same URL (e.g. `GET /api/users/{id}/tracks` in `music.internal.controller.UserTracksController`).
- Entities stay data only: shared logic goes into a service or a helper class next to the services.

## 5. Guard the boundary in ArchitectureTest
`verifyModularity()` stays `@Disabled` until KAN-71. Until then, add explicit ArchUnit rules:
- strict modules: add the module to `moduleInternalIsUsedOnlyByItsModule` (`@ValueSource`);
- if legacy packages must keep using `internal` for now, write a separate rule that forbids it only for the extracted
  modules, and say in its Javadoc which ticket removes the exemption (see `musicInternalIsNotUsedByOtherModules`);
- the module itself depends only on `shared` and other modules' `external`
  (see `musicDependsOnlyOnModules`), so later extractions cannot create a cycle.
- specific bans when useful, e.g. `onlyStorageUsesTheMinioClient`.

## 6. Tests
- Fix the moved tests: mocks of other modules' repositories become mocks of their lookups; stubs of `map(x)` in
  list paths become `mapAll(...)` / `mapPage(...)`.
- Unit tests for every new class: the lookup implementation, cleanup hooks, removers, mappers' batching
  (assert one `findSummaries` call and no `getSummary`).
- An `@IntegrationTest` `<Module>ModuleIntegrationTest` against Testcontainers Postgres for the flows that cross
  modules, especially deletion with references from other users' data. Register users through `/auth/register`, set
  up data with repositories, assert through the REST API and SQL (`JdbcTemplate`).
- `./mvnw test` green, then `java_style.py` on the changed files.

## 7. Documentation and PR
- CLAUDE.md: section 3a (where things live now), section 4 module rules if a new pattern was introduced, the
  seeders line.
- PR body: the module's packages, the `external` API, cross-module links that remain and their tickets, bugs fixed
  on the way (with how they were found and whether a test proves them), the new ArchitectureTest rules.
