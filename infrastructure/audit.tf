# See docs/AUDIT.md.
locals {
  audit_stream = "Custom-AmpAudit"

  audit_columns = {
    TimeGenerated = "datetime"
    Action        = "string"
    Outcome       = "string"
    Status        = "int"
    ActorOid      = "string"
    Target        = "string"
    Endpoint      = "string"
    Api           = "string"
    CorrelationId = "string"
    Environment   = "string"
  }

  amp_identity_principal_id = var.create_managed_identity ? module.vault.managed_identity_objectid[0] : data.azurerm_user_assigned_identity.amp[0].principal_id
}

resource "azurerm_log_analytics_workspace_table_custom_log" "audit" {
  name                    = "AmpAudit_CL"
  workspace_id            = azurerm_log_analytics_workspace.this.id
  description             = "One row per audited consumer or admin action in the API Marketplace service."
  retention_in_days       = var.log_analytics_retention_days
  total_retention_in_days = var.audit_retention_days

  dynamic "column" {
    # The table spells it dateTime, the rule datetime.
    for_each = local.audit_columns
    content {
      name = column.key
      type = column.value == "datetime" ? "dateTime" : column.value
    }
  }
}

# Direct: the rule has its own ingestion endpoint, so no data collection endpoint is needed.
resource "azurerm_monitor_data_collection_rule" "audit" {
  name                = "${var.product}-${var.component}-audit-${var.env}"
  location            = var.location
  resource_group_name = azurerm_resource_group.rg.name
  kind                = "Direct"
  tags                = local.tags

  stream_declaration {
    stream_name = local.audit_stream
    dynamic "column" {
      for_each = local.audit_columns
      content {
        name = column.key
        type = column.value
      }
    }
  }

  destinations {
    log_analytics {
      name                  = "audit"
      workspace_resource_id = azurerm_log_analytics_workspace.this.id
    }
  }

  data_flow {
    streams       = [local.audit_stream]
    destinations  = ["audit"]
    output_stream = "Custom-${azurerm_log_analytics_workspace_table_custom_log.audit.name}"
    transform_kql = "source"
  }
}

# Off until the Jenkins identity may grant Monitoring Metrics Publisher: it is not yet in
# rbac_admin_roles in hmcts/cft-jenkins-infrastructure (jenkins-agent-identities).
resource "azurerm_role_assignment" "audit_publisher" {
  count = var.grant_audit_publisher ? 1 : 0

  scope                = azurerm_monitor_data_collection_rule.audit.id
  role_definition_name = "Monitoring Metrics Publisher"
  principal_id         = local.amp_identity_principal_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_key_vault_secret" "audit_ingestion_endpoint" {
  name         = "audit-ingestion-endpoint"
  value        = azurerm_monitor_data_collection_rule.audit.logs_ingestion_endpoint
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}

resource "azurerm_key_vault_secret" "audit_ingestion_rule_id" {
  name         = "audit-ingestion-rule-id"
  value        = azurerm_monitor_data_collection_rule.audit.immutable_id
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}
