# Gradle Build Instructions

This project now supports both Maven and Gradle builds. The Gradle wrapper is included for convenience.

## Prerequisites
- Java 24 (now fully supported with Gradle 8.14)
- No need to install Gradle (wrapper included)

## Important Notes
- Both Gradle and Maven now use Java 24 for compilation
- Gradle 8.14 officially supports Java 24 (class file major version 68)
- Both build systems produce identical output

## Building with Gradle

### Clean and Build
```bash
./gradlew clean build
```

### Compile Only
```bash
./gradlew compileJava
```

### Run Tests
```bash
./gradlew test
```

### Create JAR
```bash
./gradlew jar
```

### Create Fat JAR (with all dependencies)
```bash
./gradlew fatJar
```

## Running Applications with Gradle

### Run Main RTD Pipeline
```bash
./gradlew runRTDPipeline
# OR
./gradlew run
```

### Run Bus SIRI Receiver
```bash
./gradlew runBusReceiver
```

### Run Rail Communication Receiver
```bash
./gradlew runRailReceiver
```

### Run Spring Boot API Server
```bash
./gradlew runSpringBootAPI
```

## Gradle vs Maven

Both build tools are configured to produce the same output:
- Maven: `mvn clean package` → `target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar`
- Gradle: `./gradlew clean build` → `build/libs/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar`

### Fat JAR Locations
- Maven: Use maven-shade-plugin
- Gradle: `./gradlew fatJar` → `build/libs/rtd-gtfs-pipeline-all-1.0-SNAPSHOT.jar`

## Troubleshooting

If you encounter Java version issues:
1. The project is configured to use Java 21 for Gradle builds
2. Maven still uses Java 24 as configured in pom.xml
3. You can override the Java version by setting JAVA_HOME environment variable

## Gradle Wrapper

The Gradle wrapper (gradlew) ensures everyone uses the same Gradle version (8.11.1).
- Unix/Mac: `./gradlew`
- Windows: `gradlew.bat`

No need to install Gradle separately - the wrapper downloads it automatically on first run.