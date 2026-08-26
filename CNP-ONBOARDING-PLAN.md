# CNP Onboarding Plan — apim/marketplace

Service: `service-api-marketplace`  
Product: `apim` · Component: `marketplace`  
Ticket: AMP-944

Status badges: ✅ Already done · ⚠️ Check first · ○ To do · 🔄 In progress

---

## Goal

Deploy a working API Marketplace onboarding service into the HMCTS Cloud Native Platform (CNP) AKS cluster, with all dependent infrastructure provisioned in Azure:

- **Service** — Spring Boot application running in the `apim` Kubernetes namespace, deployed and managed by Flux GitOps via a Helm chart
- **PostgreSQL** — Azure Database for PostgreSQL Flexible Server, provisioned by Terraform, credentials stored in Key Vault
- **Azure Key Vault** — Shared vault (`apim-{env}`) holding all secrets (App Insights key, DB credentials), mounted into the pod via Workload Identity
- **Managed Identity** — Azure User-Assigned Managed Identity granting the pod read access to the vault without any stored credentials
- **Jenkins pipeline** — CI/CD pipeline that builds, tests, pushes a Docker image to ACR, runs Terraform, and deploys to AKS on every PR and master merge

---

## Names Reference

All derived names follow the pattern `{product}-{component}` = `apim-marketplace`.

| What | Name |
|------|------|
| **GitHub repo** | `service-api-marketplace` |
| **GitHub org URL** | `github.com/hmcts/service-api-marketplace` |
| **GitHub topic** | `jenkins-cft-j-z` |
| **Jenkins product** | `apim` |
| **Jenkins component** | `marketplace` |
| **Jenkins job path** | `HMCTS_j_to_z/service-api-marketplace` |
| **Kubernetes namespace** | `apim` |
| **Helm release name** | `apim-marketplace` |
| **Helm chart name** | `apim-marketplace` |
| **Docker image** | `hmctsprod.azurecr.io/apim/marketplace:{tag}` |
| **Flux HelmRelease** | `apim-marketplace` |
| **Flux image policy** | `apim-marketplace` |
| **Flux image repo** | `apim-marketplace` |
| **Flux config path** | `apps/apim/apim-marketplace/` |
| **Azure resource group** | `apim-shared-{env}` (e.g. `apim-shared-aat`) |
| **Azure Key Vault (AAT)** | `apim-aat` |
| **Azure Key Vault (sandbox)** | `apim-sbox` (NOT `apim-sandbox` — globally taken by another team) |
| **Managed Identity** | `apim-{env}-mi` (e.g. `apim-aat-mi`, `apim-sandbox-mi`) |
| **Managed Identity RG** | `managed-identities-{env}-rg` |
| **Helm `aadIdentityName`** | `apim` |
| **Internal ingress host** | `apim-marketplace-{env}.service.core-compute-{env}.internal` |
| **Staging URL (AAT)** | `apim-marketplace-staging.aat.platform.hmcts.net` |
| **Preview URL** | `apim-marketplace-pr-{N}.preview.platform.hmcts.net` |
| **Java package** | `uk.gov.hmcts.cp` |
| **Gradle group** | `uk.gov.hmcts.cp` |
| **Spring app name** | `service-api-marketplace` |
| **Jenkins AAD group** | `DTS API Marketplace` |
| **Slack contact** | `#api-marketplace-tech` |
| **Slack build notices** | `#api-marketplace-build-notices` |
| **TF approval whitelist** | `cnp-jenkins-config/terraform-infra-approvals/service-api-marketplace.json` |

---

## Phase 0 — Verify current state before touching anything

| # | Step | Status | Notes |
|---|------|--------|-------|
| 0.1 | `apim` team in `cnp-jenkins-config/team-config.yml` | ✅ Done | namespace, AAD group, Slack channels all present |
| 0.2 | `apps/apim/` namespace kustomizations in `cnp-flux-config` | ✅ Done | aat, demo, preview, serviceaccount all exist |
| 0.3 | Old `feat/add-apim-marketplace` flux branch cleaned up | ✅ Done | Remote branches deleted; start fresh with new PRs |
| 0.4 | ACR images at `hmctsprod.azurecr.io/apim/marketplace` | ✅ Done | Images building and pushing via Jenkins CI |
| 0.5 | Azure resources from previous attempt (RG, vault, MI in demo) | ✅ Done | Existing resources adopted via terraform import |

---

## Phase 1 — GitHub repository setup

