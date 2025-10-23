#!/bin/bash

# Minimal Azure Setup for LRGPS HTTP Server Testing
# Creates AKS cluster and deploys LRGPS receiver for testing

set -e  # Exit on error

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "========================================================"
echo "  LRGPS HTTP Server - Azure Test Setup"
echo "========================================================"
echo ""

# Configuration - CUSTOMIZE THESE
RESOURCE_GROUP="${RESOURCE_GROUP:-rtd-lrgps-test-rg}"
AKS_CLUSTER_NAME="${AKS_CLUSTER_NAME:-rtd-lrgps-test}"
LOCATION="${LOCATION:-eastus}"
ACR_NAME="${ACR_NAME}"  # Your Azure Container Registry name
NAMESPACE="rtd-test"

# Check prerequisites
echo -e "${GREEN}[1/8] Checking prerequisites...${NC}"
if ! command -v az &> /dev/null; then
    echo -e "${RED}❌ Azure CLI not found. Install from: https://aka.ms/InstallAzureCLIDeb${NC}"
    exit 1
fi

if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}❌ kubectl not found. Installing...${NC}"
    az aks install-cli
fi

if ! az account show &> /dev/null; then
    echo -e "${RED}❌ Not logged into Azure. Run: az login${NC}"
    exit 1
fi

if [ -z "$ACR_NAME" ]; then
    echo -e "${YELLOW}⚠️  ACR_NAME not set. You'll need to provide it.${NC}"
    read -p "Enter your Azure Container Registry name (e.g., rtdpipelineacr): " ACR_NAME
    if [ -z "$ACR_NAME" ]; then
        echo -e "${RED}❌ ACR name is required${NC}"
        exit 1
    fi
fi

echo "✅ Prerequisites OK"
echo ""
echo -e "${BLUE}Configuration:${NC}"
echo "  Resource Group: $RESOURCE_GROUP"
echo "  AKS Cluster: $AKS_CLUSTER_NAME"
echo "  Location: $LOCATION"
echo "  ACR: $ACR_NAME"
echo "  Namespace: $NAMESPACE"
echo ""

# Create resource group
echo -e "${GREEN}[2/8] Creating resource group...${NC}"
if az group show --name $RESOURCE_GROUP &> /dev/null; then
    echo -e "${YELLOW}⚠️  Resource group already exists. Using existing.${NC}"
else
    az group create \
        --name $RESOURCE_GROUP \
        --location $LOCATION \
        --output none
    echo "✅ Resource group created"
fi
echo ""

# Create AKS cluster (minimal for testing)
echo -e "${GREEN}[3/8] Creating minimal AKS cluster for testing...${NC}"
echo "This takes ~5-7 minutes..."

if az aks show --name $AKS_CLUSTER_NAME --resource-group $RESOURCE_GROUP &> /dev/null; then
    echo -e "${YELLOW}⚠️  AKS cluster already exists. Using existing.${NC}"
else
    az aks create \
        --resource-group $RESOURCE_GROUP \
        --name $AKS_CLUSTER_NAME \
        --node-count 2 \
        --node-vm-size Standard_B2s \
        --enable-managed-identity \
        --generate-ssh-keys \
        --location $LOCATION \
        --attach-acr $ACR_NAME \
        --output none

    echo "✅ AKS cluster created"
fi
echo ""

# Get AKS credentials
echo -e "${GREEN}[4/8] Getting AKS credentials...${NC}"
az aks get-credentials \
    --resource-group $RESOURCE_GROUP \
    --name $AKS_CLUSTER_NAME \
    --overwrite-existing \
    --output none

echo "✅ Credentials configured"
echo ""

# Create namespace
echo -e "${GREEN}[5/8] Creating namespace...${NC}"
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f - > /dev/null
echo "✅ Namespace created: $NAMESPACE"
echo ""

# Create ConfigMap for Kafka
echo -e "${GREEN}[6/8] Creating ConfigMap...${NC}"
kubectl apply -f - > /dev/null <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: rtd-config
  namespace: $NAMESPACE
data:
  KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
  TIS_PROXY_HOST: "http://tisproxy.rtd-denver.com"
EOF
echo "✅ ConfigMap created"
echo ""

# Create minimal Kafka deployment (single pod for testing)
echo -e "${GREEN}[7/8] Deploying minimal Kafka (single pod for testing)...${NC}"
kubectl apply -f - > /dev/null <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kafka
  namespace: $NAMESPACE
spec:
  replicas: 1
  selector:
    matchLabels:
      app: kafka
  template:
    metadata:
      labels:
        app: kafka
    spec:
      containers:
      - name: kafka
        image: confluentinc/cp-kafka:7.5.0
        ports:
        - containerPort: 9092
        env:
        - name: KAFKA_ZOOKEEPER_CONNECT
          value: "localhost:2181"
        - name: KAFKA_ADVERTISED_LISTENERS
          value: "PLAINTEXT://kafka:9092"
        - name: KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR
          value: "1"
        - name: KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR
          value: "1"
        - name: KAFKA_TRANSACTION_STATE_LOG_MIN_ISR
          value: "1"
      - name: zookeeper
        image: confluentinc/cp-zookeeper:7.5.0
        ports:
        - containerPort: 2181
        env:
        - name: ZOOKEEPER_CLIENT_PORT
          value: "2181"
        - name: ZOOKEEPER_TICK_TIME
          value: "2000"
