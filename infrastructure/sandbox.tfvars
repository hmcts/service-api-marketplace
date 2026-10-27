aks_subscription_id = "bf308a5c-0624-4334-8ff8-8dca9fd43783"
pgsql_sku           = "B_Standard_B1ms"
pgsql_public_access = true
# sbox, not sandbox: the resource group is amp-marketplace-sandbox but the vault abbreviates,
# as amp-try-slc-sbox and amp-try-it-now-sbox do.
vault_name          = "amp-marketplace-sbox"

apim_subscription_id = "bd2864ed-4f3e-45ed-9c6a-8d179674bab1" # DTS-SPS-SBOX
apim_resource_group  = "rg-sps-platform-sbox"
apim_service_name    = "sps-api-mgmt-sbox"
