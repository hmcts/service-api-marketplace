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

# Consumed, not declared. Under apim this repo created the product's shared resource group and
# Key Vault. Under amp they already exist - try-cp-crime-schedulingandlisting-courtschedule was
# amp's first component and created them there (commit fdf7b37), which says in terms that this
# service must consume them when it migrates. Two repositories declaring one vault fight over its
# state, and the second to apply wins by destroying what the first made.
data "azurerm_resource_group" "rg" {
  name = "${var.product}-shared-${var.env}"
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
  key_vault_id = data.azurerm_key_vault.vault.id
}

resource "azurerm_key_vault_secret" "postgres_pass" {
  name         = "marketplace-POSTGRES-PASS"
  value        = module.postgresql_flexible.password
  key_vault_id = data.azurerm_key_vault.vault.id
}

resource "azurerm_key_vault_secret" "postgres_host" {
  name         = "marketplace-POSTGRES-HOST"
  value        = module.postgresql_flexible.fqdn
  key_vault_id = data.azurerm_key_vault.vault.id
}

resource "azurerm_key_vault_secret" "postgres_port" {
  name         = "marketplace-POSTGRES-PORT"
  value        = "5432"
  key_vault_id = data.azurerm_key_vault.vault.id
}

resource "azurerm_key_vault_secret" "postgres_database" {
  name         = "marketplace-POSTGRES-DATABASE"
  value        = "marketplace"
  key_vault_id = data.azurerm_key_vault.vault.id
}

data "azurerm_key_vault" "vault" {
  name                = var.vault_name != "" ? var.vault_name : "${var.product}-${var.env}"
  resource_group_name = data.azurerm_resource_group.rg.name
}
