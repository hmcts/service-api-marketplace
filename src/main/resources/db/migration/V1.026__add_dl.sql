insert into marketplace_api (short_code, title, catalogue_name, product_id, try_it_now_product_id, gateway_path, role,
                             team, domain, platform, docs_url, repo_url, listed, apim_api_name)
  values
    ('dl', 'Crime Defendant Details', 'api-cp-crime-defendant-details',
     'try-it-now-cp-crime-defendant-details', 'try-it-now-cp-crime-defendant-details', 'amp/try/dl', 'app.read',
     'api-marketplace', 'Case administration', 'Common Platform',
     'https://hmcts.github.io/api-cp-crime-defendant-details/',
     'https://github.com/hmcts/api-cp-crime-defendant-details',
     true, 'crime-defendant-details-api');
