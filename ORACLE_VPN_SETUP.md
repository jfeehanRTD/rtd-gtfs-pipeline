# Oracle TIES Connection Setup (VPN Access)

## Quick Setup

Your Oracle TIES database is remote and requires VPN access. Here's how to configure the connection:

### Step 1: Get Oracle Connection Details

You need these from your Oracle DBA or connection documentation:

```
Hostname: _____________________ (e.g., ties-db.company.com)
Port:     _____________________ (usually 1521)
Service:  _____________________ (e.g., TIESPROD, TIESDEV, or TIES)
Username: _____________________ (usually TIES)
Password: _____________________
```

### Step 2: Test VPN Connection

Before running migration, ensure VPN is connected and you can reach the Oracle server:

```bash
# Check if VPN is active
# (Look for your company's VPN software)

# Test Oracle host connectivity
ping <oracle-hostname>

# Test Oracle port (requires nc/netcat)
nc -zv <oracle-hostname> 1521
```

### Step 3: Configure Environment Variables

Create a configuration file with your Oracle connection details:

```bash
# Create config file
cat > ~/ties-oracle-config.sh <<'EOF'
#!/bin/bash

# Oracle TIES Connection (Remote via VPN)
export ORACLE_TIES_URL="jdbc:oracle:thin:@<hostname>:1521/<service_name>"
export ORACLE_TIES_USER="TIES"
export ORACLE_TIES_PASSWORD="<your_password>"

# PostgreSQL TIES Connection (Local)
export POSTGRES_TIES_URL="jdbc:postgresql://localhost:5432/ties"
export POSTGRES_TIES_USER="ties"
export POSTGRES_TIES_PASSWORD="TiesPassword123"

echo "✓ Oracle TIES connection configured for: $ORACLE_TIES_URL"
EOF

chmod +x ~/ties-oracle-config.sh
```

### Step 4: Load Configuration and Run Migration

```bash
# Load config
source ~/ties-oracle-config.sh

# Verify environment variables are set
echo "Oracle URL: $ORACLE_TIES_URL"
echo "Oracle User: $ORACLE_TIES_USER"

# Run migration
cd ~/demo/rtd-gtfs-pipeline-refArch1
./gradlew runTIESMigrationALL
```

---

## Common Oracle Connection Formats

### Format 1: Service Name (Recommended)
```bash
export ORACLE_TIES_URL="jdbc:oracle:thin:@hostname:1521/TIESPROD"
```

### Format 2: SID (Legacy)
```bash
export ORACLE_TIES_URL="jdbc:oracle:thin:@hostname:1521:TIESSID"
```

### Format 3: TNS Entry
```bash
export ORACLE_TIES_URL="jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=hostname)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=TIESPROD)))"
```

---

## Test Oracle Connection

Before running the full migration, test your connection:

```bash
# Load config
source ~/ties-oracle-config.sh

# Test with sqlplus (if installed)
sqlplus TIES/<password>@<hostname>:1521/<service_name> <<EOF
SELECT 'Connection successful!' FROM dual;
SELECT table_name FROM user_tables WHERE table_name LIKE 'TIES_GTFS%';
EXIT;
EOF
```

If you don't have sqlplus, use our Java test utility (see below).

---

## Quick Connection Test Utility

Create a simple test to verify Oracle connectivity:

```bash
cd ~/demo/rtd-gtfs-pipeline-refArch1

# Create test file
cat > test-oracle-connection.sh <<'EOF'
#!/bin/bash
source ~/ties-oracle-config.sh

echo "Testing Oracle connection..."
echo "URL: $ORACLE_TIES_URL"
echo "User: $ORACLE_TIES_USER"

./gradlew runTIESMigration -Pagency=RTD --info 2>&1 | head -50
EOF

chmod +x test-oracle-connection.sh
./test-oracle-connection.sh
```

---

## Example: RTD Production Setup

If you're connecting to RTD's production Oracle TIES database:

