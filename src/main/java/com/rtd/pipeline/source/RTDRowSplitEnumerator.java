package com.rtd.pipeline.source;

import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SplitEnumerator implementation for RTD Row Source.
 */
public class RTDRowSplitEnumerator implements SplitEnumerator<RTDRowSplit, List<RTDRowSplit>> {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDRowSplitEnumerator.class);
    
    private final SplitEnumeratorContext<RTDRowSplit> context;
    private final List<RTDRowSplit> pendingSplits;
    private final AtomicInteger nextSplitId = new AtomicInteger(0);
    
    public RTDRowSplitEnumerator(SplitEnumeratorContext<RTDRowSplit> context, List<RTDRowSplit> initialSplits) {
        this.context = context;
        this.pendingSplits = new ArrayList<>(initialSplits);
    }
    
    @Override
    public void start() {
        LOG.info("Starting RTD Row Split Enumerator");
        assignPendingSplits();
    }
    
    @Override
    public void handleSplitRequest(int subtaskId, String requesterHostname) {
        LOG.debug("Handling split request from subtask {}", subtaskId);
        assignPendingSplits();
    }
    
    @Override
    public void addSplitsBack(List<RTDRowSplit> splits, int subtaskId) {
        LOG.info("Adding back {} splits from subtask {}", splits.size(), subtaskId);
        pendingSplits.addAll(splits);
        assignPendingSplits();
    }
    
    @Override
    public void addReader(int subtaskId) {
        LOG.info("Adding reader for subtask {}", subtaskId);
        assignPendingSplits();
    }
    
    @Override
    public List<RTDRowSplit> snapshotState(long checkpointId) throws Exception {
        LOG.debug("Snapshotting enumerator state for checkpoint {}", checkpointId);
        return new ArrayList<>(pendingSplits);
    }
    
    @Override
    public void close() throws IOException {
        LOG.info("Closing RTD Row Split Enumerator");
        pendingSplits.clear();
    }
    
    private void assignPendingSplits() {
        if (!pendingSplits.isEmpty() && context.registeredReaders().size() > 0) {
            // Assign one split to each available reader
            for (Integer readerId : context.registeredReaders().keySet()) {
                if (!pendingSplits.isEmpty()) {
                    RTDRowSplit split = pendingSplits.remove(0);
                    context.assignSplit(split, readerId);
                    LOG.info("Assigned split {} to reader {}", split.splitId(), readerId);
                }
            }
        }
    }
    
    /**
     * Serializer for enumerator checkpoint state.
     */
    public static class RTDRowEnumStateSerializer implements SimpleVersionedSerializer<List<RTDRowSplit>> {
        
        @Override
        public int getVersion() {
            return 1;
        }
        
        @Override
        public byte[] serialize(List<RTDRowSplit> splits) throws IOException {
            // Simple serialization - just the count and split IDs
            StringBuilder sb = new StringBuilder();
            sb.append(splits.size()).append(";");
            for (RTDRowSplit split : splits) {
                sb.append(split.splitId()).append(",");
            }
            return sb.toString().getBytes();
        }
        
        @Override
        public List<RTDRowSplit> deserialize(int version, byte[] serialized) throws IOException {
            if (version != 1) {
                throw new IOException("Unknown version: " + version);
            }
            
            String data = new String(serialized);
            String[] parts = data.split(";");
            int count = Integer.parseInt(parts[0]);
            
            List<RTDRowSplit> splits = new ArrayList<>(count);
            if (count > 0 && parts.length > 1) {
                String[] splitIds = parts[1].split(",");
                for (int i = 0; i < count && i < splitIds.length; i++) {
                    if (!splitIds[i].trim().isEmpty()) {
                        splits.add(new RTDRowSplit(splitIds[i].trim()));
                    }
                }
            }
            
            return splits;
        }
    }
}