| # | Step | Status | Notes |
|---|------|--------|-------|
| 1.1 | Repo exists at `github.com/hmcts/service-api-marketplace` | ✅ Done | |
| 1.2 | Add GitHub topic `jenkins-cft-j-z` | ✅ Done | |
| 1.3 | Branch protection on `master` | ✅ Done | |
| 1.4 | Update `catalog-info.yaml` | ✅ Done | |

---

## Phase 2 — Service repo: Jenkinsfile, Helm chart, Terraform, application code

### 2a — Reset

| # | Step | Status |
|---|------|--------|
| 2.1 | Delete `amp-944` branch | ✅ Done |

### 2b — Jenkinsfile_CNP

| # | Step | Status | Notes |
|---|------|--------|-------|
| 2.2 | Rename `Jenkinsfile_template` → `Jenkinsfile_CNP` | ✅ Done | |
| 2.3 | Set product/component and add pipeline features | ✅ Done | `withPipeline('java', 'apim', 'marketplace')` with `enableAksStagingDeployment()` and `disableLegacyDeployment()` |

### 2c — Helm chart

| # | Step | Status | Notes |
|---|------|--------|-------|
| 2.4 | Chart at `charts/apim-marketplace/` | ✅ Done | |
| 2.5 | `Chart.yaml` updated | ✅ Done | |
| 2.6 | `values.yaml` with keyVaults, postgres secrets | ✅ Done | Uses `apim` vault key (→ `apim-{env}`). Sandbox overrides to `apim-sbox` via `excludeEnvironmentSuffix: true` in flux |
| 2.7 | `values.aat.template.yaml` | ✅ Done | |
| 2.8 | `values.preview.template.yaml` | ✅ Done | |

### 2d — Terraform

| # | Step | Status | Notes |
|---|------|--------|-------|
| 2.9 | `infrastructure/state.tf` — provider version pins | ✅ Done | |
| 2.10 | `infrastructure/main.tf` — resource group + key vault + postgres | ✅ Done | vault_name variable allows sandbox override to `apim-sbox` |
| 2.11 | `infrastructure/variables.tf` | ✅ Done | Includes `vault_name`, `pgsql_public_access`, `pgsql_subnet_suffix` |
| 2.12 | `aat.tfvars`, `sandbox.tfvars` | ✅ Done | |
| 2.12a | Adopt existing Azure resources via `terraform import` | ✅ Done | |

### 2e — Application code

| # | Step | Status | Notes |
|---|------|--------|-------|
| 2.13 | Controllers, config, application class | ✅ Done | |
| 2.14 | Spring Boot version compatible with CNP AKS | ✅ Done | |

---

## Phase 3 — cnp-jenkins-config PRs

| # | Step | Status | PR target |
|---|------|--------|-----------|
| 3.1 | Add `terraform-infra-approvals/service-api-marketplace.json` | ✅ Done | `cnp-jenkins-config` |
| 3.2 | Add to `deployment-controls.yml` | ✅ Done | `cnp-jenkins-config` |
| 3.3 | Add to `environment-approvals.yml` (prod only) | ○ Later | `cnp-jenkins-config` |

---

## Phase 4 — cnp-flux-config: namespace, HelmRelease, workload identity

| # | Step | Status | Notes |
|---|------|--------|-------|
| 4.1 | apim namespace kustomizations on Flux master | ✅ Done | |
| 4.2 | `apps/apim/apim-marketplace/apim-marketplace.yaml` | ✅ Done | |
| 4.3 | Image policy files via `add-image-policies.sh` | ✅ Done | `image-policy.yaml`, `image-repo.yaml`, automation wired |
| 4.4 | HelmRelease added to `apps/apim/base/kustomization.yaml` | ✅ Done | |
| 4.5 | Env patch files (`aat.yaml`, `demo.yaml`, `preview.yaml`) | ✅ Done | |
| 4.6 | Flux dry-run tests | ✅ Done | |
| 4.7 | Workload Identity wiring for AAT (`add-wl-identity.sh`) | ✅ Done | `serviceaccount/aat.yaml` with MI client-id `579ca499-...` |
| 4.7a | Vault mount enabled in chart + MI has vault access | ✅ Done | keyVaults re-added to `values.yaml`, postgres secrets mounted |

---

## Phase 5 — Helm chart publication to hmcts-charts

| # | Step | Status | Notes |
|---|------|--------|-------|
| 5.1 | Chart version bumped in `Chart.yaml` before each release | ✅ Done | Jenkins publishes on version increment |
| 5.2 | Chart appears in hmcts-charts `stable/apim-marketplace/` | ✅ Done | |

---

## Phase 6 — First Jenkins run

