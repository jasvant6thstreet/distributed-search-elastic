package com.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch configuration for distributed search service
 */

@Slf4j
@Configuration
public class ElasticsearchConfig {
    
    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;
    
    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;
    
    @Value("${elasticsearch.scheme:http}")
    private String elasticsearchScheme;
    
    @Value("${elasticsearch.username:}")
    private String elasticsearchUsername;
    
    @Value("${elasticsearch.password:}")
    private String elasticsearchPassword;
    
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        log.info("Initializing Elasticsearch client: {}://{}:{}", 
                elasticsearchScheme, elasticsearchHost, elasticsearchPort);
        
        // Create the low-level REST client
        RestClient restClient;
        
        if (elasticsearchUsername != null && !elasticsearchUsername.isEmpty()) {
            // Configure authentication if credentials provided
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword)
            );
            
            restClient = RestClient.builder(
                    new HttpHost(elasticsearchHost, elasticsearchPort, elasticsearchScheme))
                .setHttpClientConfigCallback(httpClientBuilder -> 
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                .build();
        } else {
            // No authentication
            restClient = RestClient.builder(
                    new HttpHost(elasticsearchHost, elasticsearchPort, elasticsearchScheme))
                .build();
        }
        
        // Create the transport with a Jackson mapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RestClientTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper(objectMapper));
        
        // Create the API client
        ElasticsearchClient client = new ElasticsearchClient(transport);
        
        log.info("Elasticsearch client initialized successfully");
        return client;
    }
}
