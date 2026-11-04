-- domain, platform and the two links are published here rather than derived from the repository
-- name, so an API named outside the convention is still filed and linked correctly
alter table marketplace_api add column team        text;
alter table marketplace_api add column domain      text;
alter table marketplace_api add column platform    text;
alter table marketplace_api add column docs_url    text;
alter table marketplace_api add column repo_url    text;
alter table marketplace_api add column listed      boolean not null default true;
-- The API as APIM names it, which is none of short_code, catalogue_name or product_id. The
-- developer portal is keyed on it, and it names the real API rather than the Try It Now one
alter table marketplace_api add column apim_api_name text;

update marketplace_api
   set team        = 'api-marketplace',
       domain      = 'Scheduling and listing',
       platform    = 'Common Platform',
       docs_url    = 'https://hmcts.github.io/api-cp-crime-schedulingandlisting-courtschedule/',
       repo_url    = 'https://github.com/hmcts/api-cp-crime-schedulingandlisting-courtschedule',
       apim_api_name = 'common-platform-api-scheduling-and-listing-court-schedule'
 where short_code = 'slc';

update marketplace_api
   set team        = 'api-marketplace',
       domain      = 'Reference data',
       platform    = 'Common Platform',
       docs_url    = 'https://hmcts.github.io/api-cp-refdata-courthearing-courthouses/',
       repo_url    = 'https://github.com/hmcts/api-cp-refdata-courthearing-courthouses',
       apim_api_name = 'common-platform-api-refdata-court-hearing-court-houses'
 where short_code = 'rcc';

alter table marketplace_api alter column domain   set not null;
alter table marketplace_api alter column platform set not null;