| # | Step | Status | Notes |
|---|------|--------|-------|
| 6.0 | Seed job approved and run | ✅ Done | |
| 6.0a | Jenkins org scan triggered | ✅ Done | |
| 6.0b | Repository scan queued first build | ✅ Done | |
| 6.1 | First PR triggered Jenkins build | ✅ Done | |
| 6.2 | Jenkins stages: Build → Test → Docker push → Terraform → Deploy | ✅ Done | |
| 6.3 | Workload identity script run, Flux PR raised | ✅ Done | |

---

## Phase 7 — Verify deployment

| # | Step | Status | Command / URL |
|---|------|--------|---------------|
| 7.1 | Preview health check (PR build) | ✅ Done | `https://apim-marketplace-pr-{N}.preview.platform.hmcts.net/health` |
| 7.2 | AAT staging health check (master merge) | ✅ Done | `https://apim-marketplace-staging.aat.platform.hmcts.net/health` |
| 7.3 | Flux HelmRelease status healthy | ✅ Done | `kubectl get helmrelease -n apim` on cft-aat-01-aks |
| 7.4 | Jenkins check added to branch protection | ✅ Done | `continuous-integration/jenkins/pr-merge` required status check |

---

## Phase 8a — PostgreSQL infrastructure (AMP-944)

| # | Step | Status | Notes |
|---|------|--------|-------|
| 8.1 | `terraform-module-postgresql-flexible` in `infrastructure/main.tf` | ✅ Done | `subnet_suffix = "expanded"` for AAT. Sandbox uses `public_access = true` (no postgresql subnet in sbox vnet) |
| 8.2 | DB credentials stored as vault secrets in terraform | ✅ Done | `marketplace-POSTGRES-USER/PASS/HOST/PORT/DATABASE` in `apim-{env}` vault |
| 8.3 | `terraform-infra-approvals` updated for postgres module | ✅ Done | |
| 8.4 | `aks_subscription_id` + `pgsql_sku` in variables and tfvars | ✅ Done | |
| 8.5 | Pin postgresql JDBC driver to 42.7.12 | ✅ Done | OWASP CVE-2026-54291 + CVE-2026-66299 |
| 8.6 | TestContainers for integration tests | ✅ Done | |
| 8.7/8.8 | keyVaults / datasource re-added to chart | ✅ Done | |

---

## Phase 8b — Wire app to use DB (follow-up, after 8a merges)

| # | Step | Status | Notes |
|---|------|--------|-------|
| 8b.1 | `aadIdentityName` + `keyVaults` in `values.yaml` | ✅ Done | Postgres secrets mounted from `apim` vault |
| 8b.2 | Datasource + flyway config in `application.yaml` | ✅ Done | `spring.config.import: optional:configtree:/mnt/secrets/apim/` |
| 8b.3 | `readiness.include: "db"` | ○ Todo | Add once DB connectivity verified in AAT |
| 8b.4 | First Flyway migration `V1__init.sql` | ○ Todo | Enable `spring.flyway.enabled: true` |
| 8b.5 | JPA entities and repositories | ○ Todo | First actual DB code |
| 8b.6 | Integration tests via TestContainers against real schema | ○ Todo | TestContainers already wired |

---

## Phase 9 — Sandbox deployment (AMP-1018)

> **Goal:** Deploy `service-api-marketplace` to CNP sbox cluster so `sps-api-mgmt-sbox` (SPS APIM sbox) can route to it.
>
> **Sandbox Jenkins:** `sandbox-build.hmcts.net/job/HMCTS_j_to_z_Sandbox/job/service-api-marketplace/job/master/`
>
> **Key differences from AAT:**
> - Sandbox vnet has no `postgresql` or `postgresql-expanded` subnets — use `public_access = true`
> - `apim-sandbox` vault name is globally taken by another HMCTS team — use `apim-sbox`
> - Sandbox Jenkins uses `withParameterizedPipeline` (not `withParameterizedInfraPipeline`) with `Jenkinsfile_parameterized`
> - tfenv requires `.terraform-version` file in repo root

| # | Step | Status | Notes |
|---|------|--------|-------|
| 9.1 | `.terraform-version` file at repo root | ✅ Done | `1.14.5` — PR #35 |
| 9.2 | `Jenkinsfile_parameterized` switched to `withParameterizedPipeline` | ✅ Done | `withParameterizedPipeline('java', 'apim', 'marketplace', params.ENVIRONMENT, 'sandbox')` — PR #37 |
| 9.3 | `GetWelcomeTest` restricted to `@WebMvcTest(RootController.class)` | ✅ Done | PR #37 |
| 9.4 | `pgsql_subnet_suffix` variable (null default, `expanded` for AAT) | ✅ Done | PR #38 |
| 9.5 | `pgsql_public_access` variable + `sandbox.tfvars` `pgsql_public_access = true` | ✅ Done | PR #39 |
| 9.6 | `pgsql_delegated_subnet_id = "bypass"` when public_access to skip subnet data source | ✅ Done | PR #41 |
| 9.7 | Removed `infrastructure/import.tf` (creator_access_policy import fails on first run) | ✅ Done | PR #40 |
| 9.8 | `vault_name` variable + `sandbox.tfvars` `vault_name = "apim-sbox"` | ✅ Done | PR #42 |
| 9.9 | `depends_on = [module.vault]` on all secret resources (race condition fix) | ✅ Done | PR #43 |
| 9.10 | Sandbox infra apply — postgres + vault + secrets all created | ✅ Done | Build #7. `apim-flexible-sandbox` postgres, `apim-sbox` vault, `apim-sandbox-mi` (clientId `775648eb-07db-4354-90f6-f0bca549341b`) |
| 9.11 | `apps/apim/serviceaccount/sbox.yaml` with MI client-id | ✅ Done | cnp-flux-config PR #47084 |
| 9.12 | `apps/apim/sbox/base/kustomization.yaml` — add service account patch | ✅ Done | cnp-flux-config PR #47084 |
| 9.13 | `apps/apim/apim-marketplace/sbox.yaml` — enable vaults, `apim-sbox` with `excludeEnvironmentSuffix`, workloadClientID, remove Spring excludes | 🔄 PR open | cnp-flux-config PR #47084 — awaiting merge and flux reconcile |
| 9.14 | Verify pod comes up healthy in sbox cluster | ○ Todo | After PR #47084 merges — check `kubectl get helmrelease -n apim` on sbox cluster and pod logs |
| 9.15 | Verify SPS APIM sbox can route to service | ○ Todo | Route test from `sps-api-mgmt-sbox` |

---

## Reference: Azure Subscriptions

| Subscription | ID | Used for |
|---|---|---|
| DCD-CNP-DEV | `1c4f0704-a29e-403d-b719-b90c34ef14c9` | Terraform creates MIs, RGs, vaults (non-prod) |
| DCD-CNP-QA | `7a4e3bd5-ae3a-4d0c-b441-2188fee3ff1c` | |
| DCD-CNP-Prod | `8999dec3-0104-4a27-94ee-6588559729d1` | ACR (`hmctsprod`), prod infra |
| DCD-CFTAPPS-STG | `96c274ce-846d-4e48-89a7-d528432298a7` | AAT AKS cluster (`cft-aat-01-aks`) |
| DTS-CFTPTL-INTSVC | `1baf5470-1c3e-40d3-a6f7-74bfbce4b348` | Jenkins infra, DNS records |
| sbox | `bf308a5c-0624-4334-8ff8-8dca9fd43783` | Sandbox AKS cluster, sandbox infra |

---

## Known gotchas (hard-won)

- **`apim-sandbox` vault name taken** — globally taken by another HMCTS team. Use `apim-sbox` in sandbox with `vault_name` variable override and `excludeEnvironmentSuffix: true` in flux.
- **Sandbox subnet** — sbox vnet has no `postgresql` or `postgresql-expanded` subnets. Must use `public_access = true` and `pgsql_delegated_subnet_id = "bypass"` to skip the subnet data source.
- **Access policy race condition** — `azurerm_key_vault_secret` resources must have `depends_on = [module.vault]` so secrets wait for all access policies to propagate before writing.
- **`withParameterizedInfraPipeline` vs `withParameterizedPipeline`** — infra pipeline runs tf from repo root; app pipeline runs tf from `infrastructure/`. Use `withParameterizedPipeline('java', ...)` for sandbox job.
- **tfenv** — sandbox Jenkins agents need `.terraform-version` in repo root.
- **`import.tf` on first run** — any `import` block for a resource that doesn't yet exist (e.g. vault access policy on first sandbox run) will fail the plan. Remove before first run.
- **Old ACR registry** — use `hmctsprod.azurecr.io` not `hmctspublic`. The `add-image-policies.sh` script still defaults to `hmctspublic` — override: `./add-image-policies.sh apim apim marketplace hmctsprod`.
- **Startup probe port** — CNP java chart probes on `applicationPort` (8080). App must listen on 8080; set `server.port: 8080` and `management.endpoints.web.base-path: /` in `application.yaml`.
- **Smoke test on Java 25** — replace rest-assured with `java.net.http.HttpClient`; groovyx HTTP builder throws NPE on Java 25.
