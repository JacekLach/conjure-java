package com.palantir.conjure.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Generated;

@JsonDeserialize(builder = SetType.Builder.class)
@Generated("com.palantir.conjure.java.types.BeanGenerator")
public final class SetType {
    private final Type itemType;

    private volatile int memoizedHashCode;

    private SetType(Type itemType) {
        validateFields(itemType);
        this.itemType = itemType;
    }

    @JsonProperty("itemType")
    public Type getItemType() {
        return this.itemType;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof SetType && equalTo((SetType) other));
    }

    private boolean equalTo(SetType other) {
        return this.itemType.equals(other.itemType);
    }

    @Override
    public int hashCode() {
        if (memoizedHashCode == 0) {
            memoizedHashCode = Objects.hash(itemType);
        }
        return memoizedHashCode;
    }

    @Override
    public String toString() {
        return new StringBuilder("SetType")
                .append("{")
                .append("itemType")
                .append(": ")
                .append(itemType)
                .append("}")
                .toString();
    }

    public static SetType of(Type itemType) {
        return builder().itemType(itemType).build();
    }

    private static void validateFields(Type itemType) {
        List<String> missingFields = null;
        missingFields = addFieldIfMissing(missingFields, itemType, "itemType");
        if (missingFields != null) {
            throw new IllegalArgumentException(
                    "Some required fields have not been set: " + missingFields);
        }
    }

    private static List<String> addFieldIfMissing(
            List<String> prev, Object fieldValue, String fieldName) {
        List<String> missingFields = prev;
        if (fieldValue == null) {
            if (missingFields == null) {
                missingFields = new ArrayList<>(1);
            }
            missingFields.add(fieldName);
        }
        return missingFields;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Generated("com.palantir.conjure.java.types.BeanBuilderGenerator")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Builder {
        private Type itemType;

        private Builder() {}

        public Builder from(SetType other) {
            itemType(other.getItemType());
            return this;
        }

        @JsonSetter("itemType")
        public Builder itemType(Type itemType) {
            this.itemType = Objects.requireNonNull(itemType, "itemType cannot be null");
            return this;
        }

        public SetType build() {
            return new SetType(itemType);
        }
    }
}
