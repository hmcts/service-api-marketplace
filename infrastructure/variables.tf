variable "product" {
  default = "apim"
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
