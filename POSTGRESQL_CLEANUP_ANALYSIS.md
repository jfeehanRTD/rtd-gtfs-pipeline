# PostgreSQL Database Cleanup Analysis
## Minimize Tables and Views for GTFS Generation

---

## Executive Summary

**Current State**:
- Total Tables: 257
- Total Views: 19
- **Required for GTFS**: 10 tables + 16 views
- **Can be Removed**: 247 tables + 3 views (96% of tables, 16% of views)

**Recommendation**: Drop 247 unnecessary tables and 3 unnecessary views to maintain a clean, minimal database focused only on GTFS file generation.

---

## Required Components for GTFS Generation

### Required Views (16 views - KEEP ALL)

These are the 16 GTFS views that directly generate the GTFS feed files:

| View Name | GTFS File | Purpose |
|-----------|-----------|---------|
| `ties_google_agency_vw` | agency.txt | RTD agency information |
| `ties_google_routes_vw` | routes.txt | Route definitions |
| `ties_google_trips_vw` | trips.txt | Trip instances |
| `ties_google_stops_vw` | stops.txt | Stop locations |
| `ties_google_stop_times_vw` | stop_times.txt | Scheduled stop times |
| `ties_google_calendar_vw` | calendar.txt | Service patterns |
| `ties_google_calendar_dates_vw` | calendar_dates.txt | Holiday exceptions |
| `ties_google_feed_info_vw` | feed_info.txt | Feed metadata |
| `ties_google_fare_media_vw` | fare_media.txt | Payment methods |
| `ties_google_fare_products_vw` | fare_products.txt | Fare products |
| `ties_google_fare_leg_rules_vw` | fare_leg_rules.txt | Zone-based fares |
| `ties_google_fare_transfer_rules_vw` | fare_transfer_rules.txt | Transfer rules |
| `ties_google_areas_vw` | areas.txt | Fare zones |
| `ties_google_stop_areas_vw` | stop_areas.txt | Stop-zone mapping |
| `ties_google_networks_vw` | networks.txt | Route networks |
| `ties_google_route_networks_vw` | route_networks.txt | Route-network mapping |

**Action**: ✅ **KEEP ALL 16 VIEWS**

---

### Required Tables (10 tables - KEEP ALL)

These are the underlying tables that the 16 GTFS views query:

| Table Name | Purpose | Used By Views | Rows (Est.) |
|------------|---------|---------------|-------------|
| `ties_google_runboard` | Runboard selection filter | routes, trips, stop_times | ~10 |
| `ties_gtfs_areas` | Fare zone definitions | areas | 81 |
| `ties_gtfs_fare_leg_rules` | Zone-based fare rules | fare_leg_rules | 600 |
| `ties_gtfs_fare_media` | Payment methods | fare_media | 40 |
| `ties_gtfs_fare_products` | Fare products/pricing | fare_products | 572 |
| `ties_gtfs_fare_transfer_rules` | Transfer pricing rules | fare_transfer_rules | 182 |
| `ties_rpt_trips` | Trip/route master data | routes, trips | ~22,000 |
| `ties_service_types` | Service patterns (weekday, etc.) | calendar | ~5 |
| `ties_stops` | Stop location master data | stops, stop_areas | ~136 |
| `ties_trip_events` | Stop time events | stop_times | ~811,000 |

**Total Required Tables**: 10
**Action**: ✅ **KEEP ALL 10 TABLES**

---

## Unnecessary Components to Remove

### Unnecessary Views (3 views - CAN DROP)

These views are NOT used by GTFS generation:

| View Name | Purpose | Used By | Can Drop? |
|-----------|---------|---------|-----------|
| `ties_bus_requirement_vw` | Bus requirement analysis | Unknown/Legacy | ✅ YES |
| `ties_comments_vw` | Comment/annotation display | Unknown/Legacy | ✅ YES |
| `ties_compare_box_numbers_log` | Box number comparison | Unknown/Legacy | ✅ YES |

**Action**: ❌ **DROP THESE 3 VIEWS**

---

### Unnecessary Tables (247 tables - CAN DROP)

The database contains **247 tables that are NOT needed** for GTFS generation. These include:

