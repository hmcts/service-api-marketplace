-- The marketplace serves the Try It Now APIs, which have their own APIM products and gateway
-- paths. try_it_now_product_id is left in place until the deployed build stops reading it
update marketplace_api
   set product_id   = 'try-it-now-cp-crime-schedulingandlisting',
       gateway_path = 'amp/try/slc'
 where short_code = 'slc';

update marketplace_api
   set product_id   = 'try-it-now-cp-refdata-courthearing',
       gateway_path = 'amp/try/rcc'
 where short_code = 'rcc';
