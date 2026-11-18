update marketplace_user
   set role = 'ADMIN'
 where entra_object_id in (
    '9ff246b4-11c4-47bf-824f-fc0a31db789a',
    '0a0ce095-786a-4917-8237-5109bade01c6',
    '661ff81d-da08-44ce-9916-6abf0f6482ab',
    '3a8aac03-5134-46d7-a87b-6d33e33aa28b',
    '47787cdd-9942-4266-845c-1841c5dc5929'
 );