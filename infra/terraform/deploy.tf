# Deploy role for GitHub Actions (KAN-105): the deploy workflow gets short-lived credentials through GitHub OIDC, so no
# AWS keys are stored in GitHub. Only runs on this repository's main branch can assume it, and it can do one thing:
# run AWS-RunShellScript on the app instance through SSM and read the result.

locals {
  github_oidc_host = "token.actions.githubusercontent.com"
  github_oidc_provider_arn = (
    var.create_github_oidc_provider
    ? aws_iam_openid_connect_provider.github[0].arn
    : "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/${local.github_oidc_host}"
  )
}

# One per account and URL: if the account already has it (another project), set create_github_oidc_provider = false
resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url            = "https://${local.github_oidc_host}"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_policy_document" "deploy_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Pushes to main and manual runs (rollbacks) started on main; not PRs, other branches or forks
    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "deploy" {
  name                 = "${var.project}-github-deploy"
  assume_role_policy   = data.aws_iam_policy_document.deploy_assume.json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "deploy" {
  # SendCommand needs both the document and the target; anything else (another document, another instance) is denied
  statement {
    sid     = "RunShellScriptOnTheAppInstance"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:${data.aws_partition.current.partition}:ssm:${var.region}::document/AWS-RunShellScript",
      aws_instance.app.arn,
    ]
  }

  # These actions support no resource-level restriction; they only read command results
  statement {
    sid       = "ReadCommandResults"
    actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "${var.project}-github-deploy"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}
