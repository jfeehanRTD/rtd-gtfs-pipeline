--  DDL for View TIES_GOOGLE_TRIPS_VW
--------------------------------------------------------

  CREATE OR REPLACE FORCE EDITIONABLE VIEW "TIES"."TIES_GOOGLE_TRIPS_VW" ("ROUTE_ID", "SERVICE_ID", "TRIP_ID", "TRIP_HEADSIGN", "DIRECTION_ID", "BLOCK_ID", "SHAPE_ID", "DIVISION_ID", "FROM_TIME", "TO_TIME", "CR_TRIP_NUMBER", "PRIMARY_DIRECTION_NAME", "VEHICLE_BLOCK_NUMBER", "TRIP_IDENTIFIER", "DIVISION_NUMBER", "BRANCH_NAME", "VEHICLE_BLOCK_ID", "RUNBOARD_ID", "BLOCK_SEQUENCE", "ROUTE_TYPE", "ROUTE_CATEGORY", "ROUTE_ABBR", "SERVICE_TYPE_ID", "ALT_TRIP_NUMBER") AS 
  WITH RB
        AS (SELECT TGR.RUNBOARD_ID, 'R' AS RB_TYPE, 0 AS SIGN_ID
              FROM ties_google_runboard TGR
            UNION
            SELECT RUNBOARD_ID, 'S' AS RB_TYPE, RUNBOARD_SIGNID AS SIGNID
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
   -- FROM TIES_GOOGLE_SPECIAL_RB_VW TGSR)
   SELECT ROUTE_ID,
          SERVICE_ID,
          TRIP_ID,
          TRIP_HEADSIGN,
          DIRECTION_ID,
          BLOCK_ID,
          SHAPE_ID,
          DIVISION_ID,
          FROM_TIME,
          TO_TIME,
          CR_TRIP_NUMBER,
          PRIMARY_DIRECTION_NAME,
          VEHICLE_BLOCK_NUMBER,
          TRIP_IDENTIFIER,
          DIVISION_NUMBER,
          BRANCH_NAME,
          VEHICLE_BLOCK_ID,
          RUNBOARD_ID,
          BLOCK_SEQUENCE,
          ROUTE_TYPE,
          ROUTE_CATEGORY,
          ROUTE_ABBR,
          SERVICE_TYPE_ID,
          ALT_TRIP_NUMBER
     FROM (  SELECT CASE
                       WHEN tt.branch_name IS NOT NULL
                       THEN
                          CASE
                             WHEN --tt.route_type IN (2, 32)--Commented by Srikanth CH on 07/19/2024 replaced with Route_type_categories
                                 cat.route_category IN
                                     ('PUBLIC_COMMUTER_RAIL',
                                      'PUBLIC_LIGHT_RAIL',
                                      'PUBLIC_RAIL') --Added by Srikanth CH on 07/19/2024 replaced with Route_type_categories
                             THEN
                                CASE
                                   WHEN tt.ROUTE_ABBR = tt.BRANCH_NAME
                                   THEN
                                      tt.BRANCH_NAME
                                   ELSE
                                      tt.route_abbr || tt.branch_name --   TIES 1 requirements --
                                END
                             ELSE
                                tt.branch_name
                          END
                       ELSE
                          tt.route_abbr
                    END
                       ROUTE_ID,
                    DECODE (
                       TTS.SCHOOL_DISTRICT,
                       NULL, DECODE (
                                RBT.RB_TYPE,
                                'R', ts.service_type_abbr2,
                                ts.service_type_abbr2 || '_' || RBT.SIGN_ID),
                       TTS.SCHOOL_DISTRICT || ts.service_type_abbr2)
                       service_id,
                    TO_NUMBER (tt.trip_identifier) + 100000000 trip_id,
                    --DECODE (TT.ROUTE_TYPE, 2, TP.DESTINATION_HEADSIGN_TEXT, 32, TP.DESTINATION_HEADSIGN_TEXT,
                    CASE
                       WHEN cat.route_category IN
                               ('PUBLIC_LIGHT_RAIL',
                                'PUBLIC_COMMUTER_RAIL',
                                'PUBLIC_RAIL') --Added by Srikanth CH on 07/18/2024 replaced with Route_type_categories
                       THEN
                          TP.DESTINATION_HEADSIGN_TEXT
                       ELSE
                          (CASE
                              WHEN INSTR (
                                      TP.DESTINATION_HEADSIGN_TEXT,
                                      TRIM (
                                         DECODE (
                                            TT.BRANCH_NAME,
                                            NULL, DECODE (TR.LONG_ABBR,
                                                          NULL, TT.ROUTE_ABBR,
                                                          UPPER (TR.LONG_ABBR)),
                                            TT.BRANCH_NAME))) > 0
                              THEN
                                 SUBSTR (
                                    TP.DESTINATION_HEADSIGN_TEXT,
                                      LENGTH (
                                         TRIM (
                                            DECODE (
                                               TT.BRANCH_NAME,
                                               NULL, DECODE (
                                                        TR.LONG_ABBR,
                                                        NULL, TT.ROUTE_ABBR,
                                                        TR.LONG_ABBR),
                                               TT.BRANCH_NAME)))
                                    + 2,
                                      LENGTH (
                                         TRIM (TP.DESTINATION_HEADSIGN_TEXT))
                                    - LENGTH (
                                         TRIM (
                                            DECODE (
                                               TT.BRANCH_NAME,
                                               NULL, DECODE (
                                                        TR.LONG_ABBR,
                                                        NULL, TT.ROUTE_ABBR,
                                                        TR.LONG_ABBR),
                                               TT.BRANCH_NAME)))
                                    - 1)
                              ELSE
                                 TRIM (TP.DESTINATION_HEADSIGN_TEXT)
                           END)
                    END
                       trip_headsign,
                    tt.sub_direction                         direction_id,
                       LPAD (tt.primary_route_abbr, 4, ' ')
                    || DECODE (tt.driver_block_number,
                               NULL, LPAD (tt.vehicle_block_number, 3, ' '),
                               LPAD (tt.driver_block_number, 3, ' '))
                       block_id,
                    tt.pattern_id                            shape_id,
                    TT.DIVISION_ID,
                    tts.from_time,
                    tts.to_time,
                    tt.cr_trip_number,
                    tt.primary_direction_name,
                    tt.vehicle_block_number,
                    tt.trip_identifier,
                    tt.division_number,
                    tt.branch_name,
                    tts.vehicle_block_id,
                    tt.runboard_id,
                    (CASE
                        WHEN cat.route_category IN
                                ('PUBLIC_COMMUTER_RAIL',
                                 'PUBLIC_LIGHT_RAIL',
                                 'PUBLIC_RAIL')
                        THEN
                           tts.block_sequence
                        ELSE
                           1
                     END)
                       block_sequence,
                    tt.route_type,
                    cat.route_category,
                    tt.route_abbr,
                    tt.service_type_id,
                    tt.alt_trip_number
               FROM ties_rpt_trips tt
                    JOIN TIES_TRIPS TTS
                       ON     TT.RUNBOARD_ID = TTS.RUNBOARD_ID
                          AND TT.TRIP_ID = TTS.TRIP_ID
                    JOIN ties_patterns tp
                       ON     tt.runboard_id = tp.runboard_id
                          AND tt.pattern_id = tp.pattern_id
                    JOIN ties_routes_vw tr
                       ON     tr.runboard_id = tt.runboard_id
                          AND tr.route_id = tt.route_id
                    JOIN ties_service_types ts
                       ON     tt.runboard_id = ts.runboard_id
                          AND tt.service_type_id = ts.service_type_id
                    JOIN (SELECT RUNBOARD_ID, RB_TYPE, SIGN_ID FROM RB) RBT
                       ON TT.RUNBOARD_ID = RBT.RUNBOARD_ID
                    JOIN ties_route_type_categories_vw cat -- Added by Srikanth CH on 07/09/2024
                       ON (    cat.route_type = tt.route_type
                           AND cat.runboard_id = tt.runboard_id)
              WHERE     tt.runboard_id IN (SELECT runboard_id
                                             FROM RB)
                    --AND tt.route_type IN ('1', '2', '3', '4',   '5', '9', '11', '12', '15', '28', '30', '32') --Commented by Srikanth CH on 07/19/2024 replaced with Route_type_categories
                    AND tt.route_type <> '0' --Added by Srikanth CH as 0 is also PUBLIC_BUS CATEGORY
                    AND cat.route_category IN ('PUBLIC_BUS',
                                               'DOWNTOWN_SHUTTLES',
                                               'PUBLIC_COMMUTER_RAIL',
                                               'PUBLIC_LIGHT_RAIL',
                                               'PUBLIC_RAIL') --Added by Srikanth CH on 07/19/2024 replaced with Route_type_categories
                    --AND NOT (   TT.ROUTE_ABBR IN ('HOP', 'SHUZ') --INC0143211 removing restriction
                    --OR (--tt.route_type IN (2, 32) --Commented By Srikanth CH
                    --         OR (    cat.route_category IN    --INC0143211 removing restriction
                    AND NOT ( (    cat.route_category IN --INC0143211 removing restriction
                                      ('PUBLIC_COMMUTER_RAIL',
                                       'PUBLIC_LIGHT_RAIL',
                                       'PUBLIC_RAIL') --Added by Srikanth CH on 07/19/2024 replaced with Route_type_categories
                               AND SUBSTR (tt.branch_name, -1) IN ('#',
                                                                   'Z',
                                                                   'S',
                                                                   '*',