#### Archive Tables (44 tables)
Historical/backup tables no longer needed:
```
commentgroups_archive          holidays_archive
comments_archive               linegroup_archive
divisions_archive              linestoplabel_archive
fxblocks_archive              linetrace_archive
fxnodeparameters_archive      mastercomments_archive
fxptattributes_archive        masterlinegroup_archive
fxptpoints_archive            masterruntype_archive
fxpttypes_archive             masterservicegroup_archive
fxpttypesattributes_archive   patterntrace_archive
fxrunpieces_archive           polygonattributes_archive
fxtimedconnections_archive    polygons_archive
polygonverts_archive          routepolygonmap_archive
reporttypes_archive           run_archive
run_number_assignment_archive servicegroup_archive
signupperiods_archive         stoppatternflags_archive
stoptimes_archive            ties1_blocks_archive
ties1_run_pieces_archive     ties_blocks_bk
ties_boxes_bk                ties_boxes_bk1
ties_box_assignments_bk      ties_box_assignments_bk1
tracemap_archive             triptimes_archive
```

**Action**: ❌ **DROP ALL ARCHIVE TABLES (44 tables)**

#### Legacy/Unused TIES Tables (203 tables)
Tables from old TIES system components not needed for GTFS:

**Category 1: User/Permission Tables (7 tables)**
```
groups                    permissions
groups_roles             roles
java$options             roles_permissions
users                    users_groups
```

**Category 2: Operational/Scheduling Tables NOT needed for GTFS (150+ tables)**
```
ties_assigned_run_xcept              ties_lightrail_corridor
ties_attachments                     ties_load_runcat_processes
ties_audit_log_link                  ties_merged_nodes
ties_audit_mva                       ties_merged_routes
ties_blocks                          ties_node_line_pub_heading
ties_board_assignments               ties_node_stops
ties_board_assignments_history       ties_pattern_fares
ties_box_assignments                 ties_pattern_stops
ties_boxes                           ties_pattern_stops_node_cd_gate
ties_branches                        ties_pattern_tracemap
ties_calendar_days                   ties_pattern_types
ties_code_symbol_link                ties_patterns
ties_codes_file_downloads            ties_payroll_costing
ties_codes_globals                   ties_payroll_holidays
ties_comment_groups                  ties_payroll_status
ties_comments                        ties_payroll_voted_run_adj
ties_config_parameters               ties_polygon_attribs
ties_contractor_divisions            ties_polygon_routes
ties_contractor_names                ties_polygon_vertices
ties_contractors                     ties_polygons
ties_corridor                        ties_product_routes
ties_deadhead_tracemap               ties_products
ties_deadheads                       ties_report_param_structure
ties_departments                     ties_report_parameters
ties_dot_employee_approval           ties_report_types
ties_dot_hrs_static                  ties_report_urls
ties_dot_parameters                  ties_reports
ties_dtds                            ties_restorable_runs
ties_emp_assignments                 ties_retroactive_pay_list
ties_emp_dot_external                ties_route_dest
ties_emp_shift_extboard_assign       ties_route_destination1
ties_employee_hourly_rates           ties_route_destination1a
ties_employee_payroll_card           ties_route_destination2
ties_employee_payroll_card_day       ties_route_destinations
ties_employee_payroll_card_log       ties_route_destinations_20250516
ties_erp_employee_payroll            ties_route_destinations_jan25
ties_erp_occ_interface_control       ties_route_destinations_may25
ties_etl_mtram_processes             ties_route_destinations_sep24
ties_etl_processes                   ties_route_sequences_temp
ties_etl_processes_prod              ties_route_type_categories
ties_event_types                     ties_route_type_category_link
ties_fare_prices                     ties_route_types
ties_fares                           ties_rpt_comments
ties_floating_periods                ties_rpt_parameter_definition
ties_garage_stop_association         ties_rpt_parameter_validation
ties_garages                         ties_rpt_parameter_visibility
ties_google_special_runboards        ties_rpt_runboard_runs
ties_gtfs_facilities                 ties_rpt_runboards
ties_gtfs_facility_properties        ties_rpt_runs
ties_gtfs_fclt_prpt_def              ties_rpt_trips_costs
ties_gtfs_runboards                  ties_rpt_vehicle_req_types
ties_gtfs_stops_flex                 ties_rt_ptn_node_nodes_temp
ties_gtt_compare_stops               ties_rt_ptn_node_stops_temp
ties_gtt_headway_export              ties_rt_ptns_node_temp
ties_hold_symptoms                   ties_rt_ptns_stop_temp
ties_holidays                        ties_run_assignments
ties_holidays_gtfs                   ties_run_assignments_20251010
ties_hubs                            ties_run_assignments_test
... (and 100+ more)
```

