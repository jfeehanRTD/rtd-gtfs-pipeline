--  DDL for View TIES_GOOGLE_STOPS_VW
--------------------------------------------------------

  CREATE OR REPLACE FORCE EDITIONABLE VIEW "TIES"."TIES_GOOGLE_STOPS_VW" ("STOP_ID", "STOP_CODE", "STOP_NAME", "STOP_DESC", "STOP_LAT", "STOP_LON", "ZONE_ID", "STOP_URL", "LOCATION_TYPE", "PARENT_STATION", "STOP_TIMEZONE", "WHEELCHAIR_BOARDING", "STOP_ADDRESS", "GATE", "STOP_MODE", "PLATFORM_CODE") AS 
  WITH RB
        AS (SELECT TGR.RUNBOARD_ID
              FROM TIES.TIES_GOOGLE_runboard TGR
            UNION
            --SELECT TGSR.RUNBOARD_ID
            --  FROM TIES.TIES_GOOGLE_SPECIAL_RB_VW TGSR)
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
          POINT_NUMBER                                  AS STOP_ID,
          POINT_NUMBER                                  AS STOP_CODE,
          TRIM (TIES.remove_curly_brackets (NAME_LONG)) AS STOP_NAME, -- 20160429, ZG
          STOP_DESC,
          LATITUDE_COORDINATE                           AS STOP_LAT,
          LONGITUDE_COORDINATE                          AS STOP_LON,
          FARE_ZONE                                     AS ZONE_ID,
          NULL                                          AS STOP_URL,
          (CASE
              WHEN PARENT_NUMBER IS NOT NULL
              THEN                                         -- WITHIN A STATION
                 CASE WHEN PARENT_NUMBER = POINT_NUMBER THEN    -- PARENT STOP
                                                            1 ELSE 0 END
              ELSE
                 0
           END)
             AS LOCATION_TYPE,
          (CASE
              WHEN PARENT_NUMBER IS NOT NULL
              THEN                                         -- WITHIN A STATION
                 CASE
                    WHEN PARENT_NUMBER = POINT_NUMBER THEN      -- PARENT STOP
                                                          NULL
                    ELSE PARENT_NUMBER
                 END
              ELSE
                 NULL
           END)
             AS PARENT_STATION,
          ''                                            AS STOP_TIMEZONE,
          (CASE
              WHEN PARENT_NUMBER IS NOT NULL
              THEN                                         -- WITHIN A STATION
                 CASE WHEN PARENT_NUMBER = POINT_NUMBER THEN    -- PARENT STOP
                                                            0 ELSE 1 END
              ELSE
                 1
           END)
             AS WHEELCHAIR_BOARDING,
          STOP_ADDRESS,
          GATE,
          STOP_MODE,
          CASE
             WHEN NAME_LONG LIKE '%Gate %'
             THEN
                SUBSTR (NAME_LONG,
                        INSTR (NAME_LONG, 'Gate ') + 5,
                        LENGTH (NAME_LONG) - INSTR (NAME_LONG, 'Gate ') + 5)
             ELSE
                NULL
          END
             AS PLATFORM_CODE
     FROM (  SELECT POINT_NUMBER,
                    PARENT_STOP,
                    PNR_ID,
                    STOP_POS,
                    (CASE
                        WHEN PNR_ID > 0
                        THEN
                           MAX (PARENT_STOP) OVER (PARTITION BY PNR_ID)
                        ELSE
                           NULL
                     END)
                       PARENT_NUMBER,
                    NAME_SHORT,
                    NAME_LONG,
                    LONGITUDE_COORDINATE,
                    LATITUDE_COORDINATE,
                    LOCATION,
                    GATE,
                    STOP_ADDRESS,
                    TRAVEL_DIRECTION,
                       CASE
                          WHEN (   LENGTH (TRIM (travel_direction)) = 0
                                OR travel_direction IS NULL)
                          THEN
                             ''
                          ELSE
                             'Vehicles Travelling '
                       END
                    || DECODE (travel_direction,
                               'N', 'North',
                               'S', 'South',
                               'W', 'West',
                               'E', 'East',
                               'NE', 'Northeast',
                               'SE', 'Southeast',
                               'SW', 'Southwest',
                               'NW', 'Northwest',
                               '')
                       STOP_DESC,
                    STOP_MODE
               FROM (SELECT TS.STOP_ABBREVIATION                  AS POINT_NUMBER,
                            (CASE
                                WHEN INSTR (TS.STOP_NAME, 'Parent') > 0
                                THEN
                                   TS.STOP_ABBREVIATION
                                ELSE
                                   NULL
                             END)
                               AS PARENT_STOP,
                            TS.PNR_ID,
                            DECODE (LOCATION,  'P', 0,  'G', 0,  1) AS STOP_POS,
                            TS.STOP_NAME
                               AS NAME_SHORT,
                            TS.STOP_NAME
                               AS NAME_LONG,
                            TS.STOP_LONGITUDE / 1000000
                               AS LONGITUDE_COORDINATE,
                            TS.STOP_LATITUDE / 1000000
                               AS LATITUDE_COORDINATE,
                            LOCATION,
                            GATE,
                            STOP_ADDRESS,
                            TRAVEL_DIRECTION,
                            STOP_MODE
                       FROM ( (SELECT TS1.RUNBOARD_ID,
                                      TS1.STOP_ABBREVIATION,
                                      TS1.PNR_ID,
                                      LOCATION,
                                      TS1.STOP_NAME,
                                      TS1.STOP_LONGITUDE,
                                      TS1.STOP_LATITUDE,
                                      TS1.TRAVEL_DIRECTION,
                                      TS1.GATE,
                                      TS1.STOP_ADDRESS,
                                      get_stop_mode (NULL, TS1.STOP_ID, 2)
                                         AS STOP_MODE,
                                      MAX (TS1.RUNBOARD_ID)
                                      OVER (PARTITION BY TS1.STOP_ABBREVIATION)
                                         AS LATEST_RB
                                 FROM TIES_STOPS TS1
                                WHERE     ts1.runboard_id IN
                                             (SELECT runboard_id
                                                FROM RB)
                                      AND TS1.STOP_ID IN
                                             (SELECT DISTINCT tte.stop_id
                                                FROM ties_trip_events tte
                                               WHERE     tte.runboard_id IN
                                                            (SELECT runboard_id
                                                               FROM RB)
                                                     AND tte.event_type_id = 1
                                                     AND EXISTS
                                                            (SELECT 1
                                                               FROM (SELECT tt.trip_id
                                                                       FROM ties_RPT_trips
                                                                            tt
                                                                            JOIN
                                                                            TIES_ROUTE_TYPE_CATEGORIES_VW
                                                                            CAT1
                                                                               ON (    CAT1.RUNBOARD_ID =
                                                                                          TT.RUNBOARD_ID
                                                                                   AND CAT1.ROUTE_TYPE =
                                                                                          TT.ROUTE_TYPE
                                                                                   AND CAT1.ROUTE_CATEGORY IN
                                                                                          ('PUBLIC_RAIL',
                                                                                           'PUBLIC_LIGHT_RAIL',
                                                                                           'PUBLIC_COMMUTER_RAIL',
                                                                                           'PUBLIC_BUS',
                                                                                           'DOWNTOWN_SHUTTLES'))
                                                                      WHERE     TT.runboard_id IN
                                                                                   (SELECT runboard_id
                                                                                      FROM RB)
                                                                            AND tt.route_type <>
                                                                                   '0'
                                                                     /*
                                                                           AND tt.route_type IN
                                                                                  ('1',
                                                                                   '2',
                                                                                   '3', -- MALL, INC0081596
                                                                                   '4',
                                                                                   '5',
                                                                                   '9',
                                                                                   '11',
                                                                                   '12',
                                                                                   '15',
                                                                                   '28', -- FMR, INC0081596
                                                                                   '30',
                                                                                   '32')
                                                                           -- '34')      -- 20190910, ZG, REMOVE CDOT TYPE 34        -- 20181022, ZG, ADDED CDOT ROUTE TYPE 34
                                                                           */
                                                                     --AND TT.ROUTE_ABBR NOT IN             --INC0143211 removing restriction
                                                                     --       ('HOP',
                                                                     --        'SHUZ')
                                                                     MINUS
                                                                     SELECT tt.trip_id
                                                                       FROM ties_RPT_trips
                                                                            tt
                                                                            JOIN
                                                                            TIES_ROUTE_TYPE_CATEGORIES_VW
                                                                            CAT2
                                                                               ON (    CAT2.RUNBOARD_ID =
                                                                                          TT.RUNBOARD_ID
                                                                                   AND CAT2.ROUTE_TYPE =
                                                                                          TT.ROUTE_TYPE
                                                                                   AND CAT2.ROUTE_CATEGORY IN
                                                                                          ('PUBLIC_RAIL',
                                                                                           'PUBLIC_LIGHT_RAIL',
                                                                                           'PUBLIC_COMMUTER_RAIL'))
                                                                      WHERE     TT.runboard_id IN
                                                                                   (SELECT runboard_id
                                                                                      FROM RB)
                                                                            AND ( --tt.route_type IN ('2','32')
                                                                                 SUBSTR (
                                                                                    tt.branch_name,
                                                                                    -1) IN
                                                                                    ('#',
                                                                                     'Z',
                                                                                     'S',
                                                                                     '*',
                                                                                     '$'))) -- ZG, 20200714, INC0083250
                                                                    trips_needed
                                                              WHERE trips_needed.trip_id =
                                                                       tte.trip_id)
                                                     AND NOT EXISTS
                                                            (SELECT 1
                                                               FROM ties_trip_event_label_rules
                                                                    ttelr
                                                              WHERE     ttelr.runboard_id =
                                                                           tte.runboard_id
                                                                    AND ttelr.trip_events_id =
                                                                           tte.trip_events_id
                                                                    AND ttelr.product_id =
                                                                           6)
                                              MINUS
                                              SELECT STOP_ID
                                                FROM (SELECT STOP_ID
                                                        FROM TIES_TRIP_EVENTS
                                                             TTE
                                                             JOIN
                                                             TIES_RPT_TRIPS TRT
                                                                ON (    TTE.RUNBOARD_ID =
                                                                           TRT.RUNBOARD_ID
                                                                    AND TTE.TRIP_ID =
                                                                           TRT.TRIP_ID)
                                                             JOIN
                                                             TIES_ROUTE_TYPE_CATEGORIES_VW
                                                             CAT2
                                                                ON (    CAT2.RUNBOARD_ID =
                                                                           TRT.RUNBOARD_ID
                                                                    AND CAT2.ROUTE_TYPE =
                                                                           TRT.ROUTE_TYPE
                                                                    AND CAT2.ROUTE_CATEGORY IN
                                                                           ('PUBLIC_RAIL',
                                                                            'PUBLIC_LIGHT_RAIL',
                                                                            'PUBLIC_COMMUTER_RAIL'))
                                                       WHERE     TTE.RUNBOARD_ID IN
                                                                    (SELECT runboard_id
                                                                       FROM RB)
                                                             --AND TRT.ROUTE_TYPE IN ('2', '32')
                                                             AND TTE.NODE_ID
                                                                    IS NULL
                                                      UNION
                                                        SELECT stop_id
                                                          FROM (SELECT pattern_id,
                                                                       stop_id,
                                                                       NVL (
                                                                          stop_actions,
                                                                          0)
                                                                          stop_act
                                                                  FROM ties_pattern_stops
                                                                       tps
                                                                 WHERE     tps.runboard_id IN
                                                                              (SELECT runboard_id
                                                                                 FROM RB)
                                                                       AND tps.pattern_id IN
                                                                              (SELECT DISTINCT
                                                                                      pattern_id
                                                                                 FROM ties_rpt_trips
                                                                                      trt
                                                                                WHERE trt.runboard_id =
                                                                                         tps.runboard_id))
                                                      GROUP BY stop_id
                                                        HAVING MIN (stop_act) =
                                                                  3)))
                             UNION
                             SELECT TS2.RUNBOARD_ID,
                                    TS2.STOP_ABBREVIATION,
                                    TS2.PNR_ID,
                                    LOCATION,
                                    TS2.STOP_NAME,
                                    TS2.STOP_LONGITUDE,
                                    TS2.STOP_LATITUDE,
                                    TS2.TRAVEL_DIRECTION,
                                    TS2.GATE,
                                    TS2.STOP_ADDRESS,
                                    '' AS STOP_MODE,
                                    MAX (TS2.RUNBOARD_ID)
                                    OVER (PARTITION BY TS2.STOP_ABBREVIATION)
                                       AS LATEST_RB
                               FROM TIES_STOPS TS2
                              WHERE     ts2.runboard_id IN (SELECT runboard_id
                                                              FROM RB)
                                    AND INSTR (TS2.STOP_NAME, 'Parent') > 0
                                    AND TS2.PNR_ID > 0
                                    AND EXISTS
                                           (SELECT 1
                                              FROM TIES_TRIP_EVENTS TTE
                                             WHERE     TTE.RUNBOARD_ID =
                                                          TS2.RUNBOARD_ID
                                                   AND TTE.STOP_ID IN
                                                          (SELECT STOP_ID
                                                             FROM TIES_STOPS
                                                                  TS3
                                                            WHERE     TS3.RUNBOARD_ID =
                                                                         TTE.RUNBOARD_ID
                                                                  AND TS3.PNR_ID =
                                                                         TS2.PNR_ID)))
                            TS
                      WHERE TS.RUNBOARD_ID = TS.LATEST_RB)
           ORDER BY 3, 4, 1) ST
          LEFT JOIN
          (SELECT DISTINCT TS.STOP_ABBREVIATION,
                           TTE.NODE_ID,
                           TN.NODE_ABBR,
                           TN.NODE_NAME,
                           TN.FARE_ZONE
             FROM TIES_TRIP_EVENTS TTE
                  JOIN TIES_NODES TN
                     ON     TTE.RUNBOARD_ID = TN.RUNBOARD_ID
                        AND TTE.NODE_ID = TN.NODE_ID
                  JOIN TIES_STOPS TS
                     ON     TTE.RUNBOARD_ID = TS.RUNBOARD_ID
                        AND TTE.STOP_ID = TS.STOP_ID
            WHERE     TTE.RUNBOARD_ID IN (SELECT runboard_id
                                            FROM RB)
                  AND TN.FARE_ZONE IS NOT NULL                    --ORDER BY 3
                                              ) STZ
             ON ST.POINT_NUMBER = STZ.STOP_ABBREVIATION
