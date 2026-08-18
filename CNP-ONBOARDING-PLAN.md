# CNP Onboarding Plan — apim/marketplace

Service: `service-api-marketplace`  
Product: `apim` · Component: `marketplace`  
Ticket: AMP-944

Status badges: ✅ Already done · ⚠️ Check first · ○ To do

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
| **Docker image** | `hmctspublic.azurecr.io/apim/marketplace:{tag}` |
| **Flux HelmRelease** | `apim-marketplace` |
| **Flux image policy** | `apim-marketplace` |
| **Flux image repo** | `apim-marketplace` |
| **Flux config path** | `apps/apim/apim-marketplace/` |
| **Azure resource group** | `apim-shared-{env}` (e.g. `apim-shared-aat`) |
| **Azure Key Vault** | `apim-{env}` (e.g. `apim-aat`) |
| **Managed Identity** | `apim-{env}-mi` (e.g. `apim-aat-mi`) |
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
| 0.4 | ACR images at `hmctspublic.azurecr.io/apim/marketplace` | ⚠️ Check | `az acr repository show-tags -n hmctspublic --repository apim/marketplace --subscription DCD-CNP-PROD` |
| 0.5 | Azure resources from previous attempt (RG, vault, MI in demo) | ⚠️ Check | `az group show --name apim-shared-demo --subscription DCD-CNP-DEV` — decide reuse or destroy |

---

## Phase 1 — GitHub repository setup

| # | Step | Status | Notes |
|---|------|--------|-------|
| 1.1 | Repo exists at `github.com/hmcts/service-api-marketplace` | ○ Todo | Must be public (GOV.UK open-source policy) |
| 1.2 | Add GitHub topic `jenkins-cft-j-z` | ○ Todo | `service-api-marketplace` starts with S → falls in J-Z range. Set in GitHub Settings → General → Topics |
| 1.3 | Branch protection on `master` | ○ Todo | Require PR + min 1 approval + "Do not allow bypassing". Add `continuous-integration/jenkins/pr-merge` as required check **after first Jenkins run** (name only appears once it has run) |
| 1.4 | Update `catalog-info.yaml` | ○ Todo | Replace all `${{ values.xxx }}` Backstage template placeholders with real values |

---

## Phase 2 — Service repo: Jenkinsfile, Helm chart, Terraform, application code

### 2a — Reset

| # | Step | Status |
|---|------|--------|
| 2.1 | Delete `amp-944` branch — the CPP/GitHub Actions code we added is wrong for CNP | ○ Todo |

### 2b — Jenkinsfile_CNP

| # | Step | Status | Notes |
|---|------|--------|-------|
| 2.2 | Rename `Jenkinsfile_template` → `Jenkinsfile_CNP` | ○ Todo | |
| 2.3 | Set product/component and add pipeline features | ○ Todo | See snippet below |

```groovy
@Library("Infrastructure")

def type = "java"
def product = "apim"
def component = "marketplace"

withPipeline(type, product, component) {
  enableAksStagingDeployment()
  disableLegacyDeployment()
}
```

### 2c — Helm chart

> **Vault naming limit:** `{product}-{component}` must be ≤ 15 chars for the vault name `{product}-{component}-{env}`.
> `apim-marketplace` = 16 chars — use the shared vault pattern `apim-{env}` instead (matches what the previous attempt created).

| # | Step | Status | Notes |
|---|------|--------|-------|
| 2.4 | Rename `charts/rpe-spring-boot-template/` → `charts/apim-marketplace/` | ○ Todo | |
| 2.5 | Update `Chart.yaml` — name, maintainer | ○ Todo | |
| 2.6 | Update `values.yaml` | ○ Todo | See snippet below |
| 2.7 | Add `values.aat.template.yaml` | ○ Todo | Jenkins applies this during staging deploy |
| 2.8 | Add `values.preview.template.yaml` | ○ Todo | Minimal — just `${IMAGE_NAME}` and `${SERVICE_FQDN}` substitutions |

