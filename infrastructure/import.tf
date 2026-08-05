# The creator_access_policy was created by an earlier pipeline and exists in Azure
# but not in Terraform state. This import block reconciles that on the next apply.
# Remove this file once the pipeline has run successfully.
import {
  to = module.vault.azurerm_key_vault_access_policy.creator_access_policy[0]
  id = "/subscriptions/${data.azurerm_client_config.current.subscription_id}/resourceGroups/${var.product}-shared-${var.env}/providers/Microsoft.KeyVault/vaults/${var.product}-${var.env}/objectId/${data.azurerm_user_assigned_identity.jenkins.principal_id}"
}