---
apiVersion: v1
kind: Service
metadata:
  name: kafka
  namespace: $NAMESPACE
spec:
  ports:
  - port: 9092
    name: kafka
  - port: 2181
    name: zookeeper
  selector:
    app: kafka
EOF
echo "✅ Kafka deployed"
echo ""

# Deploy LRGPS receiver
echo -e "${GREEN}[8/8] Deploying LRGPS HTTP receiver...${NC}"

# Create temporary manifest with ACR name substituted
TEMP_MANIFEST=$(mktemp)
cat > $TEMP_MANIFEST <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: lrgps-receiver
  namespace: $NAMESPACE
  labels:
    app: lrgps-receiver
spec:
  replicas: 1
  selector:
    matchLabels:
      app: lrgps-receiver
  template:
    metadata:
      labels:
        app: lrgps-receiver
    spec:
      containers:
      - name: lrgps-receiver
        image: ${ACR_NAME}.azurecr.io/rtd-pipeline:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8083
          name: http
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka:9092"
        - name: LRGPS_TOPIC
          value: "rtd.lrgps"
        - name: LRGPS_SERVICE
          value: "lrgps"
        - name: LRGPS_TTL
          value: "90000"
        command: ["java", "-cp", "/app/app.jar", "com.rtd.pipeline.LRGPSHTTPReceiver"]
        resources:
          requests:
            cpu: 100m
            memory: 256Mi
          limits:
            cpu: 500m
            memory: 1Gi
        livenessProbe:
          httpGet:
            path: /health
            port: 8083
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health
            port: 8083
          initialDelaySeconds: 15
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: lrgps-receiver
  namespace: $NAMESPACE
  labels:
    app: lrgps-receiver
spec:
  type: LoadBalancer
  ports:
  - port: 8083
    targetPort: 8083
    protocol: TCP
    name: http
  selector:
    app: lrgps-receiver
EOF

kubectl apply -f $TEMP_MANIFEST
rm $TEMP_MANIFEST

echo "✅ LRGPS receiver deployed"
echo ""

# Wait for deployment
echo -e "${BLUE}Waiting for LRGPS receiver to be ready...${NC}"
kubectl rollout status deployment/lrgps-receiver -n $NAMESPACE --timeout=300s
echo ""

# Get LoadBalancer IP
echo -e "${BLUE}Waiting for LoadBalancer IP (this may take 2-3 minutes)...${NC}"
for i in {1..30}; do
    EXTERNAL_IP=$(kubectl get svc lrgps-receiver -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    if [ -n "$EXTERNAL_IP" ]; then
        break
    fi
    echo -n "."
    sleep 10
done
echo ""

# Success message
echo "========================================================"
echo -e "${GREEN}✅ LRGPS Test Environment Ready!${NC}"
echo "========================================================"
echo ""

if [ -n "$EXTERNAL_IP" ]; then
    echo -e "${BLUE}🔌 LRGPS HTTP Server Endpoint:${NC}"
    echo "  http://$EXTERNAL_IP:8083/lrgps"
    echo ""

    echo -e "${BLUE}🧪 Test Commands:${NC}"
    echo ""
    echo "  Health Check:"
    echo "    curl http://$EXTERNAL_IP:8083/health"
    echo ""
    echo "  Status:"
    echo "    curl http://$EXTERNAL_IP:8083/status"
    echo ""
    echo "  Send Test Data:"
    echo "    curl -X POST http://$EXTERNAL_IP:8083/lrgps \\"
    echo "      -H 'Content-Type: application/json' \\"
    echo "      -d '{\"vehicle_id\":\"LRV-TEST\",\"latitude\":39.7392,\"longitude\":-104.9903}'"
    echo ""
    echo "  View Latest Data:"
    echo "    curl http://$EXTERNAL_IP:8083/lrgps/latest"
    echo ""
else
    echo -e "${YELLOW}⚠️  LoadBalancer IP not yet assigned.${NC}"
    echo ""
    echo "Get the IP with:"
    echo "  kubectl get svc lrgps-receiver -n $NAMESPACE"
    echo ""
fi

echo -e "${BLUE}📊 Monitor Commands:${NC}"
echo ""
echo "  View pods:"
echo "    kubectl get pods -n $NAMESPACE"
echo ""
echo "  View LRGPS logs:"
echo "    kubectl logs -f deployment/lrgps-receiver -n $NAMESPACE"
echo ""
echo "  View Kafka messages:"
echo "    kubectl exec -it deployment/kafka -n $NAMESPACE -c kafka -- \\"
echo "      kafka-console-consumer --bootstrap-server localhost:9092 \\"
echo "      --topic rtd.lrgps --from-beginning"
echo ""

echo -e "${BLUE}🗑️  Cleanup (when done testing):${NC}"
echo "  az group delete --name $RESOURCE_GROUP --yes --no-wait"
echo ""

echo -e "${BLUE}💰 Estimated Cost:${NC}"
echo "  ~\$70-100/month (delete when not in use to save money)"
echo ""

echo "========================================================"
echo -e "${GREEN}Setup Complete!${NC}"
echo "========================================================"
