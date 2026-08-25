provider "azurerm" {
  features {}
}

provider "azurerm" {
  features {}
  skip_provider_registration = true
  alias                      = "postgres_network"
  subscription_id            = var.aks_subscription_id
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

resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-shared-${var.env}"
  location = var.location
  tags     = local.tags
}


module "postgresql_flexible" {
  providers = {
    azurerm.postgres_network = azurerm.postgres_network
  }

  source        = "git@github.com:hmcts/terraform-module-postgresql-flexible?ref=master"
  env           = var.env
  product       = var.product
  name          = "${var.product}-flexible"
  component     = var.component
  business_area = "CFT"
  location      = var.location
  subnet_suffix = var.pgsql_subnet_suffix
  public_access = var.pgsql_public_access

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
}

resource "azurerm_key_vault_secret" "postgres_pass" {
  name         = "marketplace-POSTGRES-PASS"
  value        = module.postgresql_flexible.password
  key_vault_id = module.vault.key_vault_id
}

resource "azurerm_key_vault_secret" "postgres_host" {
  name         = "marketplace-POSTGRES-HOST"
  value        = module.postgresql_flexible.fqdn
  key_vault_id = module.vault.key_vault_id
}

resource "azurerm_key_vault_secret" "postgres_port" {
  name         = "marketplace-POSTGRES-PORT"
  value        = "5432"
  key_vault_id = module.vault.key_vault_id
}

resource "azurerm_key_vault_secret" "postgres_database" {
  name         = "marketplace-POSTGRES-DATABASE"
  value        = "marketplace"
  key_vault_id = module.vault.key_vault_id
}

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
