--  DDL for View TIES_GOOGLE_AGENCY_VW
--------------------------------------------------------

  CREATE OR REPLACE FORCE EDITIONABLE VIEW "TIES"."TIES_GOOGLE_AGENCY_VW" ("AGENCY_ID", "AGENCY_NAME", "AGENCY_URL", "AGENCY_TIMEZONE", "AGENCY_LANG", "AGENCY_PHONE") AS 
  SELECT 'RTD' agency_id, 'Regional Transportation District' agency_name,
          'http://rtd-denver.com' agency_url,
          'America/Denver' agency_timezone, 'en' agency_lang,
          '303-299-6000' agency_phone
     FROM DUAL
--   UNION
--   SELECT 'CDOT' agency_id, 'Colorado Department of Transportation' agency_name,
--          'https://www.ridebustang.com/' agency_url,
--          'America/Denver' agency_timezone, 'en' agency_lang,
--          '800-900-3011' agency_phone
--     FROM DUAL
;
  GRANT SELECT ON "TIES"."TIES_GOOGLE_AGENCY_VW" TO "TIES_DS";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_AGENCY_VW" TO "TIES_READ";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_AGENCY_VW" TO "TIES_WEB";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_AGENCY_VW" TO "TIES_READ_ROLE";
