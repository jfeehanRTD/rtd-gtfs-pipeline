package com.rtd.pipeline.serialization;

import com.google.protobuf.Message;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.serialization.SerializerConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;

/**
 * Custom TypeInformation for Protocol Buffer messages to ensure proper Flink serialization.
 * This handles the serialization of protobuf messages in a way that Flink can manage efficiently.
 */
public class ProtobufTypeInformation<T extends Message> extends TypeInformation<T> {
    
    private final Class<T> protoClass;
    
    public ProtobufTypeInformation(Class<T> protoClass) {
        this.protoClass = protoClass;
    }
    
    @Override
    public boolean isBasicType() {
        return false;
    }
    
    @Override
    public boolean isTupleType() {
        return false;
    }
    
    @Override
    public int getArity() {
        return 1;
    }
    
    @Override
    public int getTotalFields() {
        return 1;
    }
    
    @Override
    public Class<T> getTypeClass() {
        return protoClass;
    }
    
    @Override
    public boolean isKeyType() {
        return false;
    }
    
    @Override
    public TypeSerializer<T> createSerializer(SerializerConfig config) {
        return new ProtobufSerializer<>(protoClass);
    }
    
    @Override
    public String toString() {
        return "ProtobufTypeInformation<" + protoClass.getSimpleName() + ">";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ProtobufTypeInformation<?> that = (ProtobufTypeInformation<?>) obj;
        return protoClass.equals(that.protoClass);
    }
    
    @Override
    public int hashCode() {
        return protoClass.hashCode();
    }
    
    @Override
    public boolean canEqual(Object obj) {
        return obj instanceof ProtobufTypeInformation;
    }
    
    public static <T extends Message> ProtobufTypeInformation<T> create(Class<T> protoClass) {
        return new ProtobufTypeInformation<>(protoClass);
    }
}