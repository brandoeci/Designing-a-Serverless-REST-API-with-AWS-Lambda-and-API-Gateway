# Scripts

Everything needed to go from a clean checkout to two working APIs. Each script is independent and
idempotent: running it twice updates instead of duplicating.

| Script | What it does | Needs AWS credentials |
| --- | --- | --- |
| `build.sh` | `mvn clean package` and shows the artifact | no |
| `deploy-lambdas.sh` | creates/updates the five functions from the same JAR | yes |
| `invoke-lambdas.sh` | invokes the functions directly with JSON (Part II) | yes |
| `deploy-api.sh` | imports both REST APIs from `infra/*.yaml`, deploys the stage, grants invoke permission | yes |
| `smoke-test.sh` | calls a deployed API over HTTP and checks 200/201/204/400/404 | yes |
| `teardown.sh` | deletes both APIs and the five functions | yes |
| `config.sh` | shared configuration, sourced by the others | — |

## Order

```bash
scripts/build.sh
scripts/deploy-lambdas.sh
scripts/invoke-lambdas.sh          # Part II: Lambda alone, no HTTP
scripts/deploy-api.sh              # Parts III-V: both architectures
scripts/smoke-test.sh proxy        # Architecture B
scripts/smoke-test.sh custom       # Architecture A
scripts/teardown.sh
```

## On Windows

Run them from **Git Bash**, not from PowerShell or `cmd`:

```bash
cd /c/path/to/Designing-a-Serverless-REST-API-with-AWS-Lambda-and-API-Gateway
bash scripts/build.sh
```

`mvn`, `aws`, `curl` and `java` must be on the `PATH`. Paths passed to the AWS CLI are kept
relative on purpose, because a Windows `aws.exe` does not understand a `/c/...` MSYS path.

## AWS Academy / Learner Lab

* Credentials expire after a few hours. When a script reports `ExpiredToken`, open the lab, click
  **AWS Details → AWS CLI** and paste the block into `~/.aws/credentials`.
* The lab does not allow creating IAM roles, so the scripts use the pre-existing `LabRole`. On a
  personal account, export your own role instead:

  ```bash
  export EXECUTION_ROLE_ARN=arn:aws:iam::123456789012:role/my-lambda-role
  ```

* Everything else is configurable the same way:

  ```bash
  REGION=us-west-2 STAGE=test scripts/deploy-api.sh proxy
  ```