**Category 3: CAD/AVL/GIS Tables (20+ tables)**
```
asa_psas                         mall_announcement
cad_avl_export_2                 mall_announcement1
cad_avl_staging_data            mtram_con_table_partitions
gf_activities                    operator_eb_box_runs_history
gf_activities_all                operator_eb_history
gf_centroid_stops               payroll_run_balance_base
gf_partitions                   plsql_profiler_runs
gf_partitions_all               plsql_profiler_units
gf_patterns                     rpt_traincard_details
gf_patterns_all                 rpt_traincard_headers
gf_points                       rpt_traincard_node_abbrs
gf_points_all                   rpt_trncrd_details
gf_timebands                    rpt_trncrd_headers
gf_timebands_all                rpt_trncrd_node_abbrs
rtd_drug_test
```

**Category 4: System/Development Tables (10 tables)**
```
create$java$lob$table
ties_app_monitor
ties_staging_kronos_day_off
ties_staging_kronos_emp
ties_staging_kronos_sched
ties_station_logs_hist
ties_task_messages
ties_tasks
ties_track_staging
ties_validation_proc_args
ties_validation_procs
ties_web_trip_times_2
```

**Action**: ❌ **DROP ALL 247 UNNECESSARY TABLES**

---

## Cleanup SQL Script

### Step 1: Backup Current Database

```bash
# Create full backup before cleanup
docker exec ties-postgres pg_dump -U ties ties > /tmp/ties_backup_$(date +%Y%m%d).sql

# Verify backup
ls -lh /tmp/ties_backup_*.sql
```

### Step 2: Drop Unnecessary Views (3 views)

```sql
-- Drop 3 unnecessary views
DROP VIEW IF EXISTS ties_bus_requirement_vw CASCADE;
DROP VIEW IF EXISTS ties_comments_vw CASCADE;
DROP VIEW IF EXISTS ties_compare_box_numbers_log CASCADE;

-- Verify only 16 GTFS views remain
SELECT table_name FROM information_schema.views
WHERE table_schema = 'public'
ORDER BY table_name;
-- Expected: 16 rows (all TIES_GOOGLE_* views)
```

### Step 3: Drop Unnecessary Tables (247 tables)

**WARNING**: This will permanently delete 247 tables. Make sure you have a backup!

