--  DDL for View TIES_GOOGLE_ROUTES_VW
--------------------------------------------------------

  CREATE OR REPLACE FORCE EDITIONABLE VIEW "TIES"."TIES_GOOGLE_ROUTES_VW" ("ROUTE_ID", "AGENCY_ID", "ROUTE_SHORT_NAME", "ROUTE_LONG_NAME", "ROUTE_DESC", "ROUTE_TYPE", "ROUTE_URL", "ROUTE_COLOR", "ROUTE_TEXT_COLOR", "PARENT_ROUTE_ID", "ROUTE_CLASSIFICATION_NAME", "ROUTE_CLASSIFICATION_ID", "ROUTE_SORT_ORDER", "NETWORK_ID") AS 
  WITH RB
        AS (SELECT TGR.RUNBOARD_ID
              FROM ties_google_runboard TGR
            UNION
            --SELECT TGSR.RUNBOARD_ID
            --  FROM TIES_GOOGLE_SPECIAL_RB_VW TGSR)
            SELECT runboard_id
              FROM (  SELECT MAX (trb.runboard_id) AS runboard_id,
                             trb.runboard_signid
                        FROM ties_runboards trb
                       WHERE     trb.runboard_type = 'SPECIAL'
                             AND trb.runboard_status = 'PRODUCTION'
                             AND   effective_timestamp >= -- 20250718  prevent specials for current from being included in the future
                                          (SELECT effective_timestamp
                                             FROM ties_google_runboard
                                                  g
                                                  JOIN
                                                  ties_runboards rb
                                                     ON rb.runboard_id =
                                                           g.runboard_id)
                                 AND termination_timestamp <=
                                          (SELECT termination_timestamp
                                             FROM ties_google_runboard
                                                  g
                                                  JOIN
                                                  ties_runboards rb
                                                     ON rb.runboard_id =
                                                           g.runboard_id)
                                 AND TERMINATION_TIMESTAMP >= SYSDATE  
                             -- INC0143557 20250410  begin
                             --AND effective_timestamp >= TRUNC (SYSDATE)
                             --AND trb.regular_runboard_id IN
                             --       (SELECT runboard_id
                             --          FROM TIES.TIES_GOOGLE_RUNBOARD)
--                             AND (   TRUNC (SYSDATE) BETWEEN effective_timestamp
--                                                         AND termination_timestamp
--                                  OR (    effective_timestamp >= SYSDATE
--                                      AND effective_timestamp >= -- 20250718  prevent specials for current from being included in the future
--                                             (SELECT effective_timestamp
--                                                FROM ties_google_runboard g
--                                                     JOIN ties_runboards rb
--                                                        ON rb.runboard_id =
--                                                              g.runboard_id)
--                                      AND termination_timestamp <=
--                                             (SELECT termination_timestamp
--                                                FROM ties_google_runboard g
--                                                     JOIN ties_runboards rb
--                                                        ON rb.runboard_id =
--                                                              g.runboard_id)))
                    -- INC0143557 20250410 end
                    GROUP BY trb.runboard_signid))
     SELECT DISTINCT
            ROUTE_ID,
            AGENCY_ID,
            ROUTE_SHORT_NAME,
            ROUTE_LONG_NAME,
            ROUTE_DESC,
            ROUTE_TYPE,
            --ROUTE_CATEGORY,
            ROUTE_URL,
            UPPER (
                  SUBSTR (ROUTE_COLOR, 5, 2)
               || SUBSTR (ROUTE_COLOR, 3, 2)
               || SUBSTR (ROUTE_COLOR, 1, 2))
               AS ROUTE_COLOR,
            (CASE
                WHEN INSTR (UPPER (ROUTE_COLOR), 'FFFF') > 0 THEN '000000'
                WHEN PARENT_ROUTE_ID IN ('107', '109') THEN '000000' --20180126 AH was = '107'
                ELSE ROUTE_TEXT_COLOR
             END)
               ROUTE_TEXT_COLOR,
            PARENT_ROUTE_ID,
            ROUTE_CLASSIFICATION_NAME,
            ROUTE_CLASSIFICATION_ID,
            ROUTE_SORT_ORDER,
            NETWORK_ID                                 -- 20241020  Custom-375
       FROM (SELECT DISTINCT
                    tb.branch_name,
                    TR.ROUTE_ABBR                              AS PARENT_ROUTE_ID,
                    (CASE
                        WHEN --TR.ROUTE_TYPE = '34'                 --Commented by Srikanth CH on 07/09/2024 replaced with Route_type_categories
                            cat.route_category = 'BUSTANG_ROUTES' THEN 'CDOT'
                        ELSE 'RTD'
                     END)
                       AS AGENCY_ID,
                    CASE
                       WHEN tb.branch_name IS NOT NULL
                       THEN
                          CASE
                             WHEN --tr.route_type IN (2, 32)          --Commented by Srikanth CH on 07/09/2024 replaced with Route_type_categories
                                 cat.route_category IN
                                     ('PUBLIC_LIGHT_RAIL',
                                      'PUBLIC_COMMUTER_RAIL',
                                      'PUBLIC_RAIL')
                             THEN
                                CASE
                                   WHEN TR.ROUTE_ABBR = TB.BRANCH_NAME
                                   THEN
                                      TB.BRANCH_NAME
                                   ELSE
                                      tr.route_abbr || tb.branch_name --   TIES 1 requirements --
                                END
                             ELSE
                                tb.branch_name
                          END
                       ELSE
                          tr.route_abbr
                    END
                       ROUTE_ID,
                    CASE
                       WHEN tb.branch_name IS NOT NULL -- AND tr.route_type != 2
                       THEN
                          tb.branch_name
                       ELSE
                          DECODE (TRIM (tr.long_abbr),
                                  NULL, tr.route_abbr,
                                  tr.long_abbr)
                    END
                       route_short_name,
                    CASE
                       WHEN     tb.branch_name IS NOT NULL
                            AND --tr.route_type IN (2, 32)            --Commented by Srikanth CH on 07/09/2024 replaced with Route_type_categories
                               cat.route_category IN
                                   ('PUBLIC_LIGHT_RAIL',
                                    'PUBLIC_COMMUTER_RAIL',
                                    'PUBLIC_RAIL')
                       THEN
                          TRC.BRANCH_DESCRIP
                       ELSE
                          tr.route_name
                    END
                       route_long_name,
                    get_route_desc (tr.route_id, tr.runboard_id) route_desc,
                    --DECODE (tr.route_type,  2, 0,  32, 2,  3)  route_type,  --Commented by Srikanth CH on 07/09/2024 replaced with Route_type_categories
                    CASE
                       WHEN tb.division_id = 1 THEN 0
                       WHEN tb.division_id IN (55, 62) THEN 2
                       ELSE 3
                    END
                       route_type,             --Added by Srikanth CH on 08/19
                    DECODE ( --TR.ROUTE_TYPE,18                                --Commented by Srikanth CH on 07/09/2024 replaced with Route_type_categories
                       cat.route_category,
                       'FLEX_RIDE', 'http://www.rtd-denver.com/callNRide.shtml',
                       'http://www.rtd-denver.com/Schedules.shtml')
                       route_url,
                    LPAD (
                       TRIM (
                          TO_CHAR (
                             DECODE (TRC.BRANCH_COLOR,
                                     NULL, TR.COLOR,
                                     0, TR.COLOR,
                                     TRC.BRANCH_COLOR),
                             'xxxxxx')),
                       6,
                       '0')
                       AS ROUTE_COLOR,
                    'FFFFFF'
                       route_text_color,
                    TR.ROUTE_TYPE_NAME
                       AS ROUTE_CLASSIFICATION_NAME,
                    TO_NUMBER (tr.ROUTE_TYPE)
                       AS ROUTE_CLASSIFICATION_ID,
                    tr.ROUTE_SORT_SEQUENCE
                       AS ROUTE_SORT_ORDER,
                    CASE
                       WHEN tr.gtfs_network_id = 'Airport'
                       THEN
                          'airport_network'
                       WHEN tr.gtfs_network_id = 'Free'
                       THEN
                          'free_ride_service'
                       ELSE
                          'standard_fare_network'
                    END
                       AS NETWORK_ID                   -- 20241020  Custom-375
               FROM ties_routes_vw tr
                    JOIN ties_route_type_categories_vw cat -- Added by Srikanth CH on 07/09/2024
                       ON (    cat.route_type = tr.route_type
                           AND cat.runboard_id = tr.runboard_id)
                    JOIN
                    (SELECT DISTINCT
                            tt.route_id, tt.branch_name, tt.division_id
                       FROM ties_trips_vw tt
                      WHERE tt.runboard_id IN (SELECT RUNBOARD_ID
                                                 FROM RB)
                     MINUS
                     SELECT DISTINCT
                            tt.route_id, tt.branch_name, tt.division_id
                       FROM ties_trips_vw tt
                            JOIN ties_route_type_categories_vw cat -- Added by Srikanth CH on 07/09/2024
                               ON (    cat.route_type = tt.route_type
                                   AND cat.runboard_id = tt.runboard_id)
                      WHERE     tt.runboard_id IN (SELECT RUNBOARD_ID
                                                     FROM RB)
                            AND ( -- tt.route_type IN (2, 32)      --Commented by Srikanth ch ON 07/09/2024. REPLACED with JOIN to Route_Type_Categories
                                 cat .route_category IN
                                        ('PUBLIC_LIGHT_RAIL',
                                         'PUBLIC_COMMUTER_RAIL',
                                         'PUBLIC_RAIL')
                                 AND SUBSTR (tt.branch_name, -1) IN ('#',
                                                                     'Z',
                                                                     'S',
                                                                     '*',
                                                                     '$'))) tb -- ZG, INC0083250
                       ON tb.route_id = tr.route_id
                    LEFT JOIN TIES_BRANCHES TRC
                       ON     TR.RUNBOARD_ID = TRC.RUNBOARD_ID
                          AND TB.BRANCH_NAME = TRC.BRANCH_NAME
              WHERE     tr.runboard_id IN (SELECT RUNBOARD_ID
                                             FROM RB)
                    AND tr.route_type <> '0' --Added by Srikanth CH as 0 is also PUBLIC_BUS CATEGORY
                    /*AND tr.route_type IN (1,             --Commented by Srikanth CH on 07/09/2024 replaced with Route_type_categories
                                          2,
                                          3,               -- MALL, INC0081596
                                          4,
                                          5,
                                          9,
                                          11,
                                          12,
                                          15,
                                          28,               -- FMR, INC0081596
                                          30,
                                          32,
                                          34)          -- ADDED 34 FOR BUSTANG */
                    AND cat.route_category IN ( --Added by Srikanth CH on 07/09/2024 replaced with Route_type_categories
                                               'PUBLIC_BUS',
                                               'PUBLIC_COMMUTER_RAIL',
                                               'DOWNTOWN_SHUTTLES', --?? Check with Karen
                                               'PUBLIC_LIGHT_RAIL',
                                               'PUBLIC_RAIL') --AND TR.ROUTE_ABBR NOT IN ('HOP', 'SHUZ') --INC0143211 removing restriction
                                                             )
      WHERE AGENCY_ID = 'RTD'
   ORDER BY route_id, route_long_name
;
  GRANT SELECT ON "TIES"."TIES_GOOGLE_ROUTES_VW" TO "TIES_DS";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_ROUTES_VW" TO "TIES_READ";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_ROUTES_VW" TO "TIES_WEB";
