package com.rtd.pipeline.source;

import org.apache.flink.api.connector.source.SourceSplit;
import org.apache.flink.core.io.SimpleVersionedSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A simple split for RTD Row Source.
 */
public class RTDRowSplit implements SourceSplit {
    
    private final String splitId;
    
    public RTDRowSplit(String splitId) {
        this.splitId = splitId;
    }
    
    @Override
    public String splitId() {
        return splitId;
    }
    
    @Override
    public String toString() {
        return "RTDRowSplit{" + "splitId='" + splitId + '\'' + '}';
    }
    
    /**
     * Serializer for RTDRowSplit.
     */
    public static class RTDRowSplitSerializer implements SimpleVersionedSerializer<RTDRowSplit> {
        
        @Override
        public int getVersion() {
            return 1;
        }
        
        @Override
        public byte[] serialize(RTDRowSplit split) throws IOException {
            return split.splitId().getBytes(StandardCharsets.UTF_8);
        }
        
        @Override
        public RTDRowSplit deserialize(int version, byte[] serialized) throws IOException {
            if (version != 1) {
                throw new IOException("Unknown version: " + version);
            }
            String splitId = new String(serialized, StandardCharsets.UTF_8);
            return new RTDRowSplit(splitId);
        }
    }
}