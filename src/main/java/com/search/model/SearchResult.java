package com.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map; /**
 * Search result with relevance scoring
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    
    private String docId;
    private String tenantId;
    private double score;
    private String snippet;
    private Map<String, Object> metadata;
    
    public static SearchResult fromDocument(SearchDocument document, double score) {
        String snippet = document.getContent().length() > 200 
            ? document.getContent().substring(0, 200) + "..."
            : document.getContent();
            
        return SearchResult.builder()
                .docId(document.getDocId())
                .tenantId(document.getTenantId())
                .score(score)
                .snippet(snippet)
                .metadata(document.getMetadata())
                .build();
    }
}
