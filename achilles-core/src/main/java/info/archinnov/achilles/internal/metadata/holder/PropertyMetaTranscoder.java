package info.archinnov.achilles.internal.metadata.holder;

import info.archinnov.achilles.exception.AchillesException;
import info.archinnov.achilles.internal.persistence.operations.InternalCounterImpl;
import info.archinnov.achilles.internal.validation.Validator;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PropertyMetaTranscoder extends PropertyMetaView {

    private static final Logger log = LoggerFactory.getLogger(PropertyMetaTranscoder.class);

    protected PropertyMetaTranscoder(PropertyMeta meta) {
        super(meta);
    }

    public Object decodeFromComponents(List<?> components) {
        log.trace("Decode CQL3 components {} into compound primary key {} for entity class {}", components, meta.getPropertyName(), meta.getEntityClassName());
        Validator.validateTrue(meta.type() == PropertyType.EMBEDDED_ID, "Cannot decode components '%s' for the property '%s' which is not a compound primary key", components, meta.propertyName);
        if (CollectionUtils.isEmpty(components)) {
            return components;
        }
        final Object newInstance = meta.forValues().instantiate();
        final List<PropertyMeta> propertyMetas = meta.getEmbeddedIdProperties().propertyMetas;

        Validator.validateTrue(components.size() == propertyMetas.size(), "There should be exactly '%s' Cassandra columns to decode into an '%s' instance", propertyMetas.size(), newInstance.getClass().getCanonicalName());

        for (int i = 0; i < propertyMetas.size(); i++) {
            final PropertyMeta componentMeta = propertyMetas.get(i);
            final Object decodedValue = componentMeta.forTranscoding().decodeFromCassandra(components.get(i));
            componentMeta.forValues().setValueToField(newInstance,decodedValue);
        }
        return newInstance;
    }

    Object decodeFromCassandra(Object fromCassandra) {
        log.trace("Decode Cassandra value {} into Java for property {} of entity class {}", fromCassandra, meta.getPropertyName(), meta.getEntityClassName());
        switch (meta.type()) {
            case SIMPLE:
            case ID:
                return meta.getSimpleCodec().decode(fromCassandra);
            case LIST:
                return meta.getListCodec().decode((List) fromCassandra);
            case SET:
                return meta.getSetCodec().decode((Set) fromCassandra);
            case MAP:
                return meta.getMapCodec().decode((Map) fromCassandra);
            default:
                throw new AchillesException(String.format("Cannot decode value '%s' from CQL3 for property '%s' of type '%s'",fromCassandra, meta.propertyName, meta.type().name()));
        }
    }

    public <T> T encodeToCassandra(Object fromJava) {
        log.trace("Encode Java value {} into Cassandra for property {} of entity class {}", fromJava, meta.getPropertyName(), meta.getEntityClassName());
        switch (meta.type()) {
            case SIMPLE:
            case ID:
                return (T)meta.getSimpleCodec().encode(fromJava);
            case LIST:
                return (T)meta.getListCodec().encode((List) fromJava);
            case SET:
                return (T)meta.getSetCodec().encode((Set) fromJava);
            case MAP:
                return (T)meta.getMapCodec().encode((Map) fromJava);
            case COUNTER:
                return (T)((InternalCounterImpl) fromJava).getInternalCounterDelta();
            default:
                throw new AchillesException(String.format("Cannot encode value '%s' to CQL3 for property '%s' of type '%s'",fromJava, meta.propertyName, meta.type().name()));
        }
    }

    public Object getAndEncodeValueForCassandra(Object entity) {
        log.trace("Get and encode Java value into Cassandra for property {} of entity class {} from entity ", meta.getPropertyName(), meta.getEntityClassName(), entity);
        Object value = meta.forValues().getValueFromField(entity);
        if (value != null) {
            return encodeToCassandra(value);
        } else {
            return null;
        }
    }

    public List<Object> encodeToComponents(Object compoundKey, boolean onlyPartitionComponents) {
        log.trace("Encode compound primary key {} to CQL3 components with 'onlyPartitionComponents' : {}", compoundKey, onlyPartitionComponents);
        Validator.validateTrue(meta.type() == PropertyType.EMBEDDED_ID, "Cannot encode object '%s' for the property '%s' which is not a compound primary key", compoundKey, meta.propertyName);
        List<Object> encoded = new ArrayList<>();
        if (compoundKey == null) {
            return encoded;
        }

        if (onlyPartitionComponents) {
            for (PropertyMeta partitionKeyMeta : meta.getEmbeddedIdProperties().getPartitionComponents().propertyMetas) {
                encoded.add(partitionKeyMeta.forTranscoding().getAndEncodeValueForCassandra(compoundKey));
            }
        } else {
            for (PropertyMeta partitionKeyMeta : meta.getEmbeddedIdProperties().propertyMetas) {
                encoded.add(partitionKeyMeta.forTranscoding().getAndEncodeValueForCassandra(compoundKey));
            }
        }
        return encoded;
    }


    public String forceEncodeToJSON(Object object) {
        log.trace("Force encode {} to JSON for property {} of entity class {}", object, meta.getPropertyName(), meta.getEntityClassName());
        Validator.validateNotNull(object, "Cannot encode to JSON null primary key for class '%s'", meta.getEntityClassName());
        if (object instanceof String) {
            return String.class.cast(object);
        } else {
            try {
                return this.meta.defaultJacksonMapper.writeValueAsString(object);
            } catch (Exception e) {
                throw new AchillesException(String.format("Error while encoding primary key '%s' for class '%s'", object, meta.getEntityClassName()), e);
            }
        }
    }
}