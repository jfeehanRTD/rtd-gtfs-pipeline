# RTD GTFS Pipeline - Exploration Documentation Index

## Overview

This project has been thoroughly explored at "very thorough" depth. Three comprehensive documentation files have been generated to help you understand the codebase and implement the GTFS fare data extraction Flink job.

## Documentation Files

### 1. PROJECT_ANALYSIS.md (23KB, 758 lines)
**Comprehensive technical reference for the entire project**

**Contents:**
- Project overview and directory structure
- Build system (Gradle 8.14, Java 24)
- Apache Flink 2.1.0 architecture and usage
- Main pipeline classes analysis
- Database connectivity patterns (PostgreSQL, Oracle)
- GTFS data models (15+ classes documented)
- Configuration file reference
- Package structure and naming conventions
- Data flow patterns (4 documented patterns)
- Java/JDBC patterns
- Testing infrastructure
- Real-time data sources
- Known issues and limitations
- Build and run quick reference

**Use this for:**
- Understanding how the project is structured
- Learning Flink patterns used in the codebase
- Understanding database connectivity approaches
- Reviewing GTFS data model classes
- Configuration reference

**Key sections:**
- Section 3: Apache Flink Architecture
- Section 4: Database Connectivity
- Section 5: GTFS Data Models
- Section 8: Package Structure

---

### 2. FARE_PIPELINE_GUIDE.md (14KB)
**Step-by-step implementation guide for the new Flink job**

**Contents:**
- Complete GTFSFareDataExtractionPipeline.java template (300+ lines)
- Build task configuration
- Database query patterns
- Mapper implementations (FareProduct, FareMedia, FareLegRule)
- Running the pipeline
- Integration with existing code
- Key database tables for fare data
- CSV output format specifications
- Testing examples
- Configuration examples
- Integration checklist

**Use this for:**
- Creating the new fare data extraction pipeline
- Copy-paste code templates
- Database query examples
- Build configuration setup
- Testing and validation

**Quick start:**
1. Copy the template code from section 1
2. Update database credentials
3. Add build task from section 2
4. Run: `./gradlew runGTFSFarePipeline`

---

### 3. EXPLORATION_SUMMARY.txt (11KB)
**Quick reference guide - key findings and next steps**

**Contents:**
- Document overview
- Key findings summary
- Build system overview
- Apache Flink configuration
- Database connectivity summary
- Complete list of GTFS models
- Fare-related code locations
- Database connector overview
- Flink data sources list
- Main pipelines list
- Kafka topics configuration
- Configuration files list
- Testing infrastructure summary
- Package structure overview
- Implementation recommendations
- Gradle build commands (quick reference)
- Database connection examples
- Performance notes
- File locations (absolute paths)
- Next steps for implementation

**Use this for:**
- Quick reference during development
- Command-line reference
- Quick lookup of class locations
- Understanding current architecture
- Planning implementation approach

---

## Quick Navigation

### I need to understand...

**...how Flink is used in this project**
→ Read: PROJECT_ANALYSIS.md, Section 3 (Apache Flink Architecture)

**...how to connect to the database**
→ Read: PROJECT_ANALYSIS.md, Section 4 (Database Connectivity Patterns)

**...what GTFS models exist**
→ Read: PROJECT_ANALYSIS.md, Section 5 (GTFS Data Models)

**...how to build a new Flink job**
→ Read: FARE_PIPELINE_GUIDE.md, Sections 1-2

**...what database tables to query**
→ Read: FARE_PIPELINE_GUIDE.md, Section 6

**...how to run the pipeline**
→ Read: EXPLORATION_SUMMARY.txt, Gradle Build Commands section

**...what's already been done for fare data**
→ Read: PROJECT_ANALYSIS.md, Section 6 (Existing GTFS Fare-Related Code)

**...the project file locations**
→ Read: EXPLORATION_SUMMARY.txt, File Locations section

**...next implementation steps**
→ Read: EXPLORATION_SUMMARY.txt, Next Steps section

---

## Key Findings Summary

### Build System
- **Build Tool**: Gradle 8.14 (with Maven support)
- **Java Version**: Java 24 with preview features
- **Main Class**: com.rtd.pipeline.RTDStaticDataPipeline
- **Output**: Fat JAR with all dependencies

### Flink Setup
- **Version**: Apache Flink 2.1.0
- **Kafka**: 4.0.0 connector for Flink 2.x
- **Checkpointing**: 60-second intervals
- **Parallelism**: 1 (single worker, appropriate for GTFS data)

### Database Options
1. **PostgreSQL MTRAM** (primary)
   - JDBC: `jdbc:postgresql://localhost:5432/mtram`
   - User: postgres
   - Connector: MTRAMPostgresConnector.java

2. **Oracle TIES** (legacy)
   - JDBC: `jdbc:oracle:thin:@host:1521:sid`
   - Extractor: TIESDataExtractor.java

3. **MTRAM API** (alternative)
   - HTTP-based, no JDBC required
   - Connector: MTRAMDataConnector.java

