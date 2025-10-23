--  DDL for View TIES_GOOGLE_STOP_TIMES_VW
--------------------------------------------------------

  CREATE OR REPLACE FORCE EDITIONABLE VIEW "TIES"."TIES_GOOGLE_STOP_TIMES_VW" ("TRIP_ID", "ARRIVAL_TIME", "DEPARTURE_TIME", "STOP_ID", "STOP_SEQUENCE", "STOP_HEADSIGN", "PICKUP_TYPE", "DROP_OFF_TYPE", "SHAPE_DIST_TRAVELED", "NODE_ID", "FREE_RUNNING_FLAG", "NEXT_DISTANCE", "DIVISION_ID", "TIMEPOINT") AS 
  WITH runboards
        AS (SELECT /*+ result_cache */
                  runboard_id FROM ties_google_runboard
            UNION
            --SELECT  /*+ result_cache */ runboard_id
            --FROM TIES_GOOGLE_SPECIAL_RB_VW),
            SELECT runboard_id /*+ result_cache */
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
                    GROUP BY trb.runboard_signid)),
        lrt_stops
        AS (SELECT /*+ materialize */
                  tte.stop_id, trt.runboard_id, trt.trip_id
              FROM TIES_TRIP_EVENTS TTE
                   JOIN TIES_RPT_TRIPS TRT
                      ON (    tte.runboard_id = trt.runboard_id
                          AND tte.trip_id = trt.trip_id)
                   JOIN ties_route_type_categories_vw cat
                      ON (    cat.route_type = trt.route_type
                          AND cat.runboard_id = trt.runboard_id
                          AND cat.route_category IN
                                 ('PUBLIC_LIGHT_RAIL',
                                  'PUBLIC_COMMUTER_RAIL',
                                  'PUBLIC_RAIL'))
             WHERE     TTE.RUNBOARD_ID IN (SELECT runboard_id
                                             FROM runboards) --AND tte.event_type_id=1
                   AND TTE.NODE_ID IS NULL),
        WS
        AS                -- STOPS WITH TWO TIMES (ARRIVAL AND DEPARTURE TIME)
          (SELECT runboard_id,
                  trip_id,
                  TRIP_IDENTIFIER,
                  DIVISION_ID,
                  PATTERN_ID,
                  stop_id,
                  event_time,
                  event_time1,
                  stop_id1,
                  TRIP_EVENTS_ID,
                  NODE_ID,
                  FREE_RUNNING_FLAG
             FROM (SELECT tte.runboard_id,
                          tte.trip_id,
                          TRT.TRIP_IDENTIFIER,                            ----
                          TRT.DIVISION_ID,
                          TRT.PATTERN_ID,                                 ----
                          tte.event_time,
                          LEAD (
                             tte.event_time,
                             1,
                             0)
                          OVER (PARTITION BY tte.trip_id
                                ORDER BY tte.event_time)
                             event_time1,
                          tte.stop_id,
                          LEAD (
                             tte.stop_id,
                             1,
                             0)
                          OVER (PARTITION BY tte.trip_id
                                ORDER BY tte.event_time)
                             stop_id1,
                          --DECODE (TRT.ROUTE_TYPE,'3', TTE.TRIP_EVENTS_ID,NULL) TRIP_EVENTS_ID,--Commented by Srikanth CH
                          DECODE (TRT.ROUTE_ABBR,
                                  'MALL', TTE.TRIP_EVENTS_ID,
                                  NULL)
                             TRIP_EVENTS_ID, ----Added by Srikanth Ch on 08/05/2024
                          TTE.NODE_ID,
                          TTE.FREE_RUNNING_FLAG
                     FROM ties_trip_events  tte
                          JOIN TIES_RPT_TRIPS TRT
                             ON     TTE.RUNBOARD_ID = TRT.RUNBOARD_ID
                                AND TTE.TRIP_ID = TRT.TRIP_ID
                    WHERE     tte.runboard_id IN (SELECT runboard_id
                                                    FROM runboards)
                          AND tte.event_type_id = 1
                          AND NOT EXISTS
                                 (SELECT 1
                                    FROM ties_trip_event_label_rules ttelr2
                                   WHERE     ttelr2.runboard_id =
                                                tte.runboard_id
                                         AND ttelr2.trip_events_id =
                                                tte.trip_events_id
                                         AND ttelr2.product_id = 6)) aaa
            WHERE stop_id = stop_id1),                                   -- WS
        WP
        AS                                       -- ALL STOPS WITH TWO ACTIONS
          (SELECT runboard_id,
                  pattern_id,
                  stop_id,
                  stop_id1,
                  stop_actions,
                  stop_actions1,
                  next_distance
             FROM (SELECT tps.runboard_id,
                          tps.pattern_id,
                          tps.stop_id,
                          LEAD (
                             tps.stop_id,
                             1,
                             0)
                          OVER (PARTITION BY tps.pattern_id
                                ORDER BY tps.stop_sequence)
                             stop_id1,
                          tps.stop_actions,
                          LEAD (
                             tps.stop_actions,
                             1,
                             0)
                          OVER (PARTITION BY tps.pattern_id
                                ORDER BY tps.stop_sequence)
                             stop_actions1,
                          TPS.NEXT_DISTANCE
                     FROM ties_pattern_stops tps
                    WHERE tps.runboard_id IN (SELECT runboard_id
                                                FROM runboards)) aaa
            WHERE aaa.stop_id = aaa.stop_id1)
     SELECT TRIP_ID,
            ARRIVAL_TIME,
            DEPARTURE_TIME,
            STOP_ID,
            STOP_SEQUENCE,
            STOP_HEADSIGN,
            CASE WHEN STOP_SEQUENCE = MAX_STOP_SEQ THEN 1 ELSE PICKUP_TYPE END
               PICKUP_TYPE,
            CASE WHEN STOP_SEQUENCE = 1 THEN 1 ELSE DROP_OFF_TYPE END
               DROP_OFF_TYPE,
            NULL                       AS SHAPE_DIST_TRAVELED, -- 20180806, ZG, DISABLE THE DISTANCE COLUMN WHICH DOES NOT MATCH THE INFO IN THE SHAPE FILE
            NODE_ID,
            FREE_RUNNING_FLAG,
            NEXT_DISTANCE,
            DIVISION_ID,
            DECODE (NODE_ID, NULL, 0, 1) TIMEPOINT
       FROM (SELECT TRIP_ID,
                    ARRIVAL_TIME,
                    DEPARTURE_TIME,
                    STOP_ID,
                    STOP_SEQUENCE,
                    STOP_HEADSIGN,
                    PICKUP_TYPE,
                    DROP_OFF_TYPE,
                    SHAPE_DIST_TRAVELED,
                    NODE_ID,
                    FREE_RUNNING_FLAG,
                    NEXT_DISTANCE,
                    DIVISION_ID,
                    MAX (STOP_SEQUENCE) OVER (PARTITION BY TRIP_ID)
                       MAX_STOP_SEQ
               FROM (SELECT TO_NUMBER (trip_identifier) + 100000000 AS trip_id,
                            translatetrapezetime (arrival_time)
                               arrival_time,
                            translatetrapezetime (departure_time)
                               departure_time,
                            stop_abbreviation                     stop_id,
                            ROW_NUMBER ()
                               OVER (PARTITION BY trip_identifier
                                     ORDER BY
                                        trip_identifier,
                                        arrival_time,
                                        TRIP_EVENTS_ID,
                                        departure_time)
                               stop_sequence,
                            ''
                               stop_headsign,
                            DECODE (stop_actions,
                                    NULL, 0,
                                    2, 0,
                                    stop_actions)
                               pickup_type,
                            DECODE (stop_actions,
                                    NULL, 0,
                                    1, 0,
                                    2, 1,
                                    stop_actions)
                               drop_off_type,
                            shape_dist_traveled,
                            NODE_ID,
                            FREE_RUNNING_FLAG,
                            NEXT_DISTANCE,
                            DIVISION_ID
                       FROM (SELECT GSTM.RUNBOARD_ID,
                                    TRIP_ID,
                                    ARRIVAL_TIME,
                                    DEPARTURE_TIME,
                                    GSTM.STOP_ABBREVIATION,
                                    STOP_ACTIONS,
                                    TRIP_IDENTIFIER,
                                    TRIP_EVENTS_ID,
                                    NODE_ID,
                                    FREE_RUNNING_FLAG,
                                    ROUND (shape_dist_traveled, 1)
                                       shape_dist_traveled,
                                    NEXT_DISTANCE,
                                    DIVISION_ID
                               FROM (                                  -- gstm
                                     SELECT gst.runboard_id,
                                            gst.trip_id,
                                            GST.PATTERN_ID,
                                            GST.STOP_ID,
                                            arrival_time,
                                            departure_time,
                                            ts.stop_abbreviation,
                                            gsp.stop_actions,
                                            gst.trip_identifier,
                                            GST.DIVISION_ID,
                                            TRIP_EVENTS_ID,
                                            NODE_ID,
                                            FREE_RUNNING_FLAG
                                       FROM (                           -- gst
                                             SELECT runboard_id,
                                                    trip_id,
                                                    TRIP_IDENTIFIER,      ----
                                                    DIVISION_ID,
                                                    PATTERN_ID,           ----
                                                    stop_id,
                                                    event_time AS arrival_time,
                                                    event_time
                                                       AS departure_time,
                                                    TRIP_EVENTS_ID,
                                                    NODE_ID,
                                                    FREE_RUNNING_FLAG
                                               FROM (SELECT tte.runboard_id,
                                                            tte.trip_id,
                                                            TRT.TRIP_IDENTIFIER, ----
                                                            TRT.DIVISION_ID,
                                                            TRT.PATTERN_ID, ----
                                                            tte.stop_id,
                                                            tte.event_time,
                                                            --DECODE (TRT.ROUTE_TYPE,'3', TTE.TRIP_EVENTS_ID,NULL) TRIP_EVENTS_ID, --Commented by Srikanth Ch on 08/05/2024
                                                            DECODE (
                                                               TRT.ROUTE_ABBR,
                                                               'MALL', TTE.TRIP_EVENTS_ID,
                                                               NULL)
                                                               TRIP_EVENTS_ID, --Added by Srikanth Ch on 08/05/2024
                                                            TTE.NODE_ID,
                                                            TTE.FREE_RUNNING_FLAG
                                                       FROM ties_trip_events
                                                            tte
                                                            JOIN
                                                            TIES_RPT_TRIPS TRT
                                                               ON     TTE.RUNBOARD_ID =
                                                                         TRT.RUNBOARD_ID
                                                                  AND TTE.TRIP_ID =
                                                                         TRT.TRIP_ID
                                                      WHERE     tte.runboard_id IN
                                                                   (SELECT runboard_id
                                                                      FROM runboards)
                                                            AND tte.event_type_id =
                                                                   1
                                                            AND NOT EXISTS -- NOT NON-PUBLIC
                                                                   (SELECT 1
                                                                      FROM TIES_TRIP_EVENT_LABEL_RULES
                                                                           TTELR1
                                                                     WHERE     TTELR1.RUNBOARD_ID =
                                                                                  TTE.RUNBOARD_ID
                                                                           AND TTELR1.TRIP_EVENTS_ID =
                                                                                  TTE.TRIP_EVENTS_ID
                                                                           AND TTELR1.PRODUCT_ID =
                                                                                  6)
                                                            AND NOT EXISTS
                                                                   ( -- GET RID OF ALL STOPS WITH TWO TIMES
                                                                    SELECT 1
                                                                      FROM WS
                                                                     WHERE     WS.RUNBOARD_ID =
                                                                                  TTE.RUNBOARD_ID
                                                                           AND WS.TRIP_ID =
                                                                                  TTE.TRIP_ID
                                                                           AND WS.STOP_ID =
                                                                                  TTE.STOP_ID))
                                             UNION -- ADD ARRIVAL/DEPARTURE RECORDS
                                             SELECT runboard_id,
                                                    trip_id,
                                                    trip_identifier,      ----
                                                    DIVISION_ID,
                                                    PATTERN_ID,           ----
                                                    stop_id,
                                                    event_time AS arrival_time,
                                                    event_time1
                                                       AS departure_time,
                                                    TRIP_EVENTS_ID,
                                                    NODE_ID,
                                                    FREE_RUNNING_FLAG
                                               FROM WS) gst
                                            JOIN
                                            (SELECT tt.runboard_id,
                                                    tt.trip_id,
                                                    tt.trip_identifier,
                                                    tt.pattern_id
                                               FROM ties_rpt_trips tt
                                                    JOIN
                                                    ties_route_type_categories_vw
                                                    cat
                                                       ON (    cat.route_type =
                                                                  tt.route_type
                                                           AND cat.runboard_id =
                                                                  tt.runboard_id
                                                           AND cat.route_category IN
                                                                  ('PUBLIC_LIGHT_RAIL',
                                                                   'PUBLIC_RAIL',
                                                                   'PUBLIC_COMMUTER_RAIL',
                                                                   'PUBLIC_BUS',
                                                                   'DOWNTOWN_SHUTTLES')) --Added by Srikanth CH
                                              WHERE     tt.runboard_id IN
                                                           (SELECT runboard_id
                                                              FROM runboards)
                                                    /* --Commented by Srikanth CH on 08/05/2024
                                                    AND tt.route_type IN ('1',
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
                                                                          '32') */
                                                    -- '34')          --20190910, ZG, REMOVED CDOT TYPE 34;      -- 20181016, ZG
                                                    --AND TT.ROUTE_ABBR NOT IN     --INC0143211 removing restriction
                                                    --       ('HOP', 'SHUZ')
                                                    AND TT.ROUTE_TYPE <> '0' --Added by Srikanth CH on 08/05/2024
                                                    AND NOT ( --tt.route_type IN ('2', '32') --Commented by Srikanth CH ON 08/05/2024
                                                             cat .route_category IN
                                                                    ('PUBLIC_LIGHT_RAIL',
                                                                     'PUBLIC_COMMUTER_RAIL',
                                                                     'PUBLIC_RAIL') --Added by Srikanth CH on 08/05/2024
                                                             AND SUBSTR (
                                                                    tt.branch_name,
                                                                    -1) IN
                                                                    ('#',
                                                                     'Z',
                                                                     'S',
                                                                     '*',
                                                                     '$'))
                                                    AND NOT EXISTS
                                                           (SELECT 1
                                                              FROM TIES_TRIP_LABEL_RULES
                                                                   TTLR
                                                             WHERE     TTLR.RUNBOARD_ID =
                                                                          TT.RUNBOARD_ID
                                                                   AND TTLR.PRODUCT_ID IN
                                                                          (3, 6)
                                                                   AND TTLR.TRIP_ID =
                                                                          TT.TRIP_ID))
                                            trt
                                               ON     trt.runboard_id =
                                                         gst.runboard_id
                                                  AND trt.trip_id = gst.trip_id
                                            JOIN ties_stops ts
                                               ON     ts.runboard_id =
                                                         gst.runboard_id
                                                  AND ts.stop_id = gst.stop_id
                                                  AND GST.STOP_ID NOT IN
                                                         (SELECT STOP_ID
                                                            FROM lrt_stops) -- NOT LRT STOPS (NON-PUBLIC)
                                            JOIN
                                            (SELECT runboard_id,
                                                    pattern_id,
                                                    stop_id,
                                                    stop_actions
                                               FROM (SELECT runboard_id,
                                                            pattern_id,
                                                            stop_id,
                                                            NVL (stop_actions,
                                                                 0)
                                                               stop_actions
                                                       FROM ties_pattern_stops
                                                            tps
                                                      WHERE     tps.runboard_id IN
                                                                   (SELECT runboard_id
                                                                      FROM runboards)
                                                            AND NOT EXISTS
                                                                   ( -- GET RID OF ALL STOPS WITH TWO TIMES
                                                                    SELECT 1
                                                                      FROM WP
                                                                     WHERE     WP.RUNBOARD_ID =
                                                                                  TPS.RUNBOARD_ID
                                                                           AND WP.PATTERN_ID =
                                                                                  TPS.PATTERN_ID
                                                                           AND WP.STOP_ID =
                                                                                  TPS.STOP_ID))
                                             UNION
                                             SELECT runboard_id,
                                                    pattern_id,
                                                    stop_id,
                                                    (CASE
                                                        WHEN     act_1 = 1
                                                             AND act_2 = 2
                                                        THEN
                                                           0
                                                        WHEN act_1 = act_2
                                                        THEN
                                                           act_1
                                                        WHEN act_1 = 3
                                                        THEN
                                                           act_2
                                                        WHEN act_2 = 3
                                                        THEN
                                                           act_1
                                                        ELSE
                                                           0
                                                     END)
                                                       stop_actions
                                               FROM (SELECT runboard_id,
                                                            pattern_id,
                                                            stop_id,
                                                            stop_actions act_1,
                                                            stop_actions1 act_2
                                                       FROM WP)) gsp
                                               ON     gst.runboard_id =
                                                         gsp.runboard_id
                                                  AND gst.pattern_id =
                                                         gsp.pattern_id
                                                  AND gst.stop_id = gsp.stop_id)
                                    gstm
                                    JOIN
                                    (SELECT RUNBOARD_ID,
                                            PATTERN_ID,
                                            STOP_ID,
                                            STOP_SEQUENCE,
                                            STOP_ABBREVIATION,
                                            LAG (
                                               DIST,
                                               1,
                                               0)
                                            OVER (PARTITION BY PATTERN_ID
                                                  ORDER BY STOP_SEQUENCE)
                                               AS shape_dist_traveled,
                                            NEXT_DISTANCE
                                       FROM (SELECT TS.RUNBOARD_ID,
                                                    TPS.PATTERN_ID,
                                                    TS.STOP_ABBREVIATION,
                                                    TS.STOP_NAME,
                                                    TS.STOP_ID,
                                                    LEAD (
                                                       TPS.STOP_ID,
                                                       1,
                                                       0)
                                                    OVER (
                                                       PARTITION BY PATTERN_ID
                                                       ORDER BY STOP_SEQUENCE)
                                                       AS NEXT_STOP_ID,
                                                    SUM (
                                                       NEXT_DISTANCE)
                                                    OVER (
                                                       PARTITION BY PATTERN_ID
                                                       ORDER BY STOP_SEQUENCE
                                                       ROWS UNBOUNDED PRECEDING)
                                                       AS DIST,
                                                    TPS.STOP_SEQUENCE,
                                                    NEXT_DISTANCE
                                               --TPS.*
                                               FROM TIES_PATTERN_STOPS TPS
                                                    JOIN TIES_STOPS TS
                                                       ON     TPS.RUNBOARD_ID =
                                                                 TS.RUNBOARD_ID
                                                          AND TPS.STOP_ID =
                                                                 TS.STOP_ID
                                              WHERE     TPS.RUNBOARD_ID IN
                                                           (SELECT runboard_id
                                                              FROM runboards)
                                                    AND TPS.STOP_ACTIONS < 3)
                                      WHERE NOT STOP_ID = NEXT_STOP_ID) PSD
                                       ON     GSTM.RUNBOARD_ID =
                                                 PSD.RUNBOARD_ID
                                          AND GSTM.PATTERN_ID = PSD.PATTERN_ID
                                          AND GSTM.STOP_ID = PSD.STOP_ID
                              WHERE gstm.stop_actions < 3)))
   ORDER BY TRIP_ID, STOP_SEQUENCE
;
