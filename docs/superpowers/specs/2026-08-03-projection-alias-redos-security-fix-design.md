# Projection Alias ReDoS Security Fix Design

## Context

GitHub CodeQL alert 1 reports `java/polynomial-redos` in
`DefaultQueryParameterParser.select`. The current expression
`(?i)^(.+?)\\s+as\\s+(.+)$` runs against user-controlled `fields` values and
backtracks polynomially when the input contains long whitespace sequences.
Local reproduction measured about 577 ms for 20,000 spaces, with quadratic
growth.

## Decision

Replace the regular expression with a deterministic, single-pass alias
separator scan. The scanner will find the first case-insensitive `as` token
that has at least one Java-regex-compatible whitespace character on both
sides, then trim and pass the two values to the existing builder validation.
Tokens without a valid separator will continue through the existing
non-aliased selection path.

This preserves the public syntax and validation behavior while making parsing
linear in the length of the supplied field token. A possessive or atomic regex
is rejected because it is harder to audit and may remain vulnerable to future
regex changes. A length limit alone is rejected because it only mitigates the
root cause and would introduce a new compatibility constraint.

## Testing

Add a regression test before changing production code. It will parse an
adversarial projection token containing a valid prefix followed by 50,000
spaces and assert completion within one second while retaining the existing
validation result. The current implementation must fail this timing bound.

Existing alias tests will continue to prove normal behavior. Add focused cases
for mixed-case `AS` and non-space whitespace so the replacement preserves the
current case-insensitive and whitespace semantics. Run the Spring Web module
tests, the full Maven reactor, Java 17 with Spring Boot 3 and 4, PostgreSQL, and
quality checks.

## Delivery and release

Work on `fix/codeql-polynomial-redos`, open a pull request to protected `dev`,
and squash-merge after all required checks pass. Prepare patch release `0.1.1`
from `dev`, update version and changelog metadata, and use the controlled
`dev -> main` release pull request. After merge, create and verify the signed
`v0.1.1` tag, run the protected release workflow, approve its `maven-central`
environment, and verify the published artifacts and CodeQL alert state.

The runnable `criteriaforge-example` remains excluded from Maven Central.