```yaml
# values.yaml
java:
  applicationPort: 8080
  image: 'hmctspublic.azurecr.io/apim/marketplace:latest'
  ingressHost: apim-marketplace-{{ .Values.global.environment }}.service.core-compute-{{ .Values.global.environment }}.internal
  aadIdentityName: apim
  keyVaults:
    apim:
      secrets:
        - name: AppInsightsInstrumentationKey
          alias: azure.application-insights.instrumentation-key
```

```yaml
# values.aat.template.yaml
java:
  image: ${IMAGE_NAME}
  ingressHost: apim-marketplace-staging.aat.platform.hmcts.net
```

### 2d — Terraform

> **Key lesson from last time:** The key vault module `product_group_name` must be the correct AAD group name — NOT `dcd_ccd` (copied incorrectly from a CCD example). Use `"DTS API Marketplace"`.

| # | Step | Status | Notes |
|---|------|--------|-------|
| 2.9 | Update `infrastructure/state.tf` — provider version pins | ○ Todo | Copy from `cnp-plum-recipes-service/infrastructure/state.tf`; add `postgres_network` alias ready for Phase 8 |
| 2.10 | Update `infrastructure/main.tf` — resource group + key vault | ○ Todo | RG name: `apim-shared-${var.env}`. Vault using `cnp-module-key-vault` with `create_managed_identity = true` and correct `product_group_name` |
| 2.11 | Update `infrastructure/variables.tf` | ○ Todo | Add `managed_identity_object_id`, `additional_managed_identities_access`. Set `product` default `"apim"` |
| 2.12 | Populate `aat.tfvars`, `demo.tfvars`, `prod.tfvars` | ○ Todo | Start with aat; demo/prod minimal until those envs are targeted |
| 2.12a | **Adopt existing Azure resources** — `terraform import` before first apply | ○ Todo | Vault `apim-demo`, `apim-aat` and MIs `apim-demo-mi`, `apim-aat-mi` already exist from previous attempt. New pipeline has empty state → Terraform will conflict. Import commands below. |

```bash
# Run locally after configuring backend, before first Jenkins Terraform run
terraform import azurerm_resource_group.rg /subscriptions/1c4f0704-a29e-403d-b719-b90c34ef14c9/resourceGroups/apim-shared-aat
terraform import module.vault.azurerm_key_vault.kv /subscriptions/1c4f0704-a29e-403d-b719-b90c34ef14c9/resourceGroups/apim-shared-aat/providers/Microsoft.KeyVault/vaults/apim-aat
# Repeat for demo env. Purge warning: if vault is ever deleted, Azure soft-deletes for 90 days — must purge before recreating with same name.
```

```hcl
# infrastructure/main.tf (key vault module)
module "vault" {
  source                               = "git@github.com:hmcts/cnp-module-key-vault?ref=master"
  name                                 = "${var.product}-${var.env}"
  product                              = var.product
  env                                  = var.env
  tenant_id                            = var.tenant_id
  object_id                            = var.jenkins_AAD_objectId
  resource_group_name                  = azurerm_resource_group.rg.name
  product_group_name                   = "DTS API Marketplace"
  common_tags                          = local.tags
  managed_identity_object_id           = var.managed_identity_object_id
  create_managed_identity              = true
  additional_managed_identities_access = var.additional_managed_identities_access
  jenkins_object_id                    = data.azurerm_user_assigned_identity.jenkins.principal_id
}
```

### 2e — Application code

| # | Step | Status | Notes |
|---|------|--------|-------|
| 2.13 | Port `OnboardingController`, `CorsConfig`, `Application` from `service-marketplace-onboarding` | ○ Todo | Keep `uk.gov.hmcts.cp` package — CNP doesn't mandate Reform package |
| 2.14 | Update `build.gradle` to CNP-compatible Spring Boot version | ○ Todo | Check `cnp-plum-recipes-service/build.gradle` for current version. The CPP service used Spring Boot 4.1.0 / Java 25 — verify these are acceptable on CNP AKS nodes |

---

## Phase 3 — cnp-jenkins-config PRs (two required)

> **Gotcha:** Jenkins fetches the Terraform approval whitelist by repo name from a raw GitHub URL. 404 = hard fail before any Terraform runs. Merge **before** the first Jenkins run.

