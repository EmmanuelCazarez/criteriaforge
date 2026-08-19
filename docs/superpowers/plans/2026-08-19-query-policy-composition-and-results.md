# CriteriaForge 0.2 Query Policy, Composition, and Results Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add typed result mapping, immutable mandatory-filter composition, operation-specific field permissions, projection/sort complexity limits, and deterministic policy-controlled tie-breaking.

**Architecture:** Extend the immutable core API without replacing existing 0.1 entry points. Keep caller-controlled validation in `criteriaforge-core`, enforce operation permissions before SQL in `criteriaforge-jpa`, and build one effective JPA ordering that always ends with either the configured policy tie-breaker or the entity primary key.

**Tech Stack:** Java 17, Maven Wrapper, JUnit Jupiter, AssertJ, Jakarta Persistence 3, Hibernate, Spring Boot 3.5 and 4.1, H2, PostgreSQL Testcontainers.

**Spec:** `docs/superpowers/specs/2026-08-19-query-policy-composition-and-results-design.md`

## Global Constraints

- Keep Java 17 as the minimum runtime and compilation baseline.
- Keep Spring Boot 3.5 as dependency management and verify Spring Boot 4.1 compatibility.
- Preserve `QueryEngine.execute(Class<?>, QueryRequest)` and existing 0.1 consumer source compatibility.
- Deprecate unified field-permission APIs; do not remove them in 0.2.0.
- Default every JPA query to an ascending primary-key tie-breaker.
- Do not count a policy tie-breaker toward `maxSortFields`.
- Keep authorization decisions and Operations API envelopes outside CriteriaForge.
- Do not add cursor pagination or page-number semantics.
- Do not perform release versioning, tagging, publication, or Maven Central work in this implementation.

---

### Task 1: Add result mapping and typed entity execution

**Files:**
- Modify: `criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryResult.java`
- Modify: `criteriaforge-core/src/test/java/io/github/emmanuelcazarez/criteriaforge/core/QueryRequestTest.java`
- Modify: `criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/QueryEngine.java`
- Modify: `criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryEngine.java`
- Modify: `criteriaforge-jpa/src/test/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryEngineTest.java`

**Interfaces:**
- Consumes: existing immutable `QueryResult<T>` and `QueryEngine.execute(Class<?>, QueryRequest)`.
- Produces: `QueryResult.map`, `hasNext`, `hasPrevious`, and `QueryEngine.executeEntities`.

- [ ] **Step 1: Write failing core result tests**

Add focused tests to `QueryRequestTest`:

```java
@Test
void mapsContentWithoutChangingPaginationMetadata() {
    var result = new QueryResult<>(List.of(2, 4), 7, 2, 2);

    var mapped = result.map(value -> "item-" + value);

    assertThat(mapped.content()).containsExactly("item-2", "item-4");
    assertThat(mapped.total()).isEqualTo(7);
    assertThat(mapped.offset()).isEqualTo(2);
    assertThat(mapped.limit()).isEqualTo(2);
}

@Test
void derivesOffsetNavigationWithoutPageNumbers() {
    assertThat(new QueryResult<>(List.of("a", "b"), 5, 0, 2).hasNext()).isTrue();
    assertThat(new QueryResult<>(List.of("c"), 3, 2, 2).hasNext()).isFalse();
    assertThat(new QueryResult<>(List.of("c"), 3, 2, 2).hasPrevious()).isTrue();
    assertThat(new QueryResult<>(List.of(), 0, 10, 2).hasPrevious()).isFalse();
}

@Test
void rejectsANullResultMapper() {
    var result = new QueryResult<>(List.of("a"), 1, 0, 1);

    assertThatThrownBy(() -> result.map(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("mapper must not be null");
}
```

- [ ] **Step 2: Run the core tests and verify RED**

Run:

```bash
./mvnw -B -ntp -pl criteriaforge-core -Dtest=QueryRequestTest test
```

Expected: compilation fails because `map`, `hasNext`, and `hasPrevious` do not exist.

- [ ] **Step 3: Implement the immutable result operations**

Add to `QueryResult`:

```java
public <R> QueryResult<R> map(Function<? super T, ? extends R> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new QueryResult<>(content.stream().map(mapper).toList(), total, offset, limit);
}

public boolean hasNext() {
    return (long) offset + content.size() < total;
}

public boolean hasPrevious() {
    return offset > 0 && total > 0;
}
```