```sql
-- Drop all archive tables (44 tables)
DROP TABLE IF EXISTS commentgroups_archive CASCADE;
DROP TABLE IF EXISTS comments_archive CASCADE;
DROP TABLE IF EXISTS divisions_archive CASCADE;
DROP TABLE IF EXISTS fxblocks_archive CASCADE;
DROP TABLE IF EXISTS fxnodeparameters_archive CASCADE;
DROP TABLE IF EXISTS fxptattributes_archive CASCADE;
DROP TABLE IF EXISTS fxptpoints_archive CASCADE;
DROP TABLE IF EXISTS fxpttypes_archive CASCADE;
DROP TABLE IF EXISTS fxpttypesattributes_archive CASCADE;
DROP TABLE IF EXISTS fxrunpieces_archive CASCADE;
DROP TABLE IF EXISTS fxtimedconnections_archive CASCADE;
DROP TABLE IF EXISTS holidays_archive CASCADE;
DROP TABLE IF EXISTS linegroup_archive CASCADE;
DROP TABLE IF EXISTS linestoplabel_archive CASCADE;
DROP TABLE IF EXISTS linetrace_archive CASCADE;
DROP TABLE IF EXISTS mastercomments_archive CASCADE;
DROP TABLE IF EXISTS masterlinegroup_archive CASCADE;
DROP TABLE IF EXISTS masterruntype_archive CASCADE;
DROP TABLE IF EXISTS masterservicegroup_archive CASCADE;
DROP TABLE IF EXISTS patterntrace_archive CASCADE;
DROP TABLE IF EXISTS polygonattributes_archive CASCADE;
DROP TABLE IF EXISTS polygons_archive CASCADE;
DROP TABLE IF EXISTS polygonverts_archive CASCADE;
DROP TABLE IF EXISTS reporttypes_archive CASCADE;
DROP TABLE IF EXISTS routepolygonmap_archive CASCADE;
DROP TABLE IF EXISTS run_archive CASCADE;
DROP TABLE IF EXISTS run_number_assignment_archive CASCADE;
DROP TABLE IF EXISTS servicegroup_archive CASCADE;
DROP TABLE IF EXISTS signupperiods_archive CASCADE;
DROP TABLE IF EXISTS stoppatternflags_archive CASCADE;
DROP TABLE IF EXISTS stoptimes_archive CASCADE;
DROP TABLE IF EXISTS ties1_blocks_archive CASCADE;
DROP TABLE IF EXISTS ties1_run_pieces_archive CASCADE;
DROP TABLE IF EXISTS ties_blocks_bk CASCADE;
DROP TABLE IF EXISTS ties_boxes_bk CASCADE;
DROP TABLE IF EXISTS ties_boxes_bk1 CASCADE;
DROP TABLE IF EXISTS ties_box_assignments_bk CASCADE;
DROP TABLE IF EXISTS ties_box_assignments_bk1 CASCADE;
DROP TABLE IF EXISTS tracemap_archive CASCADE;
DROP TABLE IF EXISTS triptimes_archive CASCADE;

-- Drop user/permission tables (7 tables)
DROP TABLE IF EXISTS groups CASCADE;
DROP TABLE IF EXISTS groups_roles CASCADE;
DROP TABLE IF EXISTS java$options CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS roles_permissions CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS users_groups CASCADE;

-- Drop CAD/AVL/GIS tables (20+ tables)
DROP TABLE IF EXISTS asa_psas CASCADE;
DROP TABLE IF EXISTS cad_avl_export_2 CASCADE;
DROP TABLE IF EXISTS cad_avl_staging_data CASCADE;
DROP TABLE IF EXISTS gf_activities CASCADE;
DROP TABLE IF EXISTS gf_activities_all CASCADE;
DROP TABLE IF EXISTS gf_centroid_stops CASCADE;
DROP TABLE IF EXISTS gf_partitions CASCADE;
DROP TABLE IF EXISTS gf_partitions_all CASCADE;
DROP TABLE IF EXISTS gf_patterns CASCADE;
DROP TABLE IF EXISTS gf_patterns_all CASCADE;
DROP TABLE IF EXISTS gf_points CASCADE;
DROP TABLE IF EXISTS gf_points_all CASCADE;
DROP TABLE IF EXISTS gf_timebands CASCADE;
DROP TABLE IF EXISTS gf_timebands_all CASCADE;
DROP TABLE IF EXISTS mall_announcement CASCADE;
DROP TABLE IF EXISTS mall_announcement1 CASCADE;
DROP TABLE IF EXISTS mtram_con_table_partitions CASCADE;
DROP TABLE IF EXISTS operator_eb_box_runs_history CASCADE;
DROP TABLE IF EXISTS operator_eb_history CASCADE;
DROP TABLE IF EXISTS payroll_run_balance_base CASCADE;
DROP TABLE IF EXISTS plsql_profiler_runs CASCADE;
DROP TABLE IF EXISTS plsql_profiler_units CASCADE;
DROP TABLE IF EXISTS rpt_traincard_details CASCADE;
DROP TABLE IF EXISTS rpt_traincard_headers CASCADE;
DROP TABLE IF EXISTS rpt_traincard_node_abbrs CASCADE;
DROP TABLE IF EXISTS rpt_trncrd_details CASCADE;
DROP TABLE IF EXISTS rpt_trncrd_headers CASCADE;
DROP TABLE IF EXISTS rpt_trncrd_node_abbrs CASCADE;
DROP TABLE IF EXISTS rtd_drug_test CASCADE;

-- Drop unnecessary TIES operational tables (150+ tables)
DROP TABLE IF EXISTS create$java$lob$table CASCADE;
DROP TABLE IF EXISTS ties_app_monitor CASCADE;
DROP TABLE IF EXISTS ties_assigned_run_xcept CASCADE;
DROP TABLE IF EXISTS ties_attachments CASCADE;
DROP TABLE IF EXISTS ties_audit_log_link CASCADE;
DROP TABLE IF EXISTS ties_audit_mva CASCADE;
DROP TABLE IF EXISTS ties_blocks CASCADE;
DROP TABLE IF EXISTS ties_board_assignments CASCADE;
DROP TABLE IF EXISTS ties_board_assignments_history CASCADE;
DROP TABLE IF EXISTS ties_box_assignments CASCADE;
DROP TABLE IF EXISTS ties_boxes CASCADE;
DROP TABLE IF EXISTS ties_branches CASCADE;
DROP TABLE IF EXISTS ties_calendar_days CASCADE;
DROP TABLE IF EXISTS ties_code_symbol_link CASCADE;
DROP TABLE IF EXISTS ties_codes_file_downloads CASCADE;
DROP TABLE IF EXISTS ties_codes_globals CASCADE;
DROP TABLE IF EXISTS ties_comment_groups CASCADE;
DROP TABLE IF EXISTS ties_comments CASCADE;
DROP TABLE IF EXISTS ties_config_parameters CASCADE;
DROP TABLE IF EXISTS ties_contractor_divisions CASCADE;
DROP TABLE IF EXISTS ties_contractor_names CASCADE;
DROP TABLE IF EXISTS ties_contractors CASCADE;
DROP TABLE IF EXISTS ties_corridor CASCADE;
DROP TABLE IF EXISTS ties_deadhead_tracemap CASCADE;
DROP TABLE IF EXISTS ties_deadheads CASCADE;
DROP TABLE IF EXISTS ties_departments CASCADE;
DROP TABLE IF EXISTS ties_dot_employee_approval CASCADE;
DROP TABLE IF EXISTS ties_dot_hrs_static CASCADE;
DROP TABLE IF EXISTS ties_dot_parameters CASCADE;
DROP TABLE IF EXISTS ties_dtds CASCADE;
DROP TABLE IF EXISTS ties_emp_assignments CASCADE;
DROP TABLE IF EXISTS ties_emp_dot_external CASCADE;
DROP TABLE IF EXISTS ties_emp_shift_extboard_assign CASCADE;
DROP TABLE IF EXISTS ties_employee_hourly_rates CASCADE;
DROP TABLE IF EXISTS ties_employee_payroll_card CASCADE;
DROP TABLE IF EXISTS ties_employee_payroll_card_day CASCADE;
DROP TABLE IF EXISTS ties_employee_payroll_card_log CASCADE;
DROP TABLE IF EXISTS ties_erp_employee_payroll CASCADE;
DROP TABLE IF EXISTS ties_erp_occ_interface_control CASCADE;
DROP TABLE IF EXISTS ties_etl_mtram_processes CASCADE;
DROP TABLE IF EXISTS ties_etl_processes CASCADE;
DROP TABLE IF EXISTS ties_etl_processes_prod CASCADE;
DROP TABLE IF EXISTS ties_event_types CASCADE;
DROP TABLE IF EXISTS ties_fare_prices CASCADE;
DROP TABLE IF EXISTS ties_fares CASCADE;
DROP TABLE IF EXISTS ties_floating_periods CASCADE;
DROP TABLE IF EXISTS ties_garage_stop_association CASCADE;
DROP TABLE IF EXISTS ties_garages CASCADE;
DROP TABLE IF EXISTS ties_google_special_runboards CASCADE;
DROP TABLE IF EXISTS ties_gtfs_facilities CASCADE;
DROP TABLE IF EXISTS ties_gtfs_facility_properties CASCADE;
DROP TABLE IF EXISTS ties_gtfs_fclt_prpt_def CASCADE;
DROP TABLE IF EXISTS ties_gtfs_runboards CASCADE;
DROP TABLE IF EXISTS ties_gtfs_stops_flex CASCADE;
DROP TABLE IF EXISTS ties_gtt_compare_stops CASCADE;
DROP TABLE IF EXISTS ties_gtt_headway_export CASCADE;
DROP TABLE IF EXISTS ties_hold_symptoms CASCADE;
DROP TABLE IF EXISTS ties_holidays CASCADE;
DROP TABLE IF EXISTS ties_holidays_gtfs CASCADE;
DROP TABLE IF EXISTS ties_hubs CASCADE;
DROP TABLE IF EXISTS ties_lightrail_corridor CASCADE;
DROP TABLE IF EXISTS ties_load_runcat_processes CASCADE;
DROP TABLE IF EXISTS ties_merged_nodes CASCADE;
DROP TABLE IF EXISTS ties_merged_routes CASCADE;
DROP TABLE IF EXISTS ties_node_line_pub_heading CASCADE;
DROP TABLE IF EXISTS ties_node_stops CASCADE;
DROP TABLE IF EXISTS ties_pattern_fares CASCADE;
DROP TABLE IF EXISTS ties_pattern_stops CASCADE;
DROP TABLE IF EXISTS ties_pattern_stops_node_cd_gate CASCADE;
DROP TABLE IF EXISTS ties_pattern_tracemap CASCADE;
DROP TABLE IF EXISTS ties_pattern_types CASCADE;
DROP TABLE IF EXISTS ties_patterns CASCADE;
DROP TABLE IF EXISTS ties_payroll_costing CASCADE;
DROP TABLE IF EXISTS ties_payroll_holidays CASCADE;
DROP TABLE IF EXISTS ties_payroll_status CASCADE;
DROP TABLE IF EXISTS ties_payroll_voted_run_adj CASCADE;
DROP TABLE IF EXISTS ties_polygon_attribs CASCADE;
DROP TABLE IF EXISTS ties_polygon_routes CASCADE;
DROP TABLE IF EXISTS ties_polygon_vertices CASCADE;
DROP TABLE IF EXISTS ties_polygons CASCADE;
DROP TABLE IF EXISTS ties_product_routes CASCADE;
DROP TABLE IF EXISTS ties_products CASCADE;
DROP TABLE IF EXISTS ties_report_param_structure CASCADE;
DROP TABLE IF EXISTS ties_report_parameters CASCADE;
DROP TABLE IF EXISTS ties_report_types CASCADE;
DROP TABLE IF EXISTS ties_report_urls CASCADE;
DROP TABLE IF EXISTS ties_reports CASCADE;
DROP TABLE IF EXISTS ties_restorable_runs CASCADE;
DROP TABLE IF EXISTS ties_retroactive_pay_list CASCADE;
DROP TABLE IF EXISTS ties_route_dest CASCADE;
DROP TABLE IF EXISTS ties_route_destination1 CASCADE;
DROP TABLE IF EXISTS ties_route_destination1a CASCADE;
DROP TABLE IF EXISTS ties_route_destination2 CASCADE;
DROP TABLE IF EXISTS ties_route_destinations CASCADE;
DROP TABLE IF EXISTS ties_route_destinations_20250516 CASCADE;
DROP TABLE IF EXISTS ties_route_destinations_jan25 CASCADE;
DROP TABLE IF EXISTS ties_route_destinations_may25 CASCADE;
DROP TABLE IF EXISTS ties_route_destinations_sep24 CASCADE;
DROP TABLE IF EXISTS ties_route_sequences_temp CASCADE;
DROP TABLE IF EXISTS ties_route_type_categories CASCADE;
DROP TABLE IF EXISTS ties_route_type_category_link CASCADE;
DROP TABLE IF EXISTS ties_route_types CASCADE;
DROP TABLE IF EXISTS ties_rpt_comments CASCADE;
DROP TABLE IF EXISTS ties_rpt_parameter_definition CASCADE;
DROP TABLE IF EXISTS ties_rpt_parameter_validation CASCADE;
DROP TABLE IF EXISTS ties_rpt_parameter_visibility CASCADE;
DROP TABLE IF EXISTS ties_rpt_runboard_runs CASCADE;
DROP TABLE IF EXISTS ties_rpt_runboards CASCADE;
DROP TABLE IF EXISTS ties_rpt_runs CASCADE;
DROP TABLE IF EXISTS ties_rpt_trips_costs CASCADE;
DROP TABLE IF EXISTS ties_rpt_vehicle_req_types CASCADE;
DROP TABLE IF EXISTS ties_rt_ptn_node_nodes_temp CASCADE;
DROP TABLE IF EXISTS ties_rt_ptn_node_stops_temp CASCADE;
DROP TABLE IF EXISTS ties_rt_ptns_node_temp CASCADE;
DROP TABLE IF EXISTS ties_rt_ptns_stop_temp CASCADE;
DROP TABLE IF EXISTS ties_run_assignments CASCADE;
DROP TABLE IF EXISTS ties_run_assignments_20251010 CASCADE;
DROP TABLE IF EXISTS ties_run_assignments_test CASCADE;
DROP TABLE IF EXISTS ties_run_pieces CASCADE;
DROP TABLE IF EXISTS ties_run_types CASCADE;
DROP TABLE IF EXISTS ties_runs CASCADE;
DROP TABLE IF EXISTS ties_signup_vehicletypes CASCADE;
DROP TABLE IF EXISTS ties_special_attachments CASCADE;
DROP TABLE IF EXISTS ties_special_block_stops CASCADE;
DROP TABLE IF EXISTS ties_special_blocks CASCADE;
DROP TABLE IF EXISTS ties_special_comment_texts CASCADE;
DROP TABLE IF EXISTS ties_special_comment_types CASCADE;
DROP TABLE IF EXISTS ties_special_comments CASCADE;
DROP TABLE IF EXISTS ties_special_day_of_week CASCADE;
DROP TABLE IF EXISTS ties_special_days CASCADE;
DROP TABLE IF EXISTS ties_special_event_types CASCADE;
DROP TABLE IF EXISTS ties_special_stops CASCADE;
DROP TABLE IF EXISTS ties_staging_kronos_day_off CASCADE;
DROP TABLE IF EXISTS ties_staging_kronos_emp CASCADE;
DROP TABLE IF EXISTS ties_staging_kronos_sched CASCADE;
DROP TABLE IF EXISTS ties_station_logs_hist CASCADE;
DROP TABLE IF EXISTS ties_stop_fare_areas CASCADE;
DROP TABLE IF EXISTS ties_stop_intersections CASCADE;
DROP TABLE IF EXISTS ties_stop_products CASCADE;
DROP TABLE IF EXISTS ties_stop_segment_dwell CASCADE;
DROP TABLE IF EXISTS ties_stop_segment_time CASCADE;
DROP TABLE IF EXISTS ties_task_messages CASCADE;
DROP TABLE IF EXISTS ties_tasks CASCADE;
DROP TABLE IF EXISTS ties_template_special_runboard CASCADE;
DROP TABLE IF EXISTS ties_timed_connections CASCADE;
DROP TABLE IF EXISTS ties_tracemaps CASCADE;
DROP TABLE IF EXISTS ties_tracemaps_dh_temp CASCADE;
DROP TABLE IF EXISTS ties_tracemaps_shape_temp CASCADE;
DROP TABLE IF EXISTS ties_track_staging CASCADE;
DROP TABLE IF EXISTS ties_trip_consists CASCADE;
DROP TABLE IF EXISTS ties_trip_event_label_rules CASCADE;
DROP TABLE IF EXISTS ties_trip_label_rules CASCADE;
DROP TABLE IF EXISTS ties_trip_time_bands CASCADE;
DROP TABLE IF EXISTS ties_trips CASCADE;
DROP TABLE IF EXISTS ties_uar_foreign_box CASCADE;
DROP TABLE IF EXISTS ties_uar_nodes CASCADE;
DROP TABLE IF EXISTS ties_vac_wk_vote_slot_limits CASCADE;
DROP TABLE IF EXISTS ties_vacation_eligibility CASCADE;
DROP TABLE IF EXISTS ties_vacation_periods CASCADE;
DROP TABLE IF EXISTS ties_vacation_weeks CASCADE;
DROP TABLE IF EXISTS ties_validation_proc_args CASCADE;
DROP TABLE IF EXISTS ties_validation_procs CASCADE;
DROP TABLE IF EXISTS ties_vehicle_categories CASCADE;
DROP TABLE IF EXISTS ties_vehicle_model_matrix CASCADE;
DROP TABLE IF EXISTS ties_vehicle_models CASCADE;
DROP TABLE IF EXISTS ties_vote_schedule_entries CASCADE;
DROP TABLE IF EXISTS ties_vote_voter_criteria CASCADE;
DROP TABLE IF EXISTS ties_web_trip_times_2 CASCADE;
DROP TABLE IF EXISTS ties_year_runboard_day_counts CASCADE;
DROP TABLE IF EXISTS ties_year_runboards CASCADE;

-- Verify only 10 required tables remain
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;
-- Expected: 10 rows
```