| # | Step | Status | PR target |
|---|------|--------|-----------|
| 3.1 | Add `terraform-infra-approvals/service-api-marketplace.json` | ○ Todo | `cnp-jenkins-config` |
| 3.2 | Add to `deployment-controls.yml` | ○ Todo | `cnp-jenkins-config` |
| 3.3 | Add to `environment-approvals.yml` (prod only — when ready) | ○ Later | `cnp-jenkins-config` |

```json
// terraform-infra-approvals/service-api-marketplace.json
{
  "resources": [
    {"type": "azurerm_key_vault_access_policy"}
  ],
  "module_calls": [
    {"source": "git@github.com:hmcts/cnp-module-key-vault"}
  ]
}
```

```yaml
# deployment-controls.yml entry
- repo: https://github.com/hmcts/service-api-marketplace.git
  deployment-enabled: true
```

---

## Phase 4 — cnp-flux-config: verify namespace, add HelmRelease

> **The critical ordering rule:** Flux namespace kustomizations must be merged to master **before** the first Jenkins run. Jenkins checks the namespace exists (`helm install --namespace apim`). If it doesn't exist: `namespaces "apim" not found` and the pipeline fails.
>
> **Also:** Workload Identity wiring (`add-wl-identity.sh`) must happen **after** Terraform creates the Managed Identity.

| # | Step | Status | Notes |
|---|------|--------|-------|
| 4.1 | Verify apim namespace kustomizations are on Flux **master** (not just a branch) | ✅ Done | `apps/apim/base/kustomization.yaml` and env overlays confirmed on master |
| 4.2 | Create `apps/apim/apim-marketplace/apim-marketplace.yaml` (HelmRelease) | ○ Todo | See snippet below |
| 4.3 | Generate image policy files via script | ○ Todo | `cd cnp-flux-config && /opt/homebrew/bin/bash ./bin/v2/add-image-policies.sh apim apim marketplace` — creates `apps/apim/apim-marketplace/image-policy.yaml`, `image-repo.yaml` AND `apps/apim/automation/kustomization.yaml`, AND wires `../../apim/automation` into `apps/flux-system/automation/kustomization.yaml`. **Do NOT create these files manually** — the automation folder must be in flux-system/automation or the CI test `Fluxv2 Image Automation` will fail with `No ImagePolicy for apim-marketplace in clusters/ptl-intsvc/base`. Prereqs: `brew install yq` |
| 4.4 | Add HelmRelease to `apps/apim/base/kustomization.yaml` resources | ○ Todo | |
| 4.5 | Add env-specific patch files (`aat.yaml`, `demo.yaml`, `preview.yaml`) | ○ Todo | Set correct `ingressHost` per env |
| 4.6 | Run Flux dry-run tests locally before raising PR | ○ Todo | `./tests/dry-run-kustomize.sh preview 00` |
| 4.7 | Workload Identity wiring — **only after step 6.1 Terraform runs** | ○ Todo | `/opt/homebrew/bin/bash ./bin/workload-identity/add-wl-identity.sh --namespace apim --mi-name apim-aat` ⚠️ Requires bash4 (`brew install bash`) — macOS ships bash 3.2 which lacks associative arrays |
| 4.7a | Grant MI access to Key Vault and re-enable vault mount in chart | ○ Todo | See note below — `keyVaults` removed from `values.yaml` for initial onboarding; must be re-added once RBAC is set up |

> **Step 4.7a — Re-enabling vault mount after workload identity is set up:**
>
> `keyVaults` was removed from `charts/apim-marketplace/values.yaml` to unblock initial onboarding (pod was stuck in `Init:0/1` — `MountVolume.SetUp failed` for `AppInsightsInstrumentationKey` from `apim-aat.vault.azure.net`).
>
> To re-enable once workload identity is wired:
> 1. Confirm the Managed Identity has **Key Vault Secrets User** RBAC role on `apim-aat` vault (check in Azure Portal or via `az role assignment list`)
> 2. Confirm `AppInsightsInstrumentationKey` secret exists in `apim-aat.vault.azure.net`
> 3. Re-add to `charts/apim-marketplace/values.yaml`:
> ```yaml
> java:
>   aadIdentityName: apim
>   keyVaults:
>     apim:
>       secrets:
>         - name: AppInsightsInstrumentationKey
>           alias: azure.application-insights.instrumentation-key
> ```

