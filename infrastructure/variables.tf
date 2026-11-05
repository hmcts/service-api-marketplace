variable "product" {
  default = "amp"
}

variable "component" {}

variable "location" {
  default = "UK South"
}

variable "env" {}

variable "subscription" {}

variable "common_tags" {
  type = map(string)
}

variable "tenant_id" {}

variable "jenkins_AAD_objectId" {}

variable "team_contact" {
  default = "#api-marketplace-tech"
}

variable "managed_identity_object_id" {
  default = ""
}

variable "additional_managed_identities_access" {
  type    = list(string)
  default = []
}

variable "aks_subscription_id" {}

variable "pgsql_sku" {
  default = "GP_Standard_D2s_v3"
}

variable "pgsql_subnet_suffix" {
  default = null
}

variable "pgsql_public_access" {
  default = false
}

variable "superseded_vault_id" {
  default = ""
}

variable "vault_name" {
  default = ""
}

# The SPS platform's subscription holding sps-api-mgmt-{env}, which the marketplace calls to issue
# subscription keys. Not aks_subscription_id: the instance belongs to another team and sits in a
# different subscription in every environment.
variable "apim_subscription_id" {
  default = ""
}

variable "apim_resource_group" {
  default = ""
}

variable "apim_service_name" {
  default = ""
}

# Whichever amp repo applies first in an environment creates the product-wide identity; the rest
# read it. sandbox, aat and demo were created by amp-try-it-now, so this stays false there. The
# module creates it when true, and attaches its own access policy with count rather than the
# for_each that cannot take a principal id created in the same apply.
variable "create_managed_identity" {
  default = false
}
