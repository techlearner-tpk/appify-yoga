# Temporary image scan applicability exceptions

Reviewed 2026-10-10; expires **2026-11-09**. Owner: platform maintainers.

The image contains Spring MVC 6.2.19, but the application does not invoke the features required by these two findings. `.trivyignore.yaml` scopes each exception to that exact Maven package and version. Other findings still fail CI.

| Finding | Vendor exploit prerequisites | Application evidence |
| --- | --- | --- |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | XsltView plus wildcard mapping with implicit view-name rendering | Backend controllers are JSON REST controllers; no XSLT views, view resolvers or view-rendering mappings are configured. Frontend rendering is in Next.js. |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | Server-sent events carrying view fragments with attacker-controlled data | No SSE emitter, SSE response or view-fragment renderer exists in this API. |

`ArchitectureTest.apiDoesNotUseFeaturesCoveredByTemporaryScanExceptions` rejects direct application dependencies on Spring servlet views or SSE types. Remove these exceptions and reassess before introducing view rendering or SSE, including via configuration or a third-party integration; a source architecture check cannot inspect every external configuration.

The vendor lists 6.2.20 as enterprise-only and 7.0.9 as the public fix. Do not override Spring 7 into Boot 3. A separate supported-platform upgrade or enterprise patch should remove these exceptions. Reassess before expiry; do not extend them automatically. The installed library is still an affected version even though the documented exploit paths are absent in this application.