```yaml
# apps/apim/apim-marketplace/apim-marketplace.yaml
apiVersion: helm.toolkit.fluxcd.io/v2
kind: HelmRelease
metadata:
  name: apim-marketplace
spec:
  releaseName: apim-marketplace
  values:
    java:
      image: hmctspublic.azurecr.io/apim/marketplace:latest # {"$imagepolicy": "flux-system:apim-marketplace"}
  chart:
    spec:
      chart: ./stable/apim-marketplace
      sourceRef:
        kind: GitRepository
        name: hmcts-charts
        namespace: flux-system
      interval: 1m
```

---

## Phase 5 — Helm chart publication to hmcts-charts

> **Correction from Confluence:** The chart in `charts/apim-marketplace/` is **automatically published to `hmcts/hmcts-charts`** by the Jenkins pipeline after a successful master build. No manual PR to hmcts/charts is needed.
>
> However, Flux requires the chart to exist in `hmcts-charts` before it can deploy — so the first successful Jenkins master build must complete before Flux can reconcile the HelmRelease.

| # | Step | Status | Notes |
|---|------|--------|-------|
| 5.1 | Confirm chart version is bumped in `Chart.yaml` before each release | ○ Todo | Jenkins publishes on version increment |
| 5.2 | After first Jenkins master build: verify chart appears in hmcts-charts `stable/apim-marketplace/` | ○ Todo | Check `github.com/hmcts/hmcts-charts` |

---

## Phase 6 — First Jenkins run

> **Prerequisite checklist — merge all of these before raising the first PR:**
> - [ ] Phase 3.1 merged — `terraform-infra-approvals/service-api-marketplace.json`
> - [ ] Phase 3.2 merged — `deployment-controls.yml` entry
> - [ ] Phase 4.1 confirmed — apim namespace on Flux master ✅
> - [ ] Phase 4.2–4.6 merged — HelmRelease + image policies on Flux master

| # | Step | Status | Notes |
|---|------|--------|-------|
| 6.0 | Get seed job approved and run | ○ Todo | The seed job (`build.hmcts.net/job/Seed%20Job/`) fails with `script not yet approved for use` until a Jenkins admin approves `organisations-beta.groovy` in Manage Jenkins → In-process Script Approval. Raise in `#platops-help` on Slack. Once approved, click **Build Now** on the seed job and wait for it to go green — this bakes `service-api-marketplace` into the org folder's repo allowlist (loaded from `deployment-controls.yml` on `cnp-jenkins-config` master). |
| 6.0a | Trigger Jenkins org scan so the job is discovered | ○ Todo | Go to `build.hmcts.net/job/HMCTS_j_to_z/` → **Scan Organization Now** (left sidebar). The job folder for `service-api-marketplace` will appear but will be empty ("no branches found"). |
| 6.0b | Scan the repository to queue the first build | ○ Todo | Click into `HMCTS_j_to_z/service-api-marketplace` → **Scan Repository Now** (left sidebar). Jenkins will find `Jenkinsfile_CNP` on `master` and queue the build. |
| 6.1 | Raise a PR to trigger first Jenkins build | ○ Todo | Watch at `build.hmcts.net/job/HMCTS_j_to_z/job/service-api-marketplace/` |
| 6.2 | Jenkins stages: Checkout → Build → Test → Docker push to ACR → Terraform → Deploy preview | ○ Todo | Terraform creates `apim-aat-mi` MI and `apim-aat` vault in `DCD-CNP-DEV` subscription |
| 6.3 | After Terraform: run Phase 4.7 workload identity script, raise Flux PR | ○ Todo | |

**Known failure points from last time:**

