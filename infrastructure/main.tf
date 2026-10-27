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

# Step one of two. The vault is named amp-aat in state and amp-marketplace-{env} in config, and
# a key vault rename is a replace: terraform destroys the old one first, which the CanNotDelete
# lock on the resource group refuses. Nobody on the team holds the permission to lift that lock,
# nor to write this state by hand.
#
# So terraform is told to forget the vault instead of destroying it. destroy = false leaves the
# resource in Azure, untouched and unmanaged. The module and the secrets that live in it come
# back under the new name in the follow-up, which creates them fresh.
#
# The old vault is empty - its secrets were never applied, because the apply fails at the destroy
# before reaching them - so nothing is stranded in it. Remove this block once it has applied; a
# removed block and the module it names cannot both exist.
removed {
  from = module.vault

  lifecycle {
    destroy = false
  }
}
