package com.rtd.pipeline.serialization;

import com.google.protobuf.Message;
import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Custom TypeSerializer for Protocol Buffer messages.
 * Uses protobuf's native serialization (toByteArray/parseFrom) instead of Java serialization
 * to avoid Flink's SimpleUdfStreamOperatorFactory issues.
 */
public class ProtobufSerializer<T extends Message> extends TypeSerializer<T> {
    
    private static final Logger LOG = LoggerFactory.getLogger(ProtobufSerializer.class);
    
    private final Class<T> protoClass;
    private transient Method parseFromMethod;
    
    public ProtobufSerializer(Class<T> protoClass) {
        this.protoClass = protoClass;
        initParseFromMethod();
    }
    
    private void initParseFromMethod() {
        try {
            this.parseFromMethod = protoClass.getMethod("parseFrom", byte[].class);
        } catch (NoSuchMethodException e) {
            LOG.error("Failed to find parseFrom method for protobuf class: {}", protoClass.getName(), e);
            throw new RuntimeException("Protobuf class must have parseFrom(byte[]) method", e);
        }
    }
    
    @Override
    public boolean isImmutableType() {
        return true; // Protobuf messages are immutable
    }
    
    @Override
    public TypeSerializer<T> duplicate() {
        return new ProtobufSerializer<>(protoClass);
    }
    
    @Override
    public T createInstance() {
        try {
            // Create default instance using protobuf's getDefaultInstance
            Method getDefaultInstance = protoClass.getMethod("getDefaultInstance");
            return protoClass.cast(getDefaultInstance.invoke(null));
        } catch (Exception e) {
            LOG.error("Failed to create default instance for {}", protoClass.getName(), e);
            return null;
        }
    }
    
    @Override
    public T copy(T from) {
        // Protobuf messages are immutable, so we can return the same instance
        return from;
    }
    
    @Override
    public T copy(T from, T reuse) {
        // Protobuf messages are immutable, so we ignore reuse and return the original
        return from;
    }
    
    @Override
    public int getLength() {
        return -1; // Variable length
    }
    
    @Override
    public void serialize(T record, DataOutputView target) throws IOException {
        if (record == null) {
            target.writeInt(0);
            return;
        }
        
        try {
            byte[] data = record.toByteArray();
            target.writeInt(data.length);
            target.write(data);
        } catch (Exception e) {
            LOG.error("Failed to serialize protobuf message: {}", e.getMessage(), e);
            throw new IOException("Failed to serialize protobuf message", e);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(DataInputView source) throws IOException {
        int length = source.readInt();
        
        if (length == 0) {
            return null;
        }
        
        try {
            byte[] data = new byte[length];
            source.readFully(data);
            
            if (parseFromMethod == null) {
                initParseFromMethod();
            }
            
            return (T) parseFromMethod.invoke(null, data);
        } catch (Exception e) {
            LOG.error("Failed to deserialize protobuf message: {}", e.getMessage(), e);
            throw new IOException("Failed to deserialize protobuf message", e);
        }
    }
    
    @Override
    public T deserialize(T reuse, DataInputView source) throws IOException {
        // Protobuf messages are immutable, so we ignore reuse
        return deserialize(source);
    }
    
    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        int length = source.readInt();
        target.writeInt(length);
        
        if (length > 0) {
            byte[] data = new byte[length];
            source.readFully(data);
            target.write(data);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ProtobufSerializer<?> that = (ProtobufSerializer<?>) obj;
        return protoClass.equals(that.protoClass);
    }
    
    @Override
    public int hashCode() {
        return protoClass.hashCode();
    }
    
    @Override
    public TypeSerializerSnapshot<T> snapshotConfiguration() {
        return new ProtobufSerializerSnapshot<>(protoClass);
    }
    
    /**
     * Snapshot for serializer compatibility checks.
     */
    public static class ProtobufSerializerSnapshot<T extends Message> 
            extends SimpleTypeSerializerSnapshot<T> {
        
        private Class<T> protoClass;
        
        public ProtobufSerializerSnapshot() {
            super(() -> {
                throw new UnsupportedOperationException("Cannot create serializer without protobuf class");
            });
        }
        
        public ProtobufSerializerSnapshot(Class<T> protoClass) {
            super(() -> new ProtobufSerializer<>(protoClass));
            this.protoClass = protoClass;
        }
    }
}