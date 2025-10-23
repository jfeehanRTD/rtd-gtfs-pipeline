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
