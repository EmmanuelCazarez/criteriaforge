# Security policy

## Supported versions

Before the first public release, only the latest commit on `dev` receives security fixes. After release, the latest `0.1.x` version and the current development branch are supported.

## Reporting a vulnerability

Do not open a public issue containing an exploit, credential, sensitive field name, or affected deployment detail. Use GitHub's private security-advisory reporting for the CriteriaForge repository. If that feature is not yet available, contact the repository owner privately through their GitHub profile and request a secure reporting channel before sharing details.

Include the affected module and version, impact, minimal reproduction, and any known mitigation. Remove real application data and secrets from the report.

You should receive an acknowledgement within seven days. Triage will determine severity, supported versions, a coordinated fix and disclosure timeline, and whether a security release is required. Please allow time for a patched artifact to reach Maven Central before public disclosure.

## Scope

Reports about policy bypass, hidden-field exposure, preflight validation, unsafe query complexity, dependency vulnerabilities, artifact integrity, or release-workflow compromise are in scope. Application-specific authorization and database configuration remain consumer responsibilities unless CriteriaForge bypasses an explicitly configured policy.

For safe deployment guidance, read [Security and query policy](docs/security.md).
