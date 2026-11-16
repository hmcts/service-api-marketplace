-- sbox only: slc's sandbox is now a product on the real slc API (amp-try-it-now apim
-- sandbox-products.tf), so keys are issued on that product and calls go to the real path, amp/slc.
-- Every other environment keeps its row as it is.
update marketplace_api
   set product_id            = 'cp-crime-schedulingandlisting-sandbox',
       try_it_now_product_id = 'cp-crime-schedulingandlisting-sandbox',
       gateway_path          = 'amp/slc'
 where short_code = 'slc'
   and '${environment_name}' = 'SBOX';
