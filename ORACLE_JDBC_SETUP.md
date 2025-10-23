# Oracle JDBC Setup (Optional)

## Only Needed If Extracting from Oracle TIES

If you're using the public RTD feed (recommended), you don't need Oracle JDBC.

If you need to extract data from Oracle TIES schema, add this dependency to `pom.xml`:

## Option 1: Add to pom.xml

```xml
<!-- Add after line 226 (after other dependencies) -->

<!-- Oracle JDBC Driver (optional - only for TIES extraction) -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc11</artifactId>
    <version>23.3.0.23.09</version>
    <scope>provided</scope>
</dependency>
```

## Option 2: Manual JAR Installation

Download Oracle JDBC driver and add to classpath:

```bash
# Download from Oracle
# https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html

# Add to classpath when running
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar:ojdbc11.jar \
  com.rtd.pipeline.gtfs.TIESDataExtractor ...
```

## Option 3: Maven Local Install

```bash
# Download ojdbc11.jar, then:
mvn install:install-file \
  -Dfile=ojdbc11.jar \
  -DgroupId=com.oracle.database.jdbc \
  -DartifactId=ojdbc11 \
  -Dversion=23.3.0.23.09 \
  -Dpackaging=jar

# Then add to pom.xml as shown in Option 1
```

## Verify Installation

```bash
# Build project
mvn clean package

# Test Oracle connection
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.gtfs.TIESDataExtractor \
  jdbc:oracle:thin:@hostname:1521:sid \
  username \
  password \
  RTD \
  /tmp/test

# Should see:
#   "Connecting to Oracle database..."
#   "Connected successfully"
```

## Database Connection Formats

### Standard Connection
```
jdbc:oracle:thin:@hostname:1521:SID
jdbc:oracle:thin:@localhost:1521:ORCL
```

### Service Name
```
jdbc:oracle:thin:@//hostname:1521/service_name
jdbc:oracle:thin:@//db.example.com:1521/rtd.example.com
```

### TNS Names
```
jdbc:oracle:thin:@tns_alias
```

## Environment Variables (Recommended)

Don't hardcode credentials! Use environment variables:

```bash
# Linux/Mac
export DB_URL="jdbc:oracle:thin:@host:1521:sid"
export DB_USER="ties_user"
export DB_PASSWORD="your_password"

# Run extractor
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.gtfs.TIESDataExtractor \
  "$DB_URL" "$DB_USER" "$DB_PASSWORD" \
  "RTD" "/tmp/gtfs"
```

```powershell
# Windows PowerShell
$env:DB_URL = "jdbc:oracle:thin:@host:1521:sid"
$env:DB_USER = "ties_user"
$env:DB_PASSWORD = "your_password"

# Run extractor
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar `
  com.rtd.pipeline.gtfs.TIESDataExtractor `
  $env:DB_URL $env:DB_USER $env:DB_PASSWORD `
  "RTD" "C:\temp\gtfs"
```

## Secure Password Management

### Option 1: Encrypted Properties File
```java
// Read encrypted password
Properties props = new Properties();
props.load(new FileInputStream("secure.properties"));
String password = decrypt(props.getProperty("db.password"));
```

### Option 2: Azure Key Vault
```java
SecretClient secretClient = new SecretClientBuilder()
    .vaultUrl("https://your-vault.vault.azure.net")
    .credential(new DefaultAzureCredentialBuilder().build())
    .buildClient();

String password = secretClient.getSecret("db-password").getValue();
```

### Option 3: AWS Secrets Manager
```java
SecretsManagerClient client = SecretsManagerClient.create();
GetSecretValueRequest request = GetSecretValueRequest.builder()
    .secretId("rtd/db/password")
    .build();

String password = client.getSecretValue(request).secretString();
```

## Troubleshooting

### "ClassNotFoundException: oracle.jdbc.OracleDriver"
**Solution:** Oracle JDBC not in classpath
```bash
# Verify JAR is present
ls -l ~/.m2/repository/com/oracle/database/jdbc/ojdbc11/

# Or add manually to classpath
java -cp "target/*.jar:ojdbc11.jar" TIESDataExtractor ...
```

### "TNS:could not resolve the connect identifier"
**Solution:** Invalid connection string
```bash
# Test connection with SQL*Plus first
sqlplus user/password@hostname:1521/sid

# Then use same format in JDBC URL
jdbc:oracle:thin:@hostname:1521:sid
```

### "ORA-28040: No matching authentication protocol"
**Solution:** Use newer JDBC driver (ojdbc11 for Oracle 19c+)

### "Too many open cursors"
**Solution:** Increase cursors in Oracle
```sql
ALTER SYSTEM SET OPEN_CURSORS = 1000 SCOPE=BOTH;
```

## Performance Tuning

### Increase Fetch Size
```java
// Already set in TIESDataExtractor
stmt.setFetchSize(10000);  // Reduces round trips
```

### Enable Connection Pooling
```java
OracleDataSource ods = new OracleDataSource();
ods.setURL(jdbcUrl);
ods.setUser(username);
ods.setPassword(password);

// Connection pool settings
ods.setConnectionCachingEnabled(true);
ods.setConnectionCacheProperties(props);
```

### Use Parallel Queries
```sql
-- Enable parallel DML in Oracle
ALTER SESSION ENABLE PARALLEL DML;
ALTER SESSION SET PARALLEL_DEGREE_POLICY = AUTO;
```

## Security Checklist

- [ ] Never commit credentials to git
- [ ] Use environment variables or secrets manager
- [ ] Encrypt connection properties files
- [ ] Use read-only database user for extraction
- [ ] Enable SSL/TLS for database connections
- [ ] Rotate passwords regularly
- [ ] Audit database access logs
- [ ] Restrict network access to database

## License Note

Oracle JDBC drivers require acceptance of Oracle Technology Network License Agreement.

For production use, ensure you have appropriate Oracle licensing.

**Alternative:** Use the public RTD GTFS feed and avoid Oracle entirely! ✅
