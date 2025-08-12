workspace "RTD GTFS-RT Data Pipeline" "Real-time transit data processing system using Apache Kafka and Apache Flink" {

    model {
        # External Systems
        rtdApi = softwareSystem "RTD Transit APIs" "RTD Denver's GTFS-RT APIs providing real-time transit data" "External System" {
            tags "External"
        }
        
        staticGtfs = softwareSystem "Static GTFS Data" "Schedule and route information database" "External System" {
            tags "External"
        }
        
        # Users and External Consumers
        transitOperator = person "Transit Operations Center" "Monitors service quality and disruptions"
        mobileUser = person "Mobile App User" "Views real-time vehicle locations"
        dashboardUser = person "Dashboard User" "Analyzes transit performance metrics"
        
        # Main System
        rtdPipeline = softwareSystem "RTD Data Pipeline" "Real-time transit data processing pipeline" {
            
            # Data Ingestion Layer
            producerApp = container "GTFS-RT Producer" "Fetches and publishes raw transit data" "Java Application" {
                tags "Producer"
                
                httpFetcher = component "HTTP Fetcher" "Downloads GTFS-RT feeds from RTD APIs" "Apache HttpComponents"
                protobufParser = component "Protobuf Deserializer" "Parses Protocol Buffer messages" "Google Protobuf"
                kafkaProducer = component "Kafka Producer Client" "Publishes raw data to Kafka topics" "Kafka Client API"
            }
            
            # Message Bus Layer
            kafkaCluster = container "Apache Kafka Cluster" "Distributed message bus for data streaming" "Apache Kafka 4.0.0" {
                tags "MessageBus"
                
                vehiclePositionsTopic = component "rtd.vehicle.positions" "Raw vehicle GPS and status data" "Kafka Topic" {
                    tags "RawData"
                }
                
                tripUpdatesTopic = component "rtd.trip.updates" "Schedule adherence and delays" "Kafka Topic" {
                    tags "RawData"
                }
                
                alertsTopic = component "rtd.alerts" "Service disruption notifications" "Kafka Topic" {
                    tags "RawData"
                }
                
                comprehensiveRoutesTopic = component "rtd.comprehensive.routes" "Enriched vehicle and route data" "Kafka Topic" {
                    tags "ProcessedData"
                }
                
                routeSummaryTopic = component "rtd.route.summary" "Aggregated route statistics" "Kafka Topic" {
                    tags "ProcessedData"
                }
                
                vehicleTrackingTopic = component "rtd.vehicle.tracking" "Enhanced vehicle monitoring data" "Kafka Topic" {
                    tags "ProcessedData"
                }
            }
            
            # Stream Processing Layer
            flinkCluster = container "Apache Flink Cluster" "Stream processing and analytics engine" "Apache Flink 1.19.1" {
                tags "Processing"
                
                vehicleProcessor = component "Vehicle Position Processor" "Processes raw vehicle data streams" "Flink DataStream API" {
                    deserializer = component "Stream Deserializer" "Converts Protobuf to POJOs" "Flink Deserialization"
                    enricher = component "Data Enricher" "Joins with static GTFS data" "Flink SQL/Table API"
                    delayCalculator = component "Delay Calculator" "Computes schedule adherence" "Business Logic"
                }
                
                tripUpdateProcessor = component "Trip Update Processor" "Processes schedule updates" "Flink DataStream API" {
                    scheduleAnalyzer = component "Schedule Analyzer" "Analyzes trip delays" "Flink CEP"
                    cascadeDetector = component "Cascade Detector" "Detects delay propagation" "Pattern Detection"
                }
                
                alertProcessor = component "Alert Processor" "Processes service alerts" "Flink DataStream API" {
                    alertClassifier = component "Alert Classifier" "Categorizes disruptions" "ML Pipeline"
                    impactAnalyzer = component "Impact Analyzer" "Estimates disruption impact" "Business Logic"
                }
                
                aggregator = component "Route Aggregator" "Aggregates metrics by route" "Flink Window Functions" {
                    performanceMetrics = component "Performance Calculator" "On-time performance metrics" "Windowed Aggregation"
                    vehicleCounter = component "Vehicle Counter" "Active vehicle statistics" "State Management"
                }
                
                anomalyDetector = component "Anomaly Detector" "Detects service anomalies" "Flink CEP" {
                    patternMatcher = component "Pattern Matcher" "Historical pattern analysis" "Complex Event Processing"
                    thresholdMonitor = component "Threshold Monitor" "Real-time threshold alerts" "Rule Engine"
                }
            }
            
            # API Layer
            httpApiServer = container "HTTP API Server" "REST API for web applications" "Java HTTP Server" {
                tags "API"
                
                vehicleEndpoint = component "/api/vehicles" "Vehicle positions endpoint" "REST Endpoint"
                healthEndpoint = component "/api/health" "System health check" "REST Endpoint"
                jsonConverter = component "JSON Converter" "Converts data to JSON format" "Jackson"
                corsHandler = component "CORS Handler" "Handles cross-origin requests" "HTTP Filter"
            }
            
            # Web Application Layer
            reactWebApp = container "React Web Application" "Real-time transit map interface" "React/TypeScript" {
                tags "WebApp"
                
                mapComponent = component "OpenStreetMap View" "Interactive transit map" "React-Leaflet"
                vehicleTracker = component "Vehicle Tracker" "Real-time vehicle monitoring" "React Component"
                dataService = component "RTD Data Service" "Data fetching and state management" "TypeScript Service"
                vehicleSelector = component "Vehicle Selector" "Multi-vehicle selection tool" "React Component"
            }
            
            # Database Layer
            staticDataStore = container "Static Data Store" "GTFS schedule and route data" "PostgreSQL" {
                tags "Database"
                
                routesTable = component "Routes Table" "Transit route definitions" "Database Table"
                stopsTable = component "Stops Table" "Transit stop locations" "Database Table"
                schedulesTable = component "Schedules Table" "Service schedules" "Database Table"
            }
        }
        
        # Output Systems
        dashboard = softwareSystem "Analytics Dashboard" "Real-time performance monitoring" "External System" {
            tags "Consumer"
        }
        
        mobileApp = softwareSystem "Mobile Application" "Passenger transit app" "External System" {
            tags "Consumer"
        }
        
        dataWarehouse = softwareSystem "Data Warehouse" "Historical data storage and analysis" "External System" {
            tags "Consumer"
        }
        
        # Relationships - Data Flow
        
        # Ingestion Flow
        rtdApi -> httpFetcher "Provides GTFS-RT feeds" "HTTPS/Protocol Buffers"
        httpFetcher -> protobufParser "Raw PB data"
        protobufParser -> kafkaProducer "Parsed messages"
        kafkaProducer -> vehiclePositionsTopic "Publishes vehicle data"
        kafkaProducer -> tripUpdatesTopic "Publishes trip updates"
        kafkaProducer -> alertsTopic "Publishes alerts"
        
        # Processing Flow - Vehicle Positions
        vehiclePositionsTopic -> vehicleProcessor "Consumes raw data" "Kafka Consumer API"
        staticDataStore -> enricher "Route/stop information" "JDBC"
        vehicleProcessor -> comprehensiveRoutesTopic "Enriched vehicle data"
        vehicleProcessor -> vehicleTrackingTopic "Tracking metrics"
        
        # Processing Flow - Trip Updates
        tripUpdatesTopic -> tripUpdateProcessor "Consumes updates"
        tripUpdateProcessor -> routeSummaryTopic "Route statistics"
        
        # Processing Flow - Alerts
        alertsTopic -> alertProcessor "Consumes alerts"
        alertProcessor -> comprehensiveRoutesTopic "Alert context"
        
        # Aggregation Flow
        comprehensiveRoutesTopic -> aggregator "Route data"
        aggregator -> routeSummaryTopic "Aggregated metrics"
        
        # Anomaly Detection
        vehicleTrackingTopic -> anomalyDetector "Vehicle patterns"
        routeSummaryTopic -> anomalyDetector "Route patterns"
        
        # API Flow
        comprehensiveRoutesTopic -> httpApiServer "Latest vehicle data" "Kafka Consumer"
        httpApiServer -> vehicleEndpoint "Serves data"
        jsonConverter -> vehicleEndpoint "JSON formatting"
        corsHandler -> vehicleEndpoint "CORS headers"
        
        # Web App Flow
        reactWebApp -> httpApiServer "Fetches data" "HTTP/REST"
        vehicleEndpoint -> dataService "Vehicle JSON" "HTTP GET"
        dataService -> mapComponent "Vehicle positions"
        dataService -> vehicleTracker "Tracking data"
        dataService -> vehicleSelector "Vehicle list"
        
        # External Consumers
        comprehensiveRoutesTopic -> dashboard "Real-time metrics" "Kafka Consumer"
        comprehensiveRoutesTopic -> mobileApp "Vehicle locations" "Kafka Consumer"
        routeSummaryTopic -> dataWarehouse "Historical data" "Kafka Connect"
        
        # User Interactions
        transitOperator -> dashboard "Monitors operations"
        dashboardUser -> dashboard "Analyzes metrics"
        mobileUser -> mobileApp "Tracks vehicles"
        dashboardUser -> reactWebApp "Views live map"
        
        # Static Data Management
        staticGtfs -> staticDataStore "GTFS imports" "Batch ETL"
    }

    views {
        # System Context View
        systemContext rtdPipeline "SystemContext" {
            include *
            autoLayout
            title "RTD Pipeline System Context"
            description "High-level view of the RTD GTFS-RT data pipeline and its external dependencies"
        }
        
        # Container View
        container rtdPipeline "Containers" {
            include *
            autoLayout
            title "RTD Pipeline Container View"
            description "Shows the major containers (applications, data stores, message buses) in the RTD pipeline"
        }
        
        # Component View - Kafka Topics
        component kafkaCluster "KafkaComponents" {
            include *
            autoLayout
            title "Kafka Topics Architecture"
            description "Detailed view of Kafka topics and their data flow"
        }
        
        # Component View - Flink Processing
        component flinkCluster "FlinkComponents" {
            include *
            autoLayout
            title "Flink Stream Processing Components"
            description "Detailed view of Flink processing components and data transformations"
        }
        
        # Component View - Producer Application
        component producerApp "ProducerComponents" {
            include *
            autoLayout
            title "GTFS-RT Producer Components"
            description "Components responsible for fetching and publishing raw transit data"
        }
        
        # Component View - Web Application
        component reactWebApp "WebAppComponents" {
            include *
            autoLayout
            title "React Web Application Components"
            description "Frontend components for real-time transit visualization"
        }
        
        # Dynamic View - Vehicle Position Flow
        dynamic rtdPipeline "VehiclePositionFlow" "Vehicle Position Data Flow" {
            rtdApi -> httpFetcher "1. Fetch vehicle positions"
            httpFetcher -> protobufParser "2. Parse Protocol Buffer"
            protobufParser -> kafkaProducer "3. Convert to Kafka message"
            kafkaProducer -> vehiclePositionsTopic "4. Publish to topic"
            vehiclePositionsTopic -> vehicleProcessor "5. Consume and process"
            staticDataStore -> vehicleProcessor "6. Enrich with static data"
            vehicleProcessor -> comprehensiveRoutesTopic "7. Publish enriched data"
            comprehensiveRoutesTopic -> httpApiServer "8. Serve via API"
            reactWebApp -> httpApiServer "9. Fetch for display"
            
            autoLayout
            title "Vehicle Position Processing Flow"
            description "End-to-end flow of vehicle position data through the pipeline"
        }
        
        # Dynamic View - Alert Processing Flow
        dynamic rtdPipeline "AlertFlow" "Service Alert Processing Flow" {
            rtdApi -> httpFetcher "1. Fetch service alerts"
            httpFetcher -> protobufParser "2. Parse alert data"
            protobufParser -> kafkaProducer "3. Create alert message"
            kafkaProducer -> alertsTopic "4. Publish alert"
            alertsTopic -> alertProcessor "5. Process and classify"
            alertProcessor -> comprehensiveRoutesTopic "6. Enrich route data"
            comprehensiveRoutesTopic -> dashboard "7. Display in dashboard"
            
            autoLayout
            title "Alert Processing Flow"
            description "How service disruption alerts flow through the system"
        }
        
        # Deployment View
        deployment rtdPipeline "Production" "ProductionDeployment" {
            deploymentNode "RTD Data Center" {
                tags "Data Center"
                
                deploymentNode "Kafka Cluster" {
                    tags "Kafka"
                    deploymentNode "Kafka Broker 1" {
                        containerInstance kafkaCluster
                    }
                    deploymentNode "Kafka Broker 2" {
                        containerInstance kafkaCluster
                    }
                    deploymentNode "Kafka Broker 3" {
                        containerInstance kafkaCluster
                    }
                }
                
                deploymentNode "Flink Cluster" {
                    tags "Flink"
                    deploymentNode "Job Manager" {
                        containerInstance flinkCluster
                    }
                    deploymentNode "Task Manager 1" {
                        containerInstance flinkCluster
                    }
                    deploymentNode "Task Manager 2" {
                        containerInstance flinkCluster
                    }
                }
                
                deploymentNode "Application Servers" {
                    deploymentNode "Producer Server" {
                        containerInstance producerApp
                    }
                    deploymentNode "API Server" {
                        containerInstance httpApiServer
                    }
                }
                
                deploymentNode "Database Server" {
                    tags "Database"
                    containerInstance staticDataStore
                }
                
                deploymentNode "Web Server" {
                    tags "Web"
                    containerInstance reactWebApp
                }
            }
            
            autoLayout
            title "Production Deployment Architecture"
            description "Physical deployment of the RTD pipeline in production environment"
        }
        
        # Styling
        styles {
            element "External System" {
                background #999999
                color #ffffff
                shape RoundedBox
            }
            
            element "Person" {
                background #08427b
                color #ffffff
                shape Person
            }
            
            element "Software System" {
                background #1168bd
                color #ffffff
            }
            
            element "Container" {
                background #438dd5
                color #ffffff
            }
            
            element "Component" {
                background #85bbf0
                color #000000
            }
            
            element "External" {
                background #999999
                color #ffffff
            }
            
            element "Producer" {
                background #28a745
                color #ffffff
                icon "data:image/svg+xml;base64,..."
            }
            
            element "MessageBus" {
                background #ff6b6b
                color #ffffff
                shape Pipe
            }
            
            element "Processing" {
                background #007acc
                color #ffffff
                shape Hexagon
            }
            
            element "API" {
                background #ffa500
                color #ffffff
                shape RoundedBox
            }
            
            element "WebApp" {
                background #61dafb
                color #000000
                shape WebBrowser
            }
            
            element "Database" {
                background #336791
                color #ffffff
                shape Cylinder
            }
            
            element "Consumer" {
                background #6c757d
                color #ffffff
            }
            
            element "RawData" {
                background #dc3545
                color #ffffff
                icon "data:image/svg+xml;base64,..."
            }
            
            element "ProcessedData" {
                background #28a745
                color #ffffff
                icon "data:image/svg+xml;base64,..."
            }
            
            relationship "Relationship" {
                thickness 2
                color #777777
                dashed false
            }
        }
    }
}