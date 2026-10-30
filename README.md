# Spring Boot application template

## Purpose

The purpose of this template is to speed up the creation of new Spring applications within HMCTS
and help keep the same standards across multiple teams. If you need to create a new app, you can
simply use this one as a starting point and build on top of it.

## What's inside

The template is a working application with a minimal setup. It contains:
 * application skeleton
 * setup script to prepare project
 * common plugins and libraries
 * [HMCTS Java plugin](https://github.com/hmcts/gradle-java-plugin)
 * docker setup
 * automatically publishes API documentation to [hmcts/cnp-api-docs](https://github.com/hmcts/cnp-api-docs)
 * code quality tools already set up
 * MIT license and contribution information
 * Helm chart using chart-java.

The application exposes health endpoint (http://localhost:4550/health) and metrics endpoint
(http://localhost:4550/metrics).

## Plugins

The template contains the following plugins:

  * HMCTS Java plugin

    Applies code analysis tools with HMCTS default settings. See the [project repository](https://github.com/hmcts/gradle-java-plugin) for details.

    Analysis tools include:

    * checkstyle

        https://docs.gradle.org/current/userguide/checkstyle_plugin.html

        Performs code style checks on Java source files using Checkstyle and generates reports from these checks.
        The checks are included in gradle's *check* task (you can run them by executing `./gradlew check` command).

    * org.owasp.dependencycheck

        https://jeremylong.github.io/DependencyCheck/dependency-check-gradle/index.html

        Provides monitoring of the project's dependent libraries and creating a report
        of known vulnerable components that are included in the build. To run it
        execute `gradle dependencyCheck` command.

  * jacoco

    https://docs.gradle.org/current/userguide/jacoco_plugin.html

    Provides code coverage metrics for Java code via integration with JaCoCo.
    You can create the report by running the following command:

    ```bash
      ./gradlew jacocoTestReport
    ```

    The report will be created in build/reports subdirectory in your project directory.

  * io.spring.dependency-management

    https://github.com/spring-gradle-plugins/dependency-management-plugin

    Provides Maven-like dependency management. Allows you to declare dependency management
    using `dependency 'groupId:artifactId:version'`
    or `dependency group:'group', name:'name', version:version'`.

  * org.springframework.boot

    http://projects.spring.io/spring-boot/

    Reduces the amount of work needed to create a Spring application


  * com.github.ben-manes.versions

    https://github.com/ben-manes/gradle-versions-plugin

    Provides a task to determine which dependencies have updates. Usage:

    ```bash
      ./gradlew dependencyUpdates -Drevision=release
    ```

## Setup

Located in `./bin/init.sh`. Simply run and follow the explanation how to execute it.

## Building and deploying the application

### Building the application

The project uses [Gradle](https://gradle.org) as a build tool. It already contains
`./gradlew` wrapper script, so there's no need to install gradle.

To build the project execute the following command:

```bash
  ./gradlew build
```

### Running locally

Start Postgres from `docker-compose.yml`, which creates the `marketplace` database on
`localhost:5432`, then run the service on port 8080:

```bash
  docker compose up -d db
  ENTRA_BYPASS=true ./gradlew bootRun
  curl http://localhost:8080/api/health
```

Port 5432 must be free: another Postgres container on it gives `database "marketplace" does not exist`.

`ENTRA_BYPASS=true` (`local-bypass.enabled`, see `LocalBypassConfig`) is for local use only. It
replaces Entra application registration and APIM subscription keys with in-memory stand-ins, so
the whole journey (register an application, grant a role, subscribe to an API, read the key,
delete) works without `ENTRA_ONBOARDING_CLIENT_SECRET` or an Azure login. The client ids, secrets
and keys it hands out are made up and do not work against the real gateway, and the startup log
says `ENTRA_BYPASS is on`. It is off by default and is never set in a chart. Without it, registering an
application answers 503 locally until the onboarding secret is exported, and subscribing needs an
Azure login with a role on the sandbox APIM instance.

### Running in Docker

`docker-compose.yml` runs the application in a container, as it runs in AKS, next to Postgres.
You need Docker Desktop running, Java 25, and the Azure CLI signed in (`az login`).

1. Log in to the HMCTS registry, which holds the base image. The login lasts about 3 hours;
   without it the build fails with 401 Unauthorized from `hmctsprod.azurecr.io`.

   ```bash
     az acr login --name hmctsprod
   ```

2. Build the jar. The image copies it from `build/libs`.

   ```bash
     ./gradlew assemble
   ```

3. Build the image and start Postgres and the API. The API starts once Postgres is healthy and
   is ready when the log says `Started Application`.

   ```bash
     docker compose up --build
   ```

The API runs on `localhost:8080` with `ENTRA_BYPASS=true` (see above). To run the smoke and
functional tests against it, from a second terminal:

```bash
  ./gradlew smoke functional
```

After a code change, run `./gradlew assemble` and `docker compose up --build` again, or
`./bin/run-in-docker.sh -i` to do both (`-h` for its options).

To stop it:

```bash
  docker compose down
```

| Problem | Fix |
| --- | --- |
| `401 Unauthorized` from `hmctsprod.azurecr.io` | Run `az acr login --name hmctsprod` again |
| `amp-marketplace.jar` not found during the build | Run `./gradlew assemble` first |
| Port 5432 already allocated | Stop the other Postgres (another compose stack, or `brew services stop postgresql`) |
| Port 8080 already allocated | Stop whatever else is on 8080, such as a `./gradlew bootRun` |

### Checking it is up

```bash
  curl http://localhost:8080/api/health   # {"status":"ok"}
  curl http://localhost:8080/health       # Spring Boot actuator, including the database
```

To start again from an empty database, remove its volume. This deletes all local data:

```bash
  docker compose down -v && docker compose up -d db
```

## AAT Deployment

The service is deployed to the `apim` namespace on `cft-aat-00-aks` via Flux (`cnp-flux-config`).

### Infrastructure

Terraform provisions a PostgreSQL Flexible Server and Key Vault in the CFT AAT subscription.
The pipeline picks this up automatically from `infrastructure/aat.tfvars`.
The Key Vault is named `apim-marketplace-aat` and holds the postgres credentials as secrets.

### Workload identity

The managed identity client-id is wired into the cluster via
`apps/apim/serviceaccount/aat.yaml` in `cnp-flux-config` (client-id: `579ca499-1b6c-4ebf-befa-0ce7a5173265`).
Pods in the `apim` namespace inherit this via the `azure.workload.identity/use: "true"` label.

### Ingress

The internal ingress host is:
`apim-marketplace-aat.service.core-compute-aat.internal`

## Sandbox (sbox) Deployment

The service is deployed to the `apim` namespace on `cft-sbox-00-aks` via Flux (`cnp-flux-config`).
It is fronted by `sps-api-mgmt-sbox` (SPS APIM sbox) in the DTS-SPS-SBOX subscription.

### Infrastructure

Terraform provisions a PostgreSQL Flexible Server and Key Vault in the DCD-CFT-Sandbox subscription.
The pipeline picks this up automatically from `infrastructure/sandbox.tfvars`.

After the pipeline runs for the first time in sandbox, collect the managed identity client-id:

```bash
az identity list --subscription bf308a5c-0624-4334-8ff8-8dca9fd43783 \
  --query "[?contains(name, 'apim-marketplace')].{name:name, clientId:clientId}" -o table
```

### Wiring workload identity (post-terraform)

Create `apps/apim/serviceaccount/sandbox.yaml` in `cnp-flux-config` with the client-id above:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: ${NAMESPACE}
  namespace: ${NAMESPACE}
  annotations:
    azure.workload.identity/client-id: "<client-id-from-terraform>"
```

Then add it as a patch in `apps/apim/sbox/base/kustomization.yaml` and remove the
`global.enableKeyVaults: false` override from `apps/apim/apim-marketplace/sbox.yaml`.
--Let me test--
### Ingress

The internal ingress host is:
`apim-marketplace-sandbox.service.core-compute-sandbox.internal`

Register this as a backend in `sps-api-mgmt-sbox` to route traffic from SPS APIM.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