### Step 4: Verify Cleanup

```sql
-- Check remaining tables (should be 10)
SELECT COUNT(*) as table_count FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE';

-- Check remaining views (should be 16)
SELECT COUNT(*) as view_count FROM information_schema.views
WHERE table_schema = 'public';

-- List remaining tables (should only see the 10 required tables)
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- Expected output:
-- ties_google_runboard
-- ties_gtfs_areas
-- ties_gtfs_fare_leg_rules
-- ties_gtfs_fare_media
-- ties_gtfs_fare_products
-- ties_gtfs_fare_transfer_rules
-- ties_rpt_trips
-- ties_service_types
-- ties_stops
-- ties_trip_events

-- List remaining views (should only see the 16 GTFS views)
SELECT table_name FROM information_schema.views
WHERE table_schema = 'public'
ORDER BY table_name;

-- Expected output: All 16 TIES_GOOGLE_* views
```

### Step 5: Test GTFS Extraction

```bash
# Run GTFS extraction to verify everything still works
./gradlew runNextGenCorePipeline

# Expected output: All 16 GTFS files generated successfully
```

### Step 6: Reclaim Disk Space

```sql
-- Vacuum database to reclaim disk space
VACUUM FULL;

-- Analyze tables for query optimization
ANALYZE;
```

