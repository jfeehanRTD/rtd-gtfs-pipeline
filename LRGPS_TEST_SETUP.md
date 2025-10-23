# LRGPS HTTP Server - Azure Test Setup

## 🚀 Quick Start

### Option 1: Automated Setup (Recommended)

```bash
# Set your Azure Container Registry name
export ACR_NAME="rtdpipelineacr"

# Run automated setup
./scripts/aks/setup-lrgps-test.sh
```

**Takes**: 7-10 minutes
**Cost**: ~$81/month (~$2.70/day)
**What you get**: LRGPS HTTP server accessible at `http://<EXTERNAL_IP>:8083`

---

### Option 2: Manual Azure CLI Commands

See detailed step-by-step guide: [docs/aks/LRGPS_AZURE_CLI_SETUP.md](./docs/aks/LRGPS_AZURE_CLI_SETUP.md)

**Quick manual setup**:

```bash
# 1. Set variables
export RESOURCE_GROUP="rtd-lrgps-test-rg"
export AKS_CLUSTER_NAME="rtd-lrgps-test"
export LOCATION="eastus"
export ACR_NAME="rtdpipelineacr"
export NAMESPACE="rtd-test"

# 2. Create resource group
az group create --name $RESOURCE_GROUP --location $LOCATION

# 3. Create AKS cluster (takes ~5-7 minutes)
az aks create \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER_NAME \
  --node-count 2 \
  --node-vm-size Standard_B2s \
  --enable-managed-identity \
  --generate-ssh-keys \
  --attach-acr $ACR_NAME

# 4. Get credentials
az aks get-credentials --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER_NAME

# 5. Create namespace
kubectl create namespace $NAMESPACE

# 6. Deploy Kafka and LRGPS receiver
# (See full guide for deployment manifests)

# 7. Get external IP
kubectl get svc lrgps-receiver -n $NAMESPACE
```

---

## 🧪 Test Your LRGPS Endpoint

Once deployed, get the external IP:

```bash
EXTERNAL_IP=$(kubectl get svc lrgps-receiver -n rtd-test -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "LRGPS Endpoint: http://$EXTERNAL_IP:8083"
```

### Health Check

```bash
curl http://$EXTERNAL_IP:8083/health
```

### Send Test Data

```bash
curl -X POST http://$EXTERNAL_IP:8083/lrgps \
  -H "Content-Type: application/json" \
  -d '{
    "vehicle_id": "LRV-TEST-001",
    "latitude": 39.7392,
    "longitude": -104.9903,
    "timestamp": "2025-10-02T15:30:00Z",
    "speed": 35,
    "heading": 180
  }'
```

**Expected response**:
```json
{"status": "success", "message": "LRGPS data received and forwarded to Kafka"}
```

### View Data in Kafka

```bash
kubectl exec -it deployment/kafka -n rtd-test -c kafka -- \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic rtd.lrgps \
  --from-beginning \
  --max-messages 10
```

---

## 📊 What Gets Created

### Azure Resources

| Resource | Type | Purpose | Cost/month |
|----------|------|---------|------------|
| Resource Group | Container | Holds all resources | Free |
| AKS Cluster | Kubernetes | Runs containers | ~$60 |
| 2x VM Nodes | Standard_B2s | Worker nodes | ~$60 |
| LoadBalancer | Standard | Public IP endpoint | ~$20 |
| **Total** | | | **~$81** |

### Kubernetes Resources

| Resource | Type | Replicas | Purpose |
|----------|------|----------|---------|
| kafka | Deployment | 1 | Message queue |
| lrgps-receiver | Deployment | 1 | HTTP endpoint |
| lrgps-receiver | Service (LoadBalancer) | - | Public endpoint |

---

## 🔌 LRGPS Endpoints (Once Deployed)

All endpoints accessible at: `http://<EXTERNAL_IP>:8083`

- **POST /lrgps** - Receive LRGPS data from ExpressLink
- **GET /health** - Health check
- **GET /status** - Detailed status
- **GET /lrgps/latest** - View last 10 messages

---

## 📋 Monitor Your Deployment

```bash
# View all resources
kubectl get all -n rtd-test

# View LRGPS logs
kubectl logs -f deployment/lrgps-receiver -n rtd-test

# View Kafka logs
kubectl logs -f deployment/kafka -c kafka -n rtd-test

# Check service status
kubectl get svc lrgps-receiver -n rtd-test
```

---

## 🗑️ Cleanup (Important!)

### Delete Everything (Recommended)

```bash
az group delete --name rtd-lrgps-test-rg --yes --no-wait
```

### Or Stop Cluster Temporarily

```bash
# Stop (saves ~30% cost)
az aks stop --resource-group rtd-lrgps-test-rg --name rtd-lrgps-test

# Start again later
az aks start --resource-group rtd-lrgps-test-rg --name rtd-lrgps-test
```

---

## 💰 Cost Management

| Usage Pattern | Monthly Cost |
|---------------|--------------|
| **24/7 running** | ~$81 |
| **Stop overnight** | ~$55 |
| **Only business hours (8am-6pm M-F)** | ~$20 |
| **Delete when not in use** | $0 |

**Recommendation**: Delete resource group when done testing to avoid charges.

---

## 🐛 Troubleshooting

### External IP shows "pending"

Wait 2-3 minutes for Azure to provision the LoadBalancer:

```bash
kubectl get svc lrgps-receiver -n rtd-test -w
```

### Pod not starting

```bash
# Check pod events
kubectl describe pod <pod-name> -n rtd-test

# Check logs
kubectl logs <pod-name> -n rtd-test
```

### Can't access endpoint from internet

```bash
# Verify external IP is assigned
kubectl get svc lrgps-receiver -n rtd-test

# Test from cloud shell (Azure Portal)
curl http://<EXTERNAL_IP>:8083/health
```

---

## 📚 Documentation

- **[Automated Setup Script](./scripts/aks/setup-lrgps-test.sh)** - One-command deployment
- **[Azure CLI Manual Steps](./docs/aks/LRGPS_AZURE_CLI_SETUP.md)** - Step-by-step guide
- **[LRGPS Endpoint Guide](./docs/aks/LRGPS_ENDPOINT_GUIDE.md)** - Complete reference
- **[Quick Reference](./docs/aks/LRGPS_QUICK_REF.md)** - Printable commands

---

## ✅ Success Checklist

- [ ] Azure CLI installed and logged in
- [ ] ACR_NAME environment variable set
- [ ] Setup script executed successfully
- [ ] External IP assigned to LoadBalancer
- [ ] Health check returns "healthy"
- [ ] Test data successfully posted
- [ ] Data visible in Kafka topic
- [ ] On-premise ExpressLink configured (if applicable)

---

## 🎯 Next Steps

1. **Test from on-premise** - Send real LRGPS data from ExpressLink
2. **Monitor data flow** - Watch Kafka messages
3. **Scale if needed** - Increase replicas for production
4. **Add security** - Enable HTTPS and authentication
5. **Remember to cleanup** - Delete resources when done testing

---

**Your LRGPS HTTP server is now running in Azure!** 🚀

Get the endpoint URL:
```bash
kubectl get svc lrgps-receiver -n rtd-test -o jsonpath='http://{.status.loadBalancer.ingress[0].ip}:8083/lrgps'
```
