package com.tempmon.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.math.BigDecimal;

/**
 * DynamoDB bean mapping for the hygrometer_readings table.
 *
 * Table key design:
 *   - Partition key: location (String)
 *   - Sort key:      timestamp (String, UTC ISO 8601 — sorts lexicographically)
 */
@DynamoDbBean
public class ReadingItem {

    private String location;
    private String timestamp;
    private String requestId;
    private BigDecimal temperatureF;
    private BigDecimal humidityPct;
    private String ingestedAt;

    // Required no-arg constructor for DynamoDB Enhanced Client
    public ReadingItem() {}

    @DynamoDbPartitionKey
    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @DynamoDbSortKey
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public BigDecimal getTemperatureF() {
        return temperatureF;
    }

    public void setTemperatureF(BigDecimal temperatureF) {
        this.temperatureF = temperatureF;
    }

    public BigDecimal getHumidityPct() {
        return humidityPct;
    }

    public void setHumidityPct(BigDecimal humidityPct) {
        this.humidityPct = humidityPct;
    }

    /**
     * UTC ingestion timestamp, ISO 8601 with millisecond precision.
     * Example: "2024-01-15T10:30:00.123Z"
     */
    public String getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(String ingestedAt) {
        this.ingestedAt = ingestedAt;
    }
}
