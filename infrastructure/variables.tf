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