```bash
# Example (adjust for your actual values)
cat > ~/ties-oracle-config.sh <<'EOF'
#!/bin/bash

# Oracle TIES @ RTD (via VPN)
export ORACLE_TIES_URL="jdbc:oracle:thin:@ties-prod.rtd-denver.com:1521/TIESPROD"
export ORACLE_TIES_USER="TIES"
export ORACLE_TIES_PASSWORD="YourSecurePassword"

# PostgreSQL TIES (Local)
export POSTGRES_TIES_URL="jdbc:postgresql://localhost:5432/ties"
export POSTGRES_TIES_USER="ties"
export POSTGRES_TIES_PASSWORD="TiesPassword123"

echo "✓ Configured for RTD Production TIES"
EOF

chmod 600 ~/ties-oracle-config.sh  # Secure the file
source ~/ties-oracle-config.sh
```

---

## Troubleshooting

### Error: ORA-12505 (SID not registered)

**Problem:** Using SID syntax when service name is required

**Solution:** Change from `:FREEPDB1` to `/SERVICENAME`

```bash
# Wrong (SID syntax)
jdbc:oracle:thin:@hostname:1521:FREEPDB1

# Right (Service name syntax)
jdbc:oracle:thin:@hostname:1521/TIESPROD
```

### Error: Connection timeout

**Problem:** VPN not connected or firewall blocking

**Solution:**
1. Verify VPN is connected
2. Test connectivity: `ping <oracle-hostname>`
3. Test port: `nc -zv <oracle-hostname> 1521`

### Error: ORA-01017 (Invalid username/password)

**Problem:** Incorrect credentials

**Solution:**
1. Verify username (usually `TIES`)
2. Verify password (check with DBA)
3. Ensure no special characters need escaping

### Error: Network adapter could not establish connection

**Problem:** Hostname not resolvable or VPN issue

**Solution:**
1. Check `/etc/hosts` or DNS
2. Verify VPN routes to database network
3. Try IP address instead of hostname

---

## Security Best Practices

### 1. Use Environment Variables (Don't Hardcode)

✅ **Good:**
```bash
source ~/ties-oracle-config.sh
./gradlew runTIESMigrationALL
```

❌ **Bad:**
```bash
# Don't put passwords in gradle.properties!
```

### 2. Secure Configuration File

```bash
# Make config file read-only for your user
chmod 600 ~/ties-oracle-config.sh

# Don't commit to git
echo "ties-oracle-config.sh" >> .gitignore
```

### 3. Use Read-Only Oracle User (If Available)

If your DBA provides a read-only user for migration:

```bash
export ORACLE_TIES_USER="TIES_READONLY"
export ORACLE_TIES_PASSWORD="ReadOnlyPassword"
```

---

## Complete Migration Workflow (VPN)

```bash
# 1. Connect VPN
# (Use your company's VPN client)

# 2. Verify connectivity
ping <oracle-hostname>

# 3. Load Oracle config
source ~/ties-oracle-config.sh

# 4. Verify PostgreSQL is running
docker ps | grep ties-postgres

# 5. Test Oracle connection (optional but recommended)
# See "Test Oracle Connection" section above

# 6. Run migration
cd ~/demo/rtd-gtfs-pipeline-refArch1
./gradlew runTIESMigrationALL

# 7. Verify PostgreSQL has data
docker exec ties-postgres psql -U ties -d ties -c \
  "SELECT COUNT(*) FROM ties_gtfs_fare_products;"

# 8. Run GTFS extraction
./gradlew runTIESFarePipelineRTD
```

---

## Next Steps

1. **Get Oracle connection details** from your DBA or connection documentation
2. **Create** `~/ties-oracle-config.sh` with correct values
3. **Test connection** before full migration
4. **Run migration** after successful test

---

## Need Help?

If you need to find your Oracle connection details:

1. Check existing Oracle connection tools (SQL Developer, DBeaver, etc.)
2. Look for `tnsnames.ora` file
3. Contact your Oracle DBA
4. Check RTD/CDOT documentation