Import `java.util.function.Function`.

- [ ] **Step 4: Run the core tests and verify GREEN**

Run the command from Step 2. Expected: all `QueryRequestTest` tests pass.

- [ ] **Step 5: Write failing typed execution tests**

Add to `JpaQueryEngineTest`:

```java
@Test
void executesEntitiesWithAStaticallyTypedResult() {
    var result = executor.executeEntities(
        OrderEntity.class,
        QueryRequest.builder().limit(10).build());

    QueryResult<String> references = result.map(OrderEntity::getReference);

    assertThat(references.content())
        .containsExactly("FIRST", "SECOND", "THIRD", "FOURTH");
}

@Test
void typedEntityExecutionRejectsProjectionRequests() {
    var query = QueryRequest.builder().select("reference").limit(10).build();

    assertThatThrownBy(() -> executor.executeEntities(OrderEntity.class, query))
        .isInstanceOfSatisfying(QueryValidationException.class, error ->
            assertThat(error.code()).isEqualTo(QueryErrorCode.UNSUPPORTED_PROJECTION));
}
```

- [ ] **Step 6: Run the JPA tests and verify RED**

Run:

```bash
./mvnw -B -ntp -pl criteriaforge-jpa -am -Dtest=JpaQueryEngineTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `executeEntities` does not exist.

- [ ] **Step 7: Add the compatible typed execution entry point**

Add this default method to `QueryEngine`:

```java
default <T> QueryResult<T> executeEntities(Class<T> entityType, QueryRequest query) {
    Objects.requireNonNull(entityType, "entityType must not be null");
    Objects.requireNonNull(query, "query must not be null");
    if (!query.fields().isEmpty()) {
        throw new QueryValidationException(
            QueryErrorCode.UNSUPPORTED_PROJECTION,
            "Typed entity execution does not accept projection fields");
    }
    return execute(entityType, query).map(entityType::cast);
}
```

Add the required `Objects`, `QueryErrorCode`, and `QueryValidationException`
imports. Override it in `JpaQueryEngine`:

```java
@Override
public <T> QueryResult<T> executeEntities(Class<T> entityType, QueryRequest query) {
    return findAll(entityType, query);
}
```

- [ ] **Step 8: Run the JPA tests and verify GREEN**

Run the command from Step 6. Expected: all `JpaQueryEngineTest` tests pass.

- [ ] **Step 9: Commit the result API task**

```bash
git add criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryResult.java criteriaforge-core/src/test/java/io/github/emmanuelcazarez/criteriaforge/core/QueryRequestTest.java criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/QueryEngine.java criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryEngine.java criteriaforge-jpa/src/test/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryEngineTest.java
git commit -m "feat: add typed query result mapping"
```

### Task 2: Add immutable mandatory-filter composition

**Files:**
- Modify: `criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryRequest.java`
- Modify: `criteriaforge-core/src/test/java/io/github/emmanuelcazarez/criteriaforge/core/QueryRequestTest.java`

**Interfaces:**
- Consumes: `FilterExpression.and` and the immutable `QueryRequest` canonical constructor.
- Produces: `QueryRequest.andWhere(FilterExpression)`.

- [ ] **Step 1: Write failing composition tests**

Add:

```java
@Test
void addsARequiredFilterWithoutChangingTheIncomingRequest() {
    var incoming = QueryRequest.builder()
        .select("reference")
        .where(Filters.field("status").eq("PAID"))
        .orderByDescending("createdAt")
        .offset(20)
        .limit(10)
        .build();
    var required = Filters.field("organizationId").eq(42L);

    var scoped = incoming.andWhere(required);

    assertThat(scoped.fields()).isEqualTo(incoming.fields());
    assertThat(scoped.sorting()).isEqualTo(incoming.sorting());
    assertThat(scoped.pagination()).isEqualTo(incoming.pagination());
    assertThat(scoped.filter()).contains(required.and(incoming.filter().orElseThrow()));
    assertThat(incoming.filter()).contains(Filters.field("status").eq("PAID"));
}

