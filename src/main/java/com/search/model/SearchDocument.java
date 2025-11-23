package com.search.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Document model for Elasticsearch storage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchDocument {
    
    @JsonProperty("doc_id")
    private String docId;
    
    @JsonProperty("tenant_id")
    private String tenantId;
    
    private String content;
    
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Instant timestamp = Instant.now();
    
    /**
     * Get the Elasticsearch index name for this tenant
     */
    public static String getIndexName(String tenantId) {
        // Normalize tenant ID for Elasticsearch index naming
        // Index names must be lowercase
        return "search-docs-" + tenantId.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }
}
