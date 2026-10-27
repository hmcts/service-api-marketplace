provider "azurerm" {
  features {}
}

provider "azurerm" {
  features {}
  resource_provider_registrations = "none"
  alias                           = "postgres_network"
  subscription_id                 = var.aks_subscription_id
}

data "azurerm_client_config" "current" {}

data "azurerm_user_assigned_identity" "jenkins" {
  name                = "jenkins-${var.env == "sandbox" ? "sbox" : var.env}-mi"
  resource_group_name = "managed-identities-${var.env}-rg"
}

locals {
  tags = merge(
    var.common_tags,
    tomap({ "Team Contact" = var.team_contact })
  )
}

# Product-wide identity created by try-slc; this repo must not create it.
data "azurerm_user_assigned_identity" "amp" {
  name                = "${var.product}-${var.env}-mi"
  resource_group_name = "managed-identities-${var.env}-rg"
}

# Component-scoped so it does not clash with other amp repos.
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = var.location
  tags     = local.tags
}

module "postgresql_flexible" {
  providers = {
    azurerm.postgres_network = azurerm.postgres_network
  }

  source                    = "git@github.com:hmcts/terraform-module-postgresql-flexible?ref=master"
  env                       = var.env
  product                   = var.product
  name                      = "${var.product}-flexible"
  component                 = var.component
  business_area             = "CFT"
  location                  = var.location
  subnet_suffix             = var.pgsql_subnet_suffix
  public_access             = var.pgsql_public_access
  pgsql_delegated_subnet_id = var.pgsql_public_access ? "bypass" : ""

  common_tags          = local.tags
  admin_user_object_id = var.jenkins_AAD_objectId

  pgsql_databases = [
    { name : "marketplace" }
  ]

  pgsql_version = "16"
  pgsql_sku     = var.pgsql_sku
}

resource "azurerm_key_vault_secret" "postgres_user" {
  name         = "marketplace-POSTGRES-USER"
  value        = module.postgresql_flexible.username
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}

resource "azurerm_key_vault_secret" "postgres_pass" {
  name         = "marketplace-POSTGRES-PASS"
  value        = module.postgresql_flexible.password
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}

resource "azurerm_key_vault_secret" "postgres_host" {
  name         = "marketplace-POSTGRES-HOST"
  value        = module.postgresql_flexible.fqdn
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}

resource "azurerm_key_vault_secret" "postgres_port" {
  name         = "marketplace-POSTGRES-PORT"
  value        = "5432"
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}

resource "azurerm_key_vault_secret" "postgres_database" {
  name         = "marketplace-POSTGRES-DATABASE"
  value        = "marketplace"
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}

# AAT HAS AN ORPHANED amp-aat VAULT, in this same resource group, left behind by this rename.
#
# amp-aat was created by the apply before the rename and held the five secrets below. Key Vault
# names are ForceNew, so pointing at amp-marketplace-aat meant destroying it - and the destroy
# failed on the CanNotDelete lock on amp-marketplace-aat:
#
#   ScopeLocked: ... cannot perform delete operation because following scope(s) are locked
#
# Rather than lift the lock mid-pipeline, module.vault and the five secrets were removed from the
# aat state so terraform created the new vault alongside and stopped managing the old one. Delete
# amp-aat once amp-marketplace-aat is serving; it needs the lock lifted, and nothing references it.
# sbox, demo and preview never had amp-{env}, so only aat is affected.

# amp-marketplace-{env}, matching the resource group above and amp-try-slc-{env} /
# amp-try-it-now-{env} alongside it. Component-scoped rather than product-scoped: a vault named
# "${var.product}-${var.env}" would be the same name in every amp repo, so the first to apply
# creates it and every other repo's apply fails on a name that already exists. One vault per
# component means each repo owns exactly what it creates.
#
# web-api-marketplace consumes THIS vault rather than declaring its own - its redis and session
# secrets live here, and its vault_resource_group already points at this resource group.
module "vault" {
  source                               = "git@github.com:hmcts/cnp-module-key-vault?ref=master"
  name                                 = var.vault_name != "" ? var.vault_name : "${var.product}-${var.component}-${var.env}"
  product                              = var.product
  env                                  = var.env
  tenant_id                            = var.tenant_id
  object_id                            = var.jenkins_AAD_objectId
  resource_group_name                  = azurerm_resource_group.rg.name
  product_group_name                   = "DTS API Marketplace"
  common_tags                          = local.tags
  managed_identity_object_id           = data.azurerm_user_assigned_identity.amp.principal_id
  create_managed_identity              = false
  additional_managed_identities_access = var.additional_managed_identities_access
  jenkins_object_id                    = data.azurerm_user_assigned_identity.jenkins.principal_id
}
