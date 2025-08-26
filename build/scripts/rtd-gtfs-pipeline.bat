@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  rtd-gtfs-pipeline startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and RTD_GTFS_PIPELINE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\rtd-gtfs-pipeline-1.0-SNAPSHOT-plain.jar;%APP_HOME%\lib\flink-table-planner_2.12-2.1.0.jar;%APP_HOME%\lib\flink-table-runtime-2.1.0.jar;%APP_HOME%\lib\flink-table-api-java-bridge-2.1.0.jar;%APP_HOME%\lib\flink-clients-2.1.0.jar;%APP_HOME%\lib\flink-table-api-bridge-base-2.1.0.jar;%APP_HOME%\lib\flink-table-api-scala_2.12-2.1.0.jar;%APP_HOME%\lib\flink-datastream-2.1.0.jar;%APP_HOME%\lib\flink-streaming-java-2.1.0.jar;%APP_HOME%\lib\flink-runtime-2.1.0.jar;%APP_HOME%\lib\flink-table-api-java-2.1.0.jar;%APP_HOME%\lib\flink-table-common-2.1.0.jar;%APP_HOME%\lib\flink-rpc-akka-loader-2.1.0.jar;%APP_HOME%\lib\flink-hadoop-fs-2.1.0.jar;%APP_HOME%\lib\flink-core-2.1.0.jar;%APP_HOME%\lib\flink-connector-files-2.1.0.jar;%APP_HOME%\lib\flink-json-2.1.0.jar;%APP_HOME%\lib\flink-connector-kafka-4.0.0-2.0.jar;%APP_HOME%\lib\flink-connector-datagen-2.1.0.jar;%APP_HOME%\lib\json-path-2.8.0.jar;%APP_HOME%\lib\kafka_2.13-4.0.0.jar;%APP_HOME%\lib\kafka-server-3.8.1.jar;%APP_HOME%\lib\kafka-group-coordinator-4.0.0.jar;%APP_HOME%\lib\kafka-transaction-coordinator-4.0.0.jar;%APP_HOME%\lib\kafka-share-coordinator-4.0.0.jar;%APP_HOME%\lib\kafka-coordinator-common-4.0.0.jar;%APP_HOME%\lib\kafka-metadata-3.8.1.jar;%APP_HOME%\lib\kafka-storage-3.8.1.jar;%APP_HOME%\lib\kafka-storage-api-3.8.1.jar;%APP_HOME%\lib\kafka-raft-3.8.1.jar;%APP_HOME%\lib\kafka-server-common-3.8.1.jar;%APP_HOME%\lib\kafka-group-coordinator-api-4.0.0.jar;%APP_HOME%\lib\kafka-tools-api-4.0.0.jar;%APP_HOME%\lib\kafka-clients-4.0.0.jar;%APP_HOME%\lib\gtfs-realtime-bindings-0.0.4.jar;%APP_HOME%\lib\httpclient-4.5.14.jar;%APP_HOME%\lib\flink-parquet-2.1.0.jar;%APP_HOME%\lib\flink-csv-2.1.0.jar;%APP_HOME%\lib\scala-logging_2.13-3.9.5.jar;%APP_HOME%\lib\scala-compiler-2.12.20.jar;%APP_HOME%\lib\scala-reflect-2.13.15.jar;%APP_HOME%\lib\scala-library-2.12.18.jar;%APP_HOME%\lib\spring-boot-starter-web-3.4.1.jar;%APP_HOME%\lib\spring-boot-starter-json-3.4.1.jar;%APP_HOME%\lib\jackson-dataformat-csv-2.18.2.jar;%APP_HOME%\lib\jackson-dataformat-yaml-2.18.2.jar;%APP_HOME%\lib\jackson-datatype-jsr310-2.18.1.jar;%APP_HOME%\lib\jackson-dataformat-xml-2.18.1.jar;%APP_HOME%\lib\jackson-module-parameter-names-2.18.2.jar;%APP_HOME%\lib\jackson-core-2.18.2.jar;%APP_HOME%\lib\jackson-annotations-2.18.2.jar;%APP_HOME%\lib\jackson-datatype-jdk8-2.18.2.jar;%APP_HOME%\lib\jackson-databind-2.18.1.jar;%APP_HOME%\lib\log4j-slf4j2-impl-2.24.3.jar;%APP_HOME%\lib\flink-datastream-api-2.1.0.jar;%APP_HOME%\lib\flink-core-api-2.1.0.jar;%APP_HOME%\lib\flink-metrics-core-2.1.0.jar;%APP_HOME%\lib\flink-annotations-2.1.0.jar;%APP_HOME%\lib\flink-rpc-core-2.1.0.jar;%APP_HOME%\lib\jose4j-0.9.4.jar;%APP_HOME%\lib\metrics-core-2.2.0.jar;%APP_HOME%\lib\parquet-hadoop-1.15.2.jar;%APP_HOME%\lib\parquet-column-1.15.2.jar;%APP_HOME%\lib\parquet-encoding-1.15.2.jar;%APP_HOME%\lib\parquet-common-1.15.2.jar;%APP_HOME%\lib\slf4j-api-2.0.16.jar;%APP_HOME%\lib\log4j-core-2.24.3.jar;%APP_HOME%\lib\log4j-api-2.24.3.jar;%APP_HOME%\lib\spring-boot-starter-3.4.1.jar;%APP_HOME%\lib\http-20070405.jar;%APP_HOME%\lib\commons-csv-1.12.0.jar;%APP_HOME%\lib\commons-text-1.10.0.jar;%APP_HOME%\lib\commons-compress-1.26.0.jar;%APP_HOME%\lib\commons-lang3-3.17.0.jar;%APP_HOME%\lib\flink-shaded-asm-9-9.6-20.0.jar;%APP_HOME%\lib\flink-shaded-jackson-2.18.2-20.0.jar;%APP_HOME%\lib\snakeyaml-engine-2.6.jar;%APP_HOME%\lib\kryo-5.6.2.jar;%APP_HOME%\lib\commons-validator-1.9.0.jar;%APP_HOME%\lib\commons-beanutils-1.9.4.jar;%APP_HOME%\lib\commons-collections-3.2.2.jar;%APP_HOME%\lib\flink-cep-2.1.0.jar;%APP_HOME%\lib\flink-queryable-state-client-java-2.1.0.jar;%APP_HOME%\lib\flink-shaded-guava-33.4.0-jre-20.0.jar;%APP_HOME%\lib\jsr305-1.3.9.jar;%APP_HOME%\lib\flink-file-sink-common-2.1.0.jar;%APP_HOME%\lib\commons-math3-3.6.1.jar;%APP_HOME%\lib\value-2.8.8.jar;%APP_HOME%\lib\value-annotations-2.8.8.jar;%APP_HOME%\lib\janino-3.1.12.jar;%APP_HOME%\lib\commons-compiler-3.1.12.jar;%APP_HOME%\lib\commons-io-2.17.0.jar;%APP_HOME%\lib\flink-shaded-netty-4.1.100.Final-20.0.jar;%APP_HOME%\lib\flink-shaded-zookeeper-3-3.7.2-20.0.jar;%APP_HOME%\lib\commons-cli-1.5.0.jar;%APP_HOME%\lib\javassist-3.24.0-GA.jar;%APP_HOME%\lib\snappy-java-1.1.10.7.jar;%APP_HOME%\lib\async-profiler-2.9.jar;%APP_HOME%\lib\lz4-java-1.8.0.jar;%APP_HOME%\lib\json-smart-2.5.1.jar;%APP_HOME%\lib\argparse4j-0.7.0.jar;%APP_HOME%\lib\jopt-simple-5.0.4.jar;%APP_HOME%\lib\zstd-jni-1.5.6-6.jar;%APP_HOME%\lib\protobuf-java-3.21.7.jar;%APP_HOME%\lib\httpcore-4.4.16.jar;%APP_HOME%\lib\commons-logging-1.3.2.jar;%APP_HOME%\lib\commons-codec-1.17.1.jar;%APP_HOME%\lib\woodstox-core-7.0.0.jar;%APP_HOME%\lib\stax2-api-4.2.2.jar;%APP_HOME%\lib\spring-boot-starter-tomcat-3.4.1.jar;%APP_HOME%\lib\spring-webmvc-6.2.1.jar;%APP_HOME%\lib\spring-web-6.2.1.jar;%APP_HOME%\lib\spring-boot-autoconfigure-3.4.1.jar;%APP_HOME%\lib\spring-boot-3.4.1.jar;%APP_HOME%\lib\jakarta.annotation-api-2.1.1.jar;%APP_HOME%\lib\spring-context-6.2.1.jar;%APP_HOME%\lib\spring-aop-6.2.1.jar;%APP_HOME%\lib\spring-beans-6.2.1.jar;%APP_HOME%\lib\spring-expression-6.2.1.jar;%APP_HOME%\lib\spring-core-6.2.1.jar;%APP_HOME%\lib\snakeyaml-2.3.jar;%APP_HOME%\lib\reflectasm-1.11.9.jar;%APP_HOME%\lib\objenesis-3.4.jar;%APP_HOME%\lib\minlog-1.3.1.jar;%APP_HOME%\lib\icu4j-67.1.jar;%APP_HOME%\lib\accessors-smart-2.5.1.jar;%APP_HOME%\lib\pcollections-4.0.1.jar;%APP_HOME%\lib\HdrHistogram-2.2.2.jar;%APP_HOME%\lib\re2j-1.7.jar;%APP_HOME%\lib\caffeine-3.1.8.jar;%APP_HOME%\lib\commons-digester-2.1.jar;%APP_HOME%\lib\parquet-format-structures-1.15.2.jar;%APP_HOME%\lib\parquet-jackson-1.15.2.jar;%APP_HOME%\lib\aircompressor-2.0.2.jar;%APP_HOME%\lib\commons-pool-1.6.jar;%APP_HOME%\lib\tomcat-embed-websocket-10.1.34.jar;%APP_HOME%\lib\tomcat-embed-core-10.1.34.jar;%APP_HOME%\lib\tomcat-embed-el-10.1.34.jar;%APP_HOME%\lib\micrometer-observation-1.14.2.jar;%APP_HOME%\lib\spring-jcl-6.2.1.jar;%APP_HOME%\lib\scala-xml_2.12-2.3.0.jar;%APP_HOME%\lib\asm-9.6.jar;%APP_HOME%\lib\checker-qual-3.37.0.jar;%APP_HOME%\lib\error_prone_annotations-2.21.1.jar;%APP_HOME%\lib\javax.annotation-api-1.3.2.jar;%APP_HOME%\lib\micrometer-commons-1.14.2.jar


@rem Execute rtd-gtfs-pipeline
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %RTD_GTFS_PIPELINE_OPTS%  -classpath "%CLASSPATH%" com.rtd.pipeline.RTDStaticDataPipeline %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable RTD_GTFS_PIPELINE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%RTD_GTFS_PIPELINE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
