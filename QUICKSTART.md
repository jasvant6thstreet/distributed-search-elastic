# Quick Start - Elasticsearch-based Search Service

## Fastest Way to Get Started (5 minutes)

### Step 1: Start Elasticsearch and Services (2 minutes)

```bash
# Start entire stack with Docker Compose
docker-compose up -d

# This starts:
# - 3-node Elasticsearch cluster
# - 2 Search Service instances
# - Nginx load balancer
# - Kibana UI
# - Prometheus monitoring
```

### Step 2: Wait for Elasticsearch to be Ready (1 minute)

```bash
# Check Elasticsearch health
curl http://localhost:9200/_cluster/health

# Should show: "status": "green" or "yellow"
```

### Step 3: Test the API (2 minutes)

```bash
# Get authentication token
curl -X POST http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"tenantId": "demo-company"}'

# Save the token
export TOKEN="your-token-from-above"

# Index a document
curl -X POST http://localhost:8080/documents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Elasticsearch provides distributed search at scale",
    "metadata": {"title": "Elasticsearch Intro"}
  }'
  
# Search
curl -X GET http://localhost:8080/search?q=elasticesearch&topK=10 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" 

# Search
curl -X POST http://localhost:8080/search \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query": "elasticsearch", "topK": 10}'

# Get stats
curl http://localhost:8080/api/stats \
  -H "Authorization: Bearer $TOKEN"
```

## What You Get

✅ **Persistent Storage**: Data survives restarts  
✅ **3-Node Elasticsearch Cluster**: High availability  
✅ **Automatic Sharding**: 5 shards per index  
✅ **2x Replication**: Fault tolerance  
✅ **Load Balanced**: 2 service instances  
✅ **Monitoring**: Kibana + Prometheus  

## Access Points

- **Search API**: http://localhost:8080
- **Elasticsearch**: http://localhost:9200
- **Kibana**: http://localhost:5601
- **Prometheus**: http://localhost:9090

## Key Elasticsearch Commands

```bash
# View all indices
curl http://localhost:9200/_cat/indices?v

# View cluster health
curl http://localhost:9200/_cluster/health?pretty

# View specific index
curl http://localhost:9200/search-docs-demo-company

# Delete an index
curl -X DELETE http://localhost:9200/search-docs-demo-company
```

## Scalability Test

```bash
# Index 10,000 documents (example script)
for i in {1..10000}; do
  curl -X POST http://localhost:8080/api/documents \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"content\": \"Document $i about various topics\"}"
done

# Check index size
curl http://localhost:9200/_cat/indices?v
```

## Monitoring

### Kibana
1. Open http://localhost:5601
2. Go to "Management" → "Stack Monitoring"
3. View cluster health, node stats, index stats

### Elasticsearch APIs
```bash
# Node stats
curl http://localhost:9200/_nodes/stats?pretty

# Index stats
curl http://localhost:9200/_stats?pretty

# Shard allocation
curl http://localhost:9200/_cat/shards?v
```

## Stop Services

```bash
# Stop all services
docker-compose down

# Stop and remove data
docker-compose down -v
```

## Local Development (without Docker)

### 1. Start Elasticsearch

```bash
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  docker.elastic.co/elasticsearch/elasticsearch:8.11.0
```

### 2. Build and Run Application

```bash
mvn clean package
mvn spring-boot:run
```

## Configuration

### Environment Variables

```bash
# For docker-compose
ELASTICSEARCH_HOST=elasticsearch-node1
ELASTICSEARCH_PORT=9200

# For local development
ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200
```

### Application Properties

Edit `src/main/resources/application.yml`:

```yaml
elasticsearch:
  host: localhost
  port: 9200
  scheme: http
```

## Troubleshooting

### Elasticsearch not starting?
```bash
# Check logs
docker-compose logs elasticsearch-node1

# Increase Docker memory to 4GB+
```

### Connection refused?
```bash
# Verify Elasticsearch is running
curl http://localhost:9200

# Check application logs
docker-compose logs search-service-1
```

### Out of memory?
```bash
# Reduce Elasticsearch memory in docker-compose.yml
ES_JAVA_OPTS=-Xms256m -Xmx256m
```

## Next Steps

1. Read [README.md](README.md) for complete documentation
2. Explore Kibana at http://localhost:5601
3. View Prometheus metrics at http://localhost:9090
4. Scale up: Add more Elasticsearch nodes
5. Configure for production (TLS, authentication, backups)

## Performance Expectations

| Operation | Time | Throughput |
|-----------|------|------------|
| Index 1 doc | ~5ms | - |
| Index 1000 docs (bulk) | ~500ms | 2,000/sec |
| Simple search | ~10-20ms | 100/sec |
| Complex search | ~50-100ms | 20/sec |

## Architecture Benefits

**vs In-Memory Solution:**
- ✅ Data persists on restart
- ✅ Can scale to billions of documents
- ✅ Automatic replication and sharding
- ✅ Production-grade fault tolerance
- ✅ Real-time cluster management
- ✅ Enterprise monitoring tools

**Trade-offs:**
- Slightly higher latency (network + disk I/O)
- More complex infrastructure
- Requires Elasticsearch expertise

---

**Ready to scale? Start with docker-compose up -d! 🚀**
