package com.rtd.pipeline.tools;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.ConfigResource;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Kafka Topics Manager using the modern Admin API
 * Replaces command-line tools for Kafka 4.0.0 compatibility
 */
public class KafkaTopicsManager {
    
    private final AdminClient adminClient;
    
    public KafkaTopicsManager(String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        this.adminClient = AdminClient.create(props);
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        String bootstrapServers = "localhost:9092";
        
        // Parse bootstrap-server from args
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--bootstrap-server")) {
                bootstrapServers = args[i + 1];
                break;
            }
        }
        
        KafkaTopicsManager manager = new KafkaTopicsManager(bootstrapServers);
        
        try {
            String command = args[0];
            
            switch (command) {
                case "--list":
                    manager.listTopics();
                    break;
                case "--create":
                    manager.createTopic(args);
                    break;
                case "--delete":  
                    manager.deleteTopic(args);
                    break;
                case "--describe":
                    manager.describeTopic(args);
                    break;
                case "--create-rtd-topics":
                    manager.createRTDTopics();
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            manager.close();
        }
    }
    
    private static void printUsage() {
        System.out.println("RTD Kafka Topics Manager (using Admin API)");
        System.out.println("==========================================");
        System.out.println();
        System.out.println("Usage: java KafkaTopicsManager <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  --list                           - List all topics");
        System.out.println("  --create --topic <name>          - Create a topic");
        System.out.println("  --delete --topic <name>          - Delete a topic");
        System.out.println("  --describe --topic <name>        - Describe a topic");
        System.out.println("  --create-rtd-topics              - Create all RTD topics");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --bootstrap-server <server>      - Kafka bootstrap servers (default: localhost:9092)");
        System.out.println("  --partitions <num>               - Number of partitions (default: 1)");
        System.out.println("  --replication-factor <num>       - Replication factor (default: 1)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java KafkaTopicsManager --list");
        System.out.println("  java KafkaTopicsManager --create --topic test-topic --partitions 3");
        System.out.println("  java KafkaTopicsManager --create-rtd-topics");
    }
    
    public void listTopics() throws ExecutionException, InterruptedException {
        System.out.println("Listing Kafka topics...");
        Set<String> topics = adminClient.listTopics().names().get();
        
        if (topics.isEmpty()) {
            System.out.println("No topics found.");
        } else {
            topics.stream().sorted().forEach(System.out::println);
        }
    }
    
    public void createTopic(String[] args) throws ExecutionException, InterruptedException {
        String topicName = null;
        int partitions = 1;
        short replicationFactor = 1;
        
        // Parse arguments
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--topic":
                    topicName = args[i + 1];
                    break;
                case "--partitions":
                    partitions = Integer.parseInt(args[i + 1]);
                    break;
                case "--replication-factor":
                    replicationFactor = Short.parseShort(args[i + 1]);
                    break;
            }
        }
        
        if (topicName == null) {
            throw new IllegalArgumentException("Topic name is required");
        }
        
        NewTopic newTopic = new NewTopic(topicName, partitions, replicationFactor);
        
        System.out.printf("Creating topic: %s (partitions: %d, replication: %d)%n", 
            topicName, partitions, replicationFactor);
        
        adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
        System.out.println("Topic created successfully.");
    }
    
    public void deleteTopic(String[] args) throws ExecutionException, InterruptedException {
        String topicName = null;
        
        // Parse arguments
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--topic")) {
                topicName = args[i + 1];
                break;
            }
        }
        
        if (topicName == null) {
            throw new IllegalArgumentException("Topic name is required");
        }
        
        System.out.println("Deleting topic: " + topicName);
        adminClient.deleteTopics(Collections.singletonList(topicName)).all().get();
        System.out.println("Topic deleted successfully.");
    }
    
    public void describeTopic(String[] args) throws ExecutionException, InterruptedException {
        String topicName = null;
        
        // Parse arguments
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--topic")) {
                topicName = args[i + 1];
                break;
            }
        }
        
        if (topicName == null) {
            throw new IllegalArgumentException("Topic name is required");
        }
        
        DescribeTopicsResult result = adminClient.describeTopics(Collections.singletonList(topicName));
        TopicDescription description = result.topicNameValues().get(topicName).get();
        
        System.out.println("Topic: " + description.name());
        System.out.println("Partitions: " + description.partitions().size());
        System.out.println("Replication Factor: " + 
            (description.partitions().isEmpty() ? "N/A" : description.partitions().get(0).replicas().size()));
        
        description.partitions().forEach(partition -> {
            System.out.printf("  Partition %d: Leader=%s, Replicas=%s%n",
                partition.partition(),
                partition.leader().id(),
                partition.replicas().stream().map(n -> String.valueOf(n.id())).toArray());
        });
    }
    
    public void createRTDTopics() throws ExecutionException, InterruptedException {
        System.out.println("Creating all RTD GTFS-RT topics...");
        
        List<NewTopic> topics = Arrays.asList(
            new NewTopic("rtd.comprehensive.routes", 3, (short) 1),
            new NewTopic("rtd.route.summary", 1, (short) 1),
            new NewTopic("rtd.vehicle.tracking", 2, (short) 1),
            new NewTopic("rtd.vehicle.positions", 2, (short) 1),
            new NewTopic("rtd.trip.updates", 2, (short) 1),
            new NewTopic("rtd.alerts", 1, (short) 1),
            new NewTopic("rtd.rail.comm", 2, (short) 1)
        );
        
        for (NewTopic topic : topics) {
            System.out.printf("Creating topic: %s (partitions: %d, replication: %d)%n",
                topic.name(), topic.numPartitions(), topic.replicationFactor());
        }
        
        try {
            adminClient.createTopics(topics).all().get();
            System.out.println("All RTD topics created successfully!");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException) {
                System.out.println("Some topics already exist (this is OK).");
            } else {
                throw e;
            }
        }
    }
    
    public void close() {
        if (adminClient != null) {
            adminClient.close();
        }
    }
}