- **Old ACR registry references:** Jenkins runs `check-old-acr-references.sh` which fails if `hmctspublic` appears in `Chart.yaml` or `values.yaml`. The platform has migrated to `hmctsprod.azurecr.io` for service images. Use `hmctsprod` everywhere — the official `add-image-policies.sh` script still defaults to `hmctspublic` (not yet updated), so override it: `./add-image-policies.sh apim apim marketplace hmctsprod`. Also update the Flux `image-repo.yaml` and `apim-marketplace.yaml` HelmRelease image field to use `hmctsprod`.
- **DNS staging check:** Jenkins checks for CNAME `apim-marketplace-staging` in `aat.platform.hmcts.net` (sub `DTS-CFTPTL-INTSVC`). Created after Flux deploys. Transient on first run — retry after Flux reconciles.
- **Namespace not found:** `namespaces "apim" not found` means Flux namespace PR not merged or not yet reconciled on the cluster.
- **Terraform whitelist 404:** `terraform-infra-approvals/service-api-marketplace.json` missing. Must be merged before this run.
- **Prod approval gate:** Master builds will fail prod stage with "not approved for environment prod". Expected — separate `environment-approvals.yml` PR needed when ready.
- **Vault mount failure (AKS preview deploy stuck in Init:0/1):** `MountVolume.SetUp failed for volume "vault-apim"` — the pod identity doesn't have Key Vault Secrets User RBAC on `apim-aat.vault.azure.net`. Workaround: remove `keyVaults` and `aadIdentityName` from `charts/apim-marketplace/values.yaml` for initial onboarding. Re-add once workload identity RBAC is set up (see step 4.7a).
- **Startup probe failing on port 8080 (app running on 4550):** The CNP java chart probes `/health/liveness` on the `applicationPort` (8080). The Spring Boot template defaults `server.port: 4550` for local dev. In AKS the app must listen on 8080 — set `server.port: 8080` in `application.yaml`. Also ensure `management.endpoints.web.base-path: /` so the probe path `/health/liveness` resolves correctly (default actuator path is `/actuator/health/liveness`).
- **Smoke test NPE with Java 25 (rest-assured Groovy HTTP builder):** `groovyx.net.http.HTTPBuilder` (used internally by rest-assured) throws `NullPointerException` in `ClosureMetaClass.invokeOnDelegationObject` on Java 25 — adding `--add-opens` does not fix it. Solution: replace rest-assured in the smoke test with `java.net.http.HttpClient` (built-in since Java 11) and remove `@SpringBootTest` — smoke tests should hit a deployed URL (`TEST_URL` env var), not start a local Spring context. Default `TEST_URL` to `http://localhost:8080` to match the server port.

---

## Phase 7 — Verify deployment

| # | Step | Status | Command / URL |
|---|------|--------|---------------|
| 7.1 | Preview health check (PR build) | ○ Todo | `https://apim-marketplace-pr-{N}.preview.platform.hmcts.net/health` |
| 7.2 | AAT staging health check (master merge) | ○ Todo | `https://apim-marketplace-staging.aat.platform.hmcts.net/health` |
| 7.3 | Check Flux HelmRelease status | ○ Todo | `az aks get-credentials --resource-group cft-aat-01-rg --name cft-aat-01-aks --subscription DCD-CFTAPPS-STG -a --overwrite-existing` then `kubectl get helmrelease -n apim` |
| 7.4 | Add Jenkins check to branch protection | ○ Todo | Now that Jenkins has run, `continuous-integration/jenkins/pr-merge` name is known — add as required status check (step 1.3) |

---

## Phase 8a — PostgreSQL infrastructure (ticket AMP-944, PR #18 `fix/AMP-944-postgres`)

> **Two-phase approach:** Infrastructure (postgres server + vault secrets) is wired in this PR.
> App-level wiring (datasource, vault mounts) is deferred to Phase 8b when actual JPA code exists.
> This avoids the bootstrap problem where the pod fails to start because vault secrets don't yet exist.
>
> **Bootstrap problem note:** `withPipeline` + `enableAksStagingDeployment()` only runs `terraform plan` for PRs — `terraform apply` fires on master. So vault secrets don't exist until after the PR merges. Mounting them in PRs causes `Init:0/1` (`MountVolume.SetUp failed`). Platops ticket DTSPO-34206 raised to confirm approach.
>
> **Pattern reference:** `cnp-plum-recipes-service` uses a separate shared-infra repo so postgres secrets pre-exist before any PR. We manage infra in the app repo, so Phase 8a must merge first.