;
  GRANT SELECT ON "TIES"."TIES_GOOGLE_STOPS_VW" TO "TIES_DS";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_STOPS_VW" TO "TIES_READ";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_STOPS_VW" TO "TIES_REPORT";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_STOPS_VW" TO "TIES_WEB";
--------------------------------------------------------
--  DDL for View TIES_GOOGLE_STOP_AREAS_CDOT_VW
--------------------------------------------------------

  CREATE OR REPLACE FORCE EDITIONABLE VIEW "TIES"."TIES_GOOGLE_STOP_AREAS_CDOT_VW" ("STOP_ID", "AREA_ID") AS 
  WITH RB
        AS (SELECT TGR.RUNBOARD_ID
              FROM ties_google_runboard TGR
            UNION
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
          POINT_NUMBER,
          CASE
             WHEN INSTR (GTFS_AREA_ID, ';') > 0
             THEN
                SUBSTR (GTFS_AREA_ID, 1, INSTR (GTFS_AREA_ID, ';') - 1)
             ELSE
                GTFS_AREA_ID
          END
             AS AREA_ID
     FROM (  SELECT POINT_NUMBER, GTFS_AREA_ID
               FROM (SELECT TS.STOP_ABBREVIATION AS POINT_NUMBER,
                            TS.GTFS_AREA_ID
                       FROM ((SELECT TS1.RUNBOARD_ID,
                                     TS1.STOP_ABBREVIATION,
                                     TS1.GTFS_AREA_ID
                                FROM TIES_STOPS TS1
                               WHERE     ts1.runboard_id IN (SELECT runboard_id
                                                               FROM RB)
                                     AND TS1.STOP_ID IN
                                            (SELECT DISTINCT tte.stop_id
                                               FROM ties_trip_events tte
                                              WHERE     tte.runboard_id IN
                                                           (SELECT runboard_id
                                                              FROM RB)
                                                    AND tte.event_type_id = 1
                                                    AND EXISTS
                                                           (SELECT 1
                                                              FROM (SELECT tt.trip_id
                                                                      FROM ties_RPT_trips
                                                                           tt
                                                                           JOIN
                                                                           TIES_ROUTE_TYPE_CATEGORIES_VW
                                                                           CAT1
                                                                              ON (    CAT1.RUNBOARD_ID =
                                                                                         TT.RUNBOARD_ID
                                                                                  AND CAT1.ROUTE_TYPE =
                                                                                         TT.ROUTE_TYPE
                                                                                  AND CAT1.ROUTE_CATEGORY IN
                                                                                         ('BUSTANG_ROUTES'))
                                                                     WHERE tt.runboard_id IN
                                                                              (SELECT runboard_id
                                                                                 FROM RB) --AND tt.route_type IN ('34') -- 20190910, ZG, ONLY CDOT TYPE 34        -- 20181022, ZG, ADDED CDOT ROUTE TYPE 34
                                                                                         )
                                                                   trips_needed -- ZG, 20200714, INC0083250
                                                             WHERE trips_needed.trip_id =
                                                                      tte.trip_id)
                                                    AND NOT EXISTS
                                                           (SELECT 1
                                                              FROM ties_trip_event_label_rules
                                                                   ttelr
                                                             WHERE     ttelr.runboard_id =
                                                                          tte.runboard_id
                                                                   AND ttelr.trip_events_id =
                                                                          tte.trip_events_id
                                                                   AND ttelr.product_id =
                                                                          6)
                                             MINUS
                                             SELECT STOP_ID
                                               FROM (  SELECT stop_id
                                                         FROM (SELECT pattern_id,
                                                                      stop_id,
                                                                      NVL (
                                                                         stop_actions,
                                                                         0)
                                                                         stop_act
                                                                 FROM ties_pattern_stops
                                                                      tps
                                                                WHERE     tps.runboard_id IN
                                                                             (SELECT runboard_id
                                                                                FROM RB)
                                                                      AND tps.pattern_id IN
                                                                             (SELECT DISTINCT
                                                                                     pattern_id
                                                                                FROM ties_rpt_trips
                                                                                     trt
                                                                               WHERE trt.runboard_id =
                                                                                        tps.runboard_id))
                                                     GROUP BY stop_id
                                                       HAVING MIN (stop_act) =
                                                                 3)))) TS)
           ORDER BY 1) ST
          LEFT JOIN
          (SELECT DISTINCT TS.STOP_ABBREVIATION,
                           TTE.NODE_ID,
                           TN.NODE_ABBR,
                           TN.NODE_NAME,
                           TN.FARE_ZONE
             FROM TIES_TRIP_EVENTS TTE
                  JOIN TIES_NODES TN
                     ON     TTE.RUNBOARD_ID = TN.RUNBOARD_ID
                        AND TTE.NODE_ID = TN.NODE_ID
                  JOIN TIES_STOPS TS
                     ON     TTE.RUNBOARD_ID = TS.RUNBOARD_ID
                        AND TTE.STOP_ID = TS.STOP_ID
            WHERE     TTE.RUNBOARD_ID IN (SELECT runboard_id
                                            FROM RB)
                  AND TN.FARE_ZONE IS NOT NULL                    --ORDER BY 3
                                              ) STZ
             ON ST.POINT_NUMBER = STZ.STOP_ABBREVIATION
;
  GRANT SELECT ON "TIES"."TIES_GOOGLE_STOP_AREAS_CDOT_VW" TO "TIES_DS";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_STOP_AREAS_CDOT_VW" TO "TIES_READ";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_STOP_AREAS_CDOT_VW" TO "TIES_WEB";
--------------------------------------------------------
--  DDL for View TIES_GOOGLE_STOP_AREAS_VW
--------------------------------------------------------

  CREATE OR REPLACE FORCE EDITIONABLE VIEW "TIES"."TIES_GOOGLE_STOP_AREAS_VW" ("STOP_ID", "AREA_ID") AS 
  WITH RB
        AS (SELECT TGR.RUNBOARD_ID
              FROM TIES.TIES_GOOGLE_runboard TGR
            UNION
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
          POINT_NUMBER,
          CASE
             WHEN INSTR (GTFS_AREA_ID, ';') > 0
             THEN
                SUBSTR (GTFS_AREA_ID, 1, INSTR (GTFS_AREA_ID, ';') - 1)
             ELSE
                GTFS_AREA_ID
          END
             AS AREA_ID
     FROM (SELECT POINT_NUMBER, GTFS_AREA_ID
             FROM (SELECT TS.STOP_ABBREVIATION AS POINT_NUMBER,
                          TS.GTFS_AREA_ID
                     FROM ( (SELECT TS1.RUNBOARD_ID,
                                    TS1.STOP_ABBREVIATION,
                                    CASE
                                       WHEN TS1.GTFS_AREA_ID IS NULL
                                       THEN
                                          'local_fare_zone'
                                       ELSE
                                          TS1.GTFS_AREA_ID
                                    END
                                       AS GTFS_AREA_ID,
                                    MAX (TS1.RUNBOARD_ID)
                                    OVER (PARTITION BY TS1.STOP_ABBREVIATION)
                                       AS LATEST_RB
                               FROM TIES_STOPS TS1
                              WHERE     ts1.runboard_id IN
                                           (SELECT runboard_id
                                              FROM RB)
                                    AND TS1.STOP_ID IN
                                           (SELECT DISTINCT tte.stop_id
                                              FROM ties_trip_events tte
                                             WHERE     tte.runboard_id IN
                                                          (SELECT runboard_id
                                                             FROM RB)
                                                   AND tte.event_type_id = 1
                                                   AND EXISTS
                                                          (SELECT 1
                                                             FROM (SELECT tt.trip_id
                                                                     FROM ties_RPT_trips
                                                                          tt
                                                                          JOIN
                                                                          TIES_ROUTE_TYPE_CATEGORIES_VW
                                                                          CAT1
                                                                             ON (    CAT1.RUNBOARD_ID =
                                                                                        TT.RUNBOARD_ID
                                                                                 AND CAT1.ROUTE_TYPE =
                                                                                        TT.ROUTE_TYPE
                                                                                 AND CAT1.ROUTE_CATEGORY IN
                                                                                        ('PUBLIC_RAIL',
                                                                                         'PUBLIC_LIGHT_RAIL',
                                                                                         'PUBLIC_COMMUTER_RAIL',
                                                                                         'PUBLIC_BUS',
                                                                                         'DOWNTOWN_SHUTTLES'))
                                                                    WHERE     TT.runboard_id IN
                                                                                 (SELECT runboard_id
                                                                                    FROM RB)
                                                                          AND tt.route_type <>
                                                                                 '0'
                                                                   --AND TT.ROUTE_ABBR NOT IN    --INC0143211 removing restriction
                                                                   --       ('HOP',
                                                                   --        'SHUZ')
                                                                   MINUS
                                                                   SELECT tt.trip_id
                                                                     FROM ties_RPT_trips
                                                                          tt
                                                                          JOIN
                                                                          TIES_ROUTE_TYPE_CATEGORIES_VW
                                                                          CAT2
                                                                             ON (    CAT2.RUNBOARD_ID =
                                                                                        TT.RUNBOARD_ID
                                                                                 AND CAT2.ROUTE_TYPE =
                                                                                        TT.ROUTE_TYPE
                                                                                 AND CAT2.ROUTE_CATEGORY IN
                                                                                        ('PUBLIC_RAIL',
                                                                                         'PUBLIC_LIGHT_RAIL',
                                                                                         'PUBLIC_COMMUTER_RAIL'))
                                                                    WHERE     TT.runboard_id IN
                                                                                 (SELECT runboard_id
                                                                                    FROM RB)
                                                                          AND ( --tt.route_type IN ('2','32')
                                                                               SUBSTR (
                                                                                  tt.branch_name,
                                                                                  -1) IN
                                                                                  ('#',
                                                                                   'Z',
                                                                                   'S',
                                                                                   '*',
                                                                                   '$'))) -- ZG, 20200714, INC0083250
                                                                  trips_needed
                                                            WHERE trips_needed.trip_id =
                                                                     tte.trip_id)
                                                   AND NOT EXISTS
                                                          (SELECT 1
                                                             FROM ties_trip_event_label_rules
                                                                  ttelr
                                                            WHERE     ttelr.runboard_id =
                                                                         tte.runboard_id
                                                                  AND ttelr.trip_events_id =
                                                                         tte.trip_events_id
                                                                  AND ttelr.product_id =
                                                                         6)
                                            MINUS
                                            SELECT STOP_ID
                                              FROM (SELECT STOP_ID
                                                      FROM TIES_TRIP_EVENTS
                                                           TTE
                                                           JOIN
                                                           TIES_RPT_TRIPS TRT
                                                              ON (    TTE.RUNBOARD_ID =
                                                                         TRT.RUNBOARD_ID
                                                                  AND TTE.TRIP_ID =
                                                                         TRT.TRIP_ID)
                                                           JOIN
                                                           TIES_ROUTE_TYPE_CATEGORIES_VW
                                                           CAT2
                                                              ON (    CAT2.RUNBOARD_ID =
                                                                         TRT.RUNBOARD_ID
                                                                  AND CAT2.ROUTE_TYPE =
                                                                         TRT.ROUTE_TYPE
                                                                  AND CAT2.ROUTE_CATEGORY IN
                                                                         ('PUBLIC_RAIL',
                                                                          'PUBLIC_LIGHT_RAIL',
                                                                          'PUBLIC_COMMUTER_RAIL'))
                                                     WHERE     TTE.RUNBOARD_ID IN
                                                                  (SELECT runboard_id
                                                                     FROM RB)
                                                           --AND TRT.ROUTE_TYPE IN ('2', '32')
                                                           AND TTE.NODE_ID
                                                                  IS NULL
                                                    UNION
                                                      SELECT stop_id
                                                        FROM (SELECT pattern_id,
                                                                     stop_id,
                                                                     NVL (
                                                                        stop_actions,
                                                                        0)
                                                                        stop_act
                                                                FROM ties_pattern_stops
                                                                     tps
                                                               WHERE     tps.runboard_id IN
                                                                            (SELECT runboard_id
                                                                               FROM RB)
                                                                     AND tps.pattern_id IN
                                                                            (SELECT DISTINCT
                                                                                    pattern_id
                                                                               FROM ties_rpt_trips
                                                                                    trt
                                                                              WHERE trt.runboard_id =
                                                                                       tps.runboard_id))
                                                    GROUP BY stop_id
                                                      HAVING MIN (stop_act) =
                                                                3)))
                           UNION
                           SELECT TS2.RUNBOARD_ID,
                                  TS2.STOP_ABBREVIATION,
                                  CASE
                                     WHEN TS2.GTFS_AREA_ID IS NULL
                                     THEN
                                        'local_fare_zone'
                                     ELSE
                                        TS2.GTFS_AREA_ID
                                  END
                                     AS GTFS_AREA_ID,
                                  MAX (TS2.RUNBOARD_ID)
                                  OVER (PARTITION BY TS2.STOP_ABBREVIATION)
                                     AS LATEST_RB
                             FROM TIES_STOPS TS2
                            WHERE     ts2.runboard_id IN (SELECT runboard_id
                                                            FROM RB)
                                  AND INSTR (TS2.STOP_NAME, 'Parent') > 0
                                  AND TS2.PNR_ID > 0
                                  AND EXISTS
                                         (SELECT 1
                                            FROM TIES_TRIP_EVENTS TTE
                                           WHERE     TTE.RUNBOARD_ID =
                                                        TS2.RUNBOARD_ID
                                                 AND TTE.STOP_ID IN
                                                        (SELECT STOP_ID
                                                           FROM TIES_STOPS
                                                                TS3
                                                          WHERE     TS3.RUNBOARD_ID =
                                                                       TTE.RUNBOARD_ID
                                                                AND TS3.PNR_ID =
                                                                       TS2.PNR_ID)))
                          TS
                    WHERE TS.RUNBOARD_ID = TS.LATEST_RB))
;
  GRANT SELECT ON "TIES"."TIES_GOOGLE_STOP_AREAS_VW" TO "TIES_DS";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_STOP_AREAS_VW" TO "TIES_READ";
  GRANT SELECT ON "TIES"."TIES_GOOGLE_STOP_AREAS_VW" TO "TIES_WEB";
