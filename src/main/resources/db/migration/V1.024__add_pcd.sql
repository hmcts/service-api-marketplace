insert into marketplace_api (short_code, title, catalogue_name, product_id, try_it_now_product_id, gateway_path, role,
                             team, domain, platform, docs_url, repo_url, listed, apim_api_name)
  values
    ('pcd', 'Crime Prosecution Case Details', 'api-cp-crime-prosecution-case-details',
     'try-it-now-cp-crime-prosecution-case-details', 'try-it-now-cp-crime-prosecution-case-details', 'amp/try/pcd', 'app.read',
     'api-marketplace', 'Case administration', 'Common Platform',
     'https://hmcts.github.io/api-cp-crime-prosecution-case-details/',
     'https://github.com/hmcts/api-cp-crime-prosecution-case-details',
     true, 'crime-prosecution-case-details');