| # | Step | Status | Notes |
|---|------|--------|-------|
| 8.1 | Add `terraform-module-postgresql-flexible` to `infrastructure/main.tf` | ✅ Done | Follows `cnp-plum-recipes-service` pattern. Module uses `subnet_suffix = "expanded"` — no need to pass `subnet_id`. `postgres_network` provider alias in `main.tf` only (not `state.tf`) |
| 8.2 | Store DB credentials as vault secrets in terraform | ✅ Done | `marketplace-POSTGRES-USER/PASS/HOST/PORT/DATABASE` written from module outputs to `apim-{env}` vault |
| 8.3 | Update `terraform-infra-approvals/service-api-marketplace.json` | ✅ Done | Added `azurerm_key_vault_secret` resource type and `terraform-module-postgresql-flexible` module. Merged to cnp-jenkins-config master 16h+ ago |
| 8.4 | Add `aks_subscription_id` + `pgsql_sku` to `variables.tf` and `aat.tfvars` | ✅ Done | `aks_subscription_id = "96c274ce-846d-4e48-89a7-d528432298a7"` (safe to commit — already public in this file) |
| 8.5 | Pin postgresql JDBC driver to 42.7.12 in `build.gradle` | ✅ Done | OWASP CVE-2026-54291 + CVE-2026-66299 affect 42.7.4–42.7.11 |
| 8.6 | Add TestContainers for integration tests | ✅ Done | `TestContainersInitialise.java` + `RootIntegrationTest.java` in `integration` package |
| 8.7 | Remove `keyVaults` / `aadIdentityName` from `values.yaml` | ✅ Done | Deferred to Phase 8b — secrets don't exist in vault until master terraform apply runs |
| 8.8 | Remove datasource + `readiness.include: "db"` from `application.yaml` | ✅ Done | Deferred to Phase 8b |

---

## Phase 8b — Wire app to use DB (follow-up PR, after Phase 8a merges)

> **Prerequisite:** Phase 8a must be merged and master build must have run so that:
> - `apim-flexible-data-aat` resource group and postgres server exist
> - `marketplace-POSTGRES-*` secrets exist in `apim-aat` vault

| # | Step | Status | Notes |
|---|------|--------|-------|
| 8b.1 | Re-add `aadIdentityName: apim` + `keyVaults` to `values.yaml` | ○ Todo | Mount `marketplace-POSTGRES-USER/PASS/HOST/PORT/DATABASE` from `apim` vault |
| 8b.2 | Re-add datasource + flyway config to `application.yaml` | ○ Todo | `spring.config.import: optional:configtree:/mnt/secrets/apim/`, datasource using `${POSTGRES_*}` env vars |
| 8b.3 | Re-add `readiness.include: "db"` to `application.yaml` | ○ Todo | Only once DB connectivity is verified in AAT |
| 8b.4 | Write first Flyway migration `V1__init.sql` | ○ Todo | Enable `spring.flyway.enabled: true` once migration is written |
| 8b.5 | Add JPA entities and repositories | ○ Todo | First actual DB code |
| 8b.6 | Integration tests via TestContainers | ○ Todo | TestContainers already wired — add test cases against real schema |

---

## Reference: Azure Subscriptions

| Subscription | ID | Used for |
|---|---|---|
| DCD-CNP-DEV | `1c4f0704-a29e-403d-b719-b90c34ef14c9` | Terraform creates MIs, RGs, vaults (non-prod) |
| DCD-CNP-QA | `7a4e3bd5-ae3a-4d0c-b441-2188fee3ff1c` | |
| DCD-CNP-Prod | `8999dec3-0104-4a27-94ee-6588559729d1` | ACR (`hmctspublic`), prod infra |
| DCD-CFTAPPS-STG | `96c274ce-846d-4e48-89a7-d528432298a7` | AAT AKS cluster (`cft-aat-01-aks`) |
| DTS-CFTPTL-INTSVC | `1baf5470-1c3e-40d3-a6f7-74bfbce4b348` | Jenkins infra, DNS records |