@Test
void usesARequiredFilterAsTheOnlyFilterWhenTheRequestHasNone() {
    var incoming = QueryRequest.builder().limit(10).build();
    var required = Filters.field("organizationId").eq(42L);

    assertThat(incoming.andWhere(required).filter()).contains(required);
}

@Test
void rejectsANullRequiredFilter() {
    var incoming = QueryRequest.builder().build();

    assertThatThrownBy(() -> incoming.andWhere(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("requiredFilter must not be null");
}
```

- [ ] **Step 2: Run the test and verify RED**

```bash
./mvnw -B -ntp -pl criteriaforge-core -Dtest=QueryRequestTest test
```

Expected: compilation fails because `andWhere` does not exist.

- [ ] **Step 3: Implement immutable conjunction**

Add to `QueryRequest`:

```java
public QueryRequest andWhere(FilterExpression requiredFilter) {
    Objects.requireNonNull(requiredFilter, "requiredFilter must not be null");
    var combined = filter
        .map(requiredFilter::and)
        .orElse(requiredFilter);
    return new QueryRequest(fields, Optional.of(combined), sorting, pagination);
}
```

- [ ] **Step 4: Run the test and verify GREEN**

Run the command from Step 2. Expected: all tests pass.

- [ ] **Step 5: Commit the composition task**

```bash
git add criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryRequest.java criteriaforge-core/src/test/java/io/github/emmanuelcazarez/criteriaforge/core/QueryRequestTest.java
git commit -m "feat: compose required query filters"
```

### Task 3: Separate policy permissions by query operation

**Files:**
- Modify: `criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicy.java`
- Modify: `criteriaforge-core/src/test/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicyTest.java`

**Interfaces:**
- Consumes: existing field path validation, aliases, global denials, and operator restrictions.
- Produces: operation allowlists, operation checks, and deprecated unified compatibility APIs.

- [ ] **Step 1: Write failing operation-permission tests**

Add tests that establish independent behavior:

```java
@Test
void configuresIndependentProjectionFilterAndSortPermissions() {
    var policy = QueryPolicy.builder()
        .allowProjectionFields("id", "displayName")
        .allowFilterFields("status")
        .allowSortFields("createdAt")
        .denyFields("id")
        .build();

    assertThat(policy.isProjectionAllowed("displayName")).isTrue();
    assertThat(policy.isProjectionAllowed("status")).isFalse();
    assertThat(policy.isFilterAllowed("status")).isTrue();
    assertThat(policy.isFilterAllowed("displayName")).isFalse();
    assertThat(policy.isSortAllowed("createdAt")).isTrue();
    assertThat(policy.isSortAllowed("displayName")).isFalse();
    assertThat(policy.isProjectionAllowed("id")).isFalse();
}

@Test
void anExplicitEmptyOperationAllowlistDeniesThatOperation() {
    var policy = QueryPolicy.builder()
        .allowFilterFields(List.of())
        .build();

    assertThat(policy.isFilterAllowed("status")).isFalse();
    assertThat(policy.isProjectionAllowed("status")).isTrue();
    assertThat(policy.isSortAllowed("status")).isTrue();
}

@Test
void aliasesDoNotGrantOperationPermissions() {
    var policy = QueryPolicy.builder()
        .alias("amount", "total")
        .allowProjectionFields("amount")
        .allowFilterFields("status")
        .allowSortFields("createdAt")
        .build();

    assertThat(policy.isProjectionAllowed("amount")).isTrue();
    assertThat(policy.isFilterAllowed("amount")).isFalse();
    assertThat(policy.isSortAllowed("amount")).isFalse();
    assertThat(policy.resolveField("amount")).isEqualTo("total");
}
```

- [ ] **Step 2: Write failing legacy-compatibility tests**

Add:

```java
@Test
void legacyAllowFieldsAppliesToEveryOperationAndIncludesAliases() {
    var policy = QueryPolicy.builder()
        .allowFields("id")
        .alias("amount", "total")
        .build();

    assertThat(policy.isProjectionAllowed("id")).isTrue();
    assertThat(policy.isFilterAllowed("id")).isTrue();
    assertThat(policy.isSortAllowed("id")).isTrue();
    assertThat(policy.isProjectionAllowed("amount")).isTrue();
    assertThat(policy.allowedFields()).containsExactlyInAnyOrder("id", "amount");
}

@Test
void rejectsMixingLegacyAndOperationSpecificPermissions() {
    assertThatThrownBy(() -> QueryPolicy.builder()
        .allowFields("id")
        .allowProjectionFields("id"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unified and operation-specific field permissions must not be mixed");

    assertThatThrownBy(() -> QueryPolicy.builder()
        .allowSortFields("id")
        .allowFields("id"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unified and operation-specific field permissions must not be mixed");
}
```

- [ ] **Step 3: Run policy tests and verify RED**

```bash
./mvnw -B -ntp -pl criteriaforge-core -Dtest=QueryPolicyTest test
```

Expected: compilation fails because operation-specific APIs do not exist.

- [ ] **Step 4: Implement explicit permission modes and immutable sets**

In `QueryPolicy.Builder`, add a private mode enum and per-operation state:

```java
private enum PermissionMode { NONE, UNIFIED, OPERATION_SPECIFIC }

private PermissionMode permissionMode = PermissionMode.NONE;
private boolean projectionFieldsConfigured;
private boolean filterFieldsConfigured;
private boolean sortFieldsConfigured;
private final Set<String> projectionFields = new LinkedHashSet<>();
private final Set<String> filterFields = new LinkedHashSet<>();
private final Set<String> sortFields = new LinkedHashSet<>();
```

Every unified builder call switches to `UNIFIED`; every operation-specific
call switches to `OPERATION_SPECIFIC`. Reject a switch between those two modes
with the exact tested message. Collection overloads set the corresponding
configured flag even when the collection is empty.

In `QueryPolicy`, store each immutable set and configured flag. In unified
mode, copy the legacy fields plus alias public names into all three sets and
enable all three allowlists only when the legacy field set is non-empty. In
operation-specific mode, do not add alias names automatically.

Implement operation checks using one helper:

```java
private boolean isAllowed(String field, boolean configured, Set<String> fields) {
    var normalized = QueryPath.requireValid(field, "field");
    return !deniedFields.contains(normalized)
        && (!configured || fields.contains(normalized));
}
```

`allowedFields()` computes the intersection of configured operation sets,
treating unconfigured operations as unrestricted. `isFieldAllowed(field)`
returns the conjunction of all three operation checks. Mark the four unified
builder/accessor/check methods `@Deprecated(since = "0.2.0")` and document the
operation-specific replacements.

- [ ] **Step 5: Run policy tests and verify GREEN**

Run the command from Step 3. Expected: all `QueryPolicyTest` tests pass with
deprecation warnings confined to legacy compatibility tests.

- [ ] **Step 6: Commit the permission-model task**

```bash
git add criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicy.java criteriaforge-core/src/test/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicyTest.java
git commit -m "feat: separate query field permissions"
```

### Task 4: Bound projection and sort complexity

**Files:**
- Modify: `criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicy.java`
- Modify: `criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryComplexityValidator.java`
- Modify: `criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryErrorCode.java`
- Modify: `criteriaforge-core/src/test/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicyTest.java`

**Interfaces:**
- Consumes: `QueryRequest.fields`, `QueryRequest.sorting`, and policy validation conventions.
- Produces: `maxProjectionFields`, `maxSortFields`, and two stable error codes.

- [ ] **Step 1: Write failing limit tests**

Add:

```java
@Test
void defaultsBoundProjectionAndSortComplexity() {
    var policy = QueryPolicy.defaults();

    assertThat(policy.maxProjectionFields()).isEqualTo(20);
    assertThat(policy.maxSortFields()).isEqualTo(5);
}

@Test
void rejectsTooManyProjectionFields() {
    var query = QueryRequest.builder().select("id", "status").build();
    var policy = QueryPolicy.builder().maxProjectionFields(1).build();

    assertThatThrownBy(() -> validator.validate(query, policy))
        .isInstanceOfSatisfying(QueryValidationException.class, error -> {
            assertThat(error.code()).isEqualTo(QueryErrorCode.PROJECTION_LIMIT_EXCEEDED);
            assertThat(error.path()).contains("fields");
        });
}

@Test
void rejectsTooManyRequestedSortFields() {
    var query = QueryRequest.builder()
        .orderByAscending("status")
        .orderByDescending("createdAt")
        .build();
    var policy = QueryPolicy.builder().maxSortFields(1).build();

    assertThatThrownBy(() -> validator.validate(query, policy))
        .isInstanceOfSatisfying(QueryValidationException.class, error -> {
            assertThat(error.code()).isEqualTo(QueryErrorCode.SORT_LIMIT_EXCEEDED);
            assertThat(error.path()).contains("sort");
        });
}

@Test
void rejectsNonPositiveProjectionAndSortLimits() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> QueryPolicy.builder().maxProjectionFields(0).build())
        .withMessage("maxProjectionFields must be at least one");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> QueryPolicy.builder().maxSortFields(0).build())
        .withMessage("maxSortFields must be at least one");
}
```

Add the static AssertJ import for `assertThatIllegalArgumentException`.

- [ ] **Step 2: Run policy tests and verify RED**

```bash
./mvnw -B -ntp -pl criteriaforge-core -Dtest=QueryPolicyTest test
```

Expected: compilation fails for the missing limits and error codes.

- [ ] **Step 3: Add limits and validator checks**

Add constants and builder defaults to `QueryPolicy`:

```java
public static final int DEFAULT_MAX_PROJECTION_FIELDS = 20;
public static final int DEFAULT_MAX_SORT_FIELDS = 5;
```

Validate both with the existing positive-integer helper. Add enum constants
`PROJECTION_LIMIT_EXCEEDED` and `SORT_LIMIT_EXCEEDED` to `QueryErrorCode`.

In `QueryComplexityValidator.validate`, check:

```java
if (query.fields().size() > policy.maxProjectionFields()) {
    throw new QueryValidationException(
        QueryErrorCode.PROJECTION_LIMIT_EXCEEDED,
        "Query contains " + query.fields().size() + " projection fields; maximum is "
            + policy.maxProjectionFields(),
        "fields");
}
if (query.sorting().orders().size() > policy.maxSortFields()) {
    throw new QueryValidationException(
        QueryErrorCode.SORT_LIMIT_EXCEEDED,
        "Query contains " + query.sorting().orders().size() + " sort fields; maximum is "
            + policy.maxSortFields(),
        "sort");
}
```

- [ ] **Step 4: Run policy tests and verify GREEN**

Run the command from Step 2. Expected: all tests pass.

- [ ] **Step 5: Commit the complexity task**

```bash
git add criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicy.java criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryComplexityValidator.java criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryErrorCode.java criteriaforge-core/src/test/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicyTest.java
git commit -m "feat: bound projection and sort complexity"
```

### Task 5: Enforce operation permissions before SQL

**Files:**
- Modify: `criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryPolicyValidator.java`
- Modify: `criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaSortBuilder.java`
- Modify: `criteriaforge-jpa/src/test/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryPolicyValidatorTest.java`

**Interfaces:**
- Consumes: `isFilterAllowed`, `isProjectionAllowed`, and `isSortAllowed`.
- Produces: operation-specific preflight rejection with existing `FIELD_NOT_ALLOWED`.

- [ ] **Step 1: Write failing preflight tests**

Add three tests:

```java
@Test
void projectionPermissionDoesNotGrantFilterOrSortPermission() {
    var policy = QueryPolicy.builder()
        .allowProjectionFields("reference")
        .allowFilterFields("status")
        .allowSortFields("createdAt")
        .build();

    assertRejected(
        QueryRequest.builder().where(Filters.field("reference").eq("A")).build(),
        policy,
        QueryErrorCode.FIELD_NOT_ALLOWED);
    assertRejected(
        QueryRequest.builder().orderByAscending("reference").build(),
        policy,
        QueryErrorCode.FIELD_NOT_ALLOWED);
}

@Test
void filterPermissionDoesNotGrantProjectionPermission() {
    var policy = QueryPolicy.builder()
        .allowProjectionFields("reference")
        .allowFilterFields("status")
        .build();

    assertRejected(
        QueryRequest.builder().select("status").build(),
        policy,
        QueryErrorCode.FIELD_NOT_ALLOWED);
}
```

Retain the existing statistics assertion proving zero prepared statements.

- [ ] **Step 2: Run the JPA policy tests and verify RED**

```bash
./mvnw -B -ntp -pl criteriaforge-jpa -am -Dtest=JpaQueryPolicyValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: at least one prohibited operation is accepted because validation
still calls the unified check.

- [ ] **Step 3: Route each operation through its own permission**

Change filter preflight to call `policy.isFilterAllowed(field)`, projection
preflight to call `policy.isProjectionAllowed(field)`, and sort preflight to
call `policy.isSortAllowed(field)`. Split `validateCommon` so it receives the
operation's boolean permission and emits an operation-specific message while
retaining `FIELD_NOT_ALLOWED`.

Change `JpaSortBuilder`'s caller-controlled sort check from
`isFieldAllowed` to `isSortAllowed` so defense in depth matches preflight.

- [ ] **Step 4: Run the JPA policy tests and verify GREEN**

Run the command from Step 2. Expected: all tests pass and prohibited requests
prepare zero SQL statements.

- [ ] **Step 5: Commit the JPA permission task**

```bash
git add criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryPolicyValidator.java criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaSortBuilder.java criteriaforge-jpa/src/test/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryPolicyValidatorTest.java
git commit -m "feat: enforce operation query permissions"
```

### Task 6: Add deterministic primary-key and policy tie-breaking

**Files:**
- Modify: `criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicy.java`
- Modify: `criteriaforge-core/src/test/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicyTest.java`
- Modify: `criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryEngine.java`
- Modify: `criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryPolicyValidator.java`
- Modify: `criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaSortBuilder.java`
- Modify: `criteriaforge-jpa/src/test/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryEngineTest.java`

**Interfaces:**
- Consumes: caller sort orders, aliases, JPA identifier metadata, and hidden sort selections.
- Produces: `tieBreaker` policy override and deterministic effective ordering.

- [ ] **Step 1: Write failing core tie-breaker tests**

Add:

```java
@Test
void configuresAnOptionalTieBreakerOverride() {
    assertThat(QueryPolicy.defaults().tieBreaker()).isEmpty();

    var ascending = QueryPolicy.builder().tieBreaker("reference").build();
    var descending = QueryPolicy.builder()
        .tieBreaker("createdAt", SortDirection.DESC)
        .build();

    assertThat(ascending.tieBreaker())
        .contains(new Sorting.Order("reference", SortDirection.ASC));
    assertThat(descending.tieBreaker())
        .contains(new Sorting.Order("createdAt", SortDirection.DESC));
}
```

- [ ] **Step 2: Run core policy tests and verify RED**

```bash
./mvnw -B -ntp -pl criteriaforge-core -Dtest=QueryPolicyTest test
```

Expected: compilation fails because `tieBreaker` APIs do not exist.

- [ ] **Step 3: Implement policy tie-breaker configuration**

Store `Optional<Sorting.Order>` in `QueryPolicy`. Add builder methods:

```java
public Builder tieBreaker(String field) {
    return tieBreaker(field, SortDirection.ASC);
}

public Builder tieBreaker(String field, SortDirection direction) {
    tieBreaker = new Sorting.Order(field, direction);
    return this;
}
```

Require non-null direction through `Sorting.Order`; expose the value with an
immutable `Optional` accessor.

- [ ] **Step 4: Run core policy tests and verify GREEN**

Run the command from Step 2. Expected: all tests pass.

- [ ] **Step 5: Write failing JPA ordering tests**

The existing fixture has four entities with the same `PAID` status. Add:

```java
@Test
void appendsThePrimaryKeyToCallerSorting() {
    var query = QueryRequest.builder()
        .orderByAscending("status")
        .limit(20)
        .build();

    var result = executor.findAll(OrderEntity.class, query);

    assertThat(result.content())
        .extracting(OrderEntity::getOrderKey)
        .isSorted();
}

@Test
void usesAConfiguredTieBreakerAfterCallerSorting() {
    var policy = QueryPolicy.builder()
        .allowSortFields("status")
        .tieBreaker("reference", SortDirection.DESC)
        .build();
    var publicExecutor = new JpaQueryEngine(entityManager, ignored -> policy);
    var query = QueryRequest.builder()
        .orderByAscending("status")
        .limit(20)
        .build();

    var result = publicExecutor.findAll(OrderEntity.class, query);

    assertThat(result.content()).extracting(OrderEntity::getReference)
        .containsExactly("THIRD", "SECOND", "FOURTH", "FIRST");
}

@Test
void callerDirectionWinsWhenItAlreadySortsByTheTieBreaker() {
    var policy = QueryPolicy.builder()
        .alias("amount", "total")
        .allowSortFields("total")
        .tieBreaker("amount", SortDirection.DESC)
        .build();
    var publicExecutor = new JpaQueryEngine(entityManager, ignored -> policy);
    var query = QueryRequest.builder()
        .orderByAscending("total")
        .limit(20)
        .build();

    var result = publicExecutor.findAll(OrderEntity.class, query);

    assertThat(result.content()).extracting(OrderEntity::getReference)
        .containsExactly("FIRST", "SECOND", "THIRD", "FOURTH");
}
```

Add `rejectsInvalidConfiguredTieBreakerBeforeExecutingSql` to
`JpaQueryPolicyValidatorTest`, using policy
`relationshipTraversal(true).tieBreaker("items.product.name")`; assert
`UNSUPPORTED_PROJECTION` and zero prepared statements. Update the existing
`keepsSortSelectionsHiddenForDistinctPluralProjections` test in
`JpaQueryEngineTest` so its existing exact key-set assertion proves that the
new primary-key hidden selection does not leak into the result map.

- [ ] **Step 6: Run JPA tests and verify RED**

```bash
./mvnw -B -ntp -pl criteriaforge-jpa -am -Dtest=JpaQueryEngineTest,JpaQueryPolicyValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the primary-key append assertion or configured tie-breaker tests fail
because current execution only uses the identifier when no sort is requested.

- [ ] **Step 7: Build one effective ordering for entity and projection queries**

In `JpaQueryEngine`, derive:

```java
private ResolvedTieBreaker tieBreaker(Class<?> entityType, QueryPolicy policy) {
    return policy.tieBreaker()
        .map(order -> new ResolvedTieBreaker(
            policy.resolveField(order.field()), order.direction(), order.field()))
        .orElseGet(() -> new ResolvedTieBreaker(
            identifierName(entityType), SortDirection.ASC, null));
}
```

Use a private record:

```java
private record ResolvedTieBreaker(
    String persistentPath,
    SortDirection direction,
    String publicField) {
}
```

Resolve each requested sort to its persistent path. Append the tie-breaker only
when no requested sort resolves to the same path. Build the trusted appended
order through a new `JpaSortBuilder.buildResolved(...)` method that resolves a
persistent path and enforces relationship depth/to-many structural rules but
does not call `isSortAllowed`.

Add `JpaQueryPolicyValidator.validateTieBreaker` for configured overrides. It
resolves aliases, verifies the path exists, rejects scalar-invalid/to-many
paths, and enforces traversal/depth without enforcing caller-facing field
permissions or `@QueryHidden`.

For projected plural-join queries, pass the complete effective persistent sort
source list—including an appended tie-breaker—to `addHiddenSortSelections`.
Continue limiting tuple assembly to the number of visible request fields.

- [ ] **Step 8: Run JPA tests and verify GREEN**

Run the command from Step 6. Expected: all tests pass, tie-breakers are stable,
and hidden selections do not leak into projection maps.

- [ ] **Step 9: Commit the tie-breaker task**

```bash
git add criteriaforge-core/src/main/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicy.java criteriaforge-core/src/test/java/io/github/emmanuelcazarez/criteriaforge/core/QueryPolicyTest.java criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryEngine.java criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryPolicyValidator.java criteriaforge-jpa/src/main/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaSortBuilder.java criteriaforge-jpa/src/test/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryEngineTest.java criteriaforge-jpa/src/test/java/io/github/emmanuelcazarez/criteriaforge/jpa/JpaQueryPolicyValidatorTest.java
git commit -m "feat: add deterministic query tie breakers"
```

### Task 7: Update in-repository consumer contracts

**Files:**
- Modify: `criteriaforge-example/src/main/java/io/github/emmanuelcazarez/criteriaforge/example/ExampleConfiguration.java`
- Modify: `criteriaforge-example/src/test/java/io/github/emmanuelcazarez/criteriaforge/example/ConsumerApiTest.java`
- Modify: `criteriaforge-spring-boot-autoconfigure/src/test/java/io/github/emmanuelcazarez/criteriaforge/autoconfigure/CriteriaForgeAutoConfigurationTest.java`

**Interfaces:**
- Consumes: operation-specific policy APIs, `executeEntities`, and `QueryResult.map`.
- Produces: compilation-level consumer examples for the 0.2 API.

- [ ] **Step 1: Write a failing consumer API contract**

In `ConsumerApiTest.exposesOneIntentionRevealingQueryContract`, add reflection
assertions alongside the existing `execute` contract:

```java
var executeEntities = engineType.orElseThrow().getMethod(
    "executeEntities", Class.class, requestType.orElseThrow());
assertThat(executeEntities.getReturnType()).isEqualTo(QueryResult.class);
assertThat(QueryResult.class.getMethod("map", java.util.function.Function.class))
    .isNotNull();
assertThat(QueryResult.class.getMethod("hasNext")).isNotNull();
assertThat(QueryResult.class.getMethod("hasPrevious")).isNotNull();
```

- [ ] **Step 2: Update the example policy to independent permissions**

Configure the exact fields exercised by the example:

```java
var policy = QueryPolicy.builder()
    .relationshipTraversal(true)
    .maxPageSize(100)
    .maxProjectionFields(10)
    .maxSortFields(3)
    .alias("amount", "total")
    .alias("buyerName", "customer.name")
    .allowProjectionFields("reference", "buyerName", "amount")
    .allowFilterFields("status", "amount", "customer.country")
    .allowSortFields("amount")
    .build();
```

Leave the tie-breaker unconfigured so the example exercises the primary-key
default.

Update `CriteriaForgeAutoConfigurationTest.RegisteredPolicy` to use
`allowProjectionFields("id")`, and assert `projectionFields()` rather than the
deprecated unified accessor.

- [ ] **Step 3: Run example and auto-configuration tests**

```bash
./mvnw -B -ntp -pl criteriaforge-example,criteriaforge-spring-boot-autoconfigure -am test
```

Expected: all consumer, application-context, HTTP, and service tests pass.

- [ ] **Step 4: Commit consumer contract updates**

```bash
git add criteriaforge-example/src/main/java/io/github/emmanuelcazarez/criteriaforge/example/ExampleConfiguration.java criteriaforge-example/src/test/java/io/github/emmanuelcazarez/criteriaforge/example/ConsumerApiTest.java criteriaforge-spring-boot-autoconfigure/src/test/java/io/github/emmanuelcazarez/criteriaforge/autoconfigure/CriteriaForgeAutoConfigurationTest.java
git commit -m "test: cover expanded consumer query API"
```

### Task 8: Verify both Spring Boot lines and quality gates

**Files:**
- Modify only files required to fix verification failures caused by Tasks 1-7.

**Interfaces:**
- Consumes: the complete 0.2 implementation.
- Produces: evidence that the full reactor remains compatible and releasable.

- [ ] **Step 1: Run the default Spring Boot 3.5 reactor**

```bash
./mvnw -B -ntp clean verify -Dspring-boot.version=3.5.16
```

Expected: reactor success with zero test failures.

- [ ] **Step 2: Run the Spring Boot 4.1 compatibility reactor**

```bash
./mvnw -B -ntp clean verify -Dspring-boot.version=4.1.0
```

Expected: reactor success with zero test failures.

- [ ] **Step 3: Run quality checks**

```bash
./mvnw -B -ntp -Pquality verify
```

Expected: Checkstyle, JaCoCo, ArchUnit, unit tests, and integration tests pass.

- [ ] **Step 4: Run PostgreSQL integration tests when Docker is available**

```bash
./mvnw -B -ntp -Ppostgresql-tests verify
```

Expected: PostgreSQL Testcontainers integration tests pass. If Docker is not
available, record the exact environment error and rely on the required CI
`postgresql` check rather than claiming local success.

- [ ] **Step 5: Inspect the final diff and public API scope**

```bash
git status --short
git diff --check
git diff --stat
git diff -- criteriaforge-core criteriaforge-jpa criteriaforge-example criteriaforge-spring-boot-autoconfigure
```

Expected: no whitespace errors, no cursor-pagination or envelope code, no
release version changes, and no unrelated edits.

- [ ] **Step 6: Commit any verification-only correction**

If verification required a focused correction, stage only those corrected
files and commit:

```bash
git commit -m "fix: complete query policy compatibility"
```

If no correction was required, do not create an empty commit.