---

## Expected Results After Cleanup

### Before Cleanup
- **Tables**: 257
- **Views**: 19
- **Database Size**: ~500 MB (estimated)
- **Complexity**: High (many unused components)

### After Cleanup
- **Tables**: 10 (96% reduction)
- **Views**: 16 (16% reduction)
- **Database Size**: ~50-100 MB (80-90% reduction estimated)
- **Complexity**: Minimal (only GTFS-required components)

### Benefits
✅ **Faster Queries** - Less table scanning overhead
✅ **Simpler Maintenance** - Only maintain what's needed
✅ **Reduced Storage** - 80-90% smaller database
✅ **Clearer Purpose** - Database is clearly GTFS-focused
✅ **Easier Backup/Restore** - Smaller backup files
✅ **Better Performance** - Less memory/cache pressure

---

## Automated Cleanup Script

Save this as `cleanup_postgresql.sh`:

```bash
#!/bin/bash

set -e

echo "=== PostgreSQL Database Cleanup for GTFS ==="
echo "This will remove 247 tables and 3 views"
echo ""
read -p "Have you created a backup? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Please create a backup first:"
    echo "docker exec ties-postgres pg_dump -U ties ties > /tmp/ties_backup_\$(date +%Y%m%d).sql"
    exit 1
fi

echo ""
echo "Starting cleanup..."

# Drop unnecessary views
docker exec -i ties-postgres psql -U ties -d ties <<'EOF'
DROP VIEW IF EXISTS ties_bus_requirement_vw CASCADE;
DROP VIEW IF EXISTS ties_comments_vw CASCADE;
DROP VIEW IF EXISTS ties_compare_box_numbers_log CASCADE;
EOF

echo "✅ Dropped 3 unnecessary views"

# Generate and execute DROP statements for all unnecessary tables
docker exec -i ties-postgres psql -U ties -d ties <<'EOF'
DO $$
DECLARE
    r RECORD;
    required_tables TEXT[] := ARRAY[
        'ties_google_runboard',
        'ties_gtfs_areas',
        'ties_gtfs_fare_leg_rules',
        'ties_gtfs_fare_media',
        'ties_gtfs_fare_products',
        'ties_gtfs_fare_transfer_rules',
        'ties_rpt_trips',
        'ties_service_types',
        'ties_stops',
        'ties_trip_events'
    ];
BEGIN
    FOR r IN
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = 'public'
        AND table_type = 'BASE TABLE'
        AND table_name <> ALL(required_tables)
    LOOP
        EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.table_name) || ' CASCADE';
        RAISE NOTICE 'Dropped table: %', r.table_name;
    END LOOP;
END $$;
EOF

echo "✅ Dropped unnecessary tables"

# Verify cleanup
echo ""
echo "Verifying cleanup..."
table_count=$(docker exec ties-postgres psql -U ties -d ties -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';")
view_count=$(docker exec ties-postgres psql -U ties -d ties -t -c "SELECT COUNT(*) FROM information_schema.views WHERE table_schema = 'public';")

echo "Remaining tables: $table_count (expected: 10)"
echo "Remaining views: $view_count (expected: 16)"

# Vacuum
echo ""
echo "Reclaiming disk space..."
docker exec ties-postgres psql -U ties -d ties -c "VACUUM FULL; ANALYZE;"

echo ""
echo "=== Cleanup Complete ==="
echo "✅ Database cleaned up successfully"
echo ""
echo "Next steps:"
echo "1. Test GTFS extraction: ./gradlew runNextGenCorePipeline"
echo "2. Verify all 16 GTFS files are generated"
echo "3. If successful, delete backup file"
```

Make executable and run:
```bash
chmod +x cleanup_postgresql.sh
./cleanup_postgresql.sh
```

---

## Summary

**Minimal GTFS Database**:
- ✅ **10 Tables** - Only data needed for GTFS views
- ✅ **16 Views** - TIES_GOOGLE_* views for GTFS generation
- ✅ **96% Smaller** - Removed 247 unnecessary tables
- ✅ **Cleaner** - Single purpose: GTFS file generation
- ✅ **Faster** - Better query performance
- ✅ **Simpler** - Easier to understand and maintain

**Recommendation**: Execute the cleanup script to maintain a lean, purpose-built GTFS database.

---

**Document Version**: 1.0.0
**Last Updated**: 2025-10-23
**Status**: Ready for Execution