### GTFS Fare Models Available
- GTFSFareProduct.java ✓ (has CSV conversion methods)
- GTFSFareMedia.java ✓
- GTFSFareLegRule.java ✓
- GTFSFareTransferRule.java ✓
- GTFSFareAttribute.java (legacy v1)
- GTFSFareRule.java (legacy v1)

### Existing Fare-Related Code
- **Validation**: RTDFareValidator.java
- **Export**: FareDataExporter.java
- **Issues**: 305 stops with unauthorized airport fares (documented)

---

## Implementation Roadmap

### Step 1: Preparation
- [ ] Read PROJECT_ANALYSIS.md sections 1-5
- [ ] Read FARE_PIPELINE_GUIDE.md sections 1-2
- [ ] Set up PostgreSQL MTRAM connection
- [ ] Verify database connectivity

### Step 2: Create Pipeline
- [ ] Copy GTFSFareDataExtractionPipeline template from guide
- [ ] Implement FareDataSource class
- [ ] Create mapper functions (FareProduct, FareMedia, FareLegRule)
- [ ] Add FileSink configuration

### Step 3: Integration
- [ ] Add runGTFSFarePipeline task to build.gradle
- [ ] Configure database credentials
- [ ] Import existing GTFSFare* models
- [ ] Set up logging and error handling

### Step 4: Testing
- [ ] Run unit tests for CSV conversion
- [ ] Test database connectivity
- [ ] Validate CSV output format
- [ ] Test data retention policies

### Step 5: Deployment
- [ ] Build fat JAR: `./gradlew fatJar`
- [ ] Run pipeline: `./gradlew runGTFSFarePipeline`
- [ ] Verify output in data/gtfs-fare/
- [ ] Check logs for errors

---

## File Locations (Absolute Paths)

### Documentation Files
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/PROJECT_ANALYSIS.md`
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/FARE_PIPELINE_GUIDE.md`
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/EXPLORATION_SUMMARY.txt`

### Key Source Files
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/src/main/java/com/rtd/pipeline/mtram/MTRAMPostgresConnector.java`
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/src/main/java/com/rtd/pipeline/gtfs/GTFSDataProcessor.java`
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/src/main/java/com/rtd/pipeline/WorkingGTFSRTPipeline.java`
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/src/main/java/com/rtd/pipeline/RTDBusCommPipeline.java`

### GTFS Models
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/src/main/java/com/rtd/pipeline/gtfs/model/`

### Build Configuration
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/build.gradle`
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/config/gtfs-rt.properties`
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/.env.example`

---

## Technologies & Versions

| Component | Version | Purpose |
|-----------|---------|---------|
| Apache Flink | 2.1.0 | Stream processing |
| Kafka | 4.0.0 | Message broker |
| PostgreSQL | (varies) | MTRAM database |
| Oracle | (varies) | TIES database (legacy) |
| Java | 24 | JVM runtime |
| Gradle | 8.14 | Build system |
| Spring Boot | 3.4.1 | REST API framework |
| Jackson | 2.18.1 | JSON/XML processing |
| SLF4J | 2.0.16 | Logging |
| JUnit | 5.11.0 | Testing |
| GTFS-RT | 0.0.4 | Protobuf bindings |

---

## Common Commands

### Build
```bash
./gradlew clean build          # Full build with tests
./gradlew fatJar               # Create fat JAR
./gradlew compileJava          # Compile only
./gradlew test                 # Run tests
```

### Run Pipelines
```bash
./gradlew runRTDPipeline       # Main static data pipeline
./gradlew runGTFSRTPipeline    # GTFS-RT generator
./gradlew runStaticPipeline    # Static data processor
./gradlew runGTFSFarePipeline  # (new) Fare data extraction
```

### Services
```bash
./gradlew startLRGPS           # Light rail GPS service
./gradlew startSIRI            # Bus SIRI service
./gradlew startRailComm        # Rail communication service
./gradlew runAPIServer         # Spring Boot API server
```

### Utilities
```bash
./gradlew rtdHelp              # Show all RTD tasks
./gradlew checkServices        # Check service status
./gradlew checkAllServices     # Check all services
```

---

## Contact & References

### Documentation Cross-References
- **Flink Docs**: https://flink.apache.org/
- **GTFS Spec**: https://gtfs.org/
- **PostgreSQL JDBC**: https://jdbc.postgresql.org/
- **Gradle Guide**: https://gradle.org/guides/

### Project Configuration
- TIS Proxy settings: See `.env.example`
- GTFS-RT configuration: See `config/gtfs-rt.properties`
- Query client settings: See `config/query-client.properties`

### Test Data
- Mock SIRI data: See `src/test/resources/`
- Rail communication mock: See `bin/test/mock-data/`
- Test utilities: See `src/test/java/com/rtd/pipeline/testutils/`

---

## Notes

This exploration was performed at "very thorough" depth, examining:
- 50+ Java source files
- Complete build configuration
- Database connectors and patterns
- Flink pipeline implementations
- GTFS data models
- Configuration files
- Test infrastructure

All findings are documented in the three files listed above, organized for easy reference and implementation.

---

**Exploration Completed**: October 22, 2025
**Status**: Ready for implementation
**Next Action**: Start with PROJECT_ANALYSIS.md, Section 3

