#!/bin/bash

# RTD GTFS Pipeline - Minimal AKS Deployment for Dev Teams
# No DevOps experience required - just run this script!

set -e  # Exit on error

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "================================================"
echo "  RTD Pipeline - Minimal AKS Dev Deployment"
echo "================================================"
echo ""

# Check prerequisites
echo -e "${GREEN}[1/6] Checking prerequisites...${NC}"
if ! command -v az &> /dev/null; then
    echo "❌ Azure CLI not found. Install: curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash"
    exit 1
fi

if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl not found. Install: az aks install-cli"
    exit 1
fi

if ! az account show &> /dev/null; then
    echo "❌ Not logged into Azure. Run: az login"
    exit 1
fi
echo "✅ Prerequisites OK"
echo ""

# Set variables
RESOURCE_GROUP="rtd-dev-rg"
CLUSTER_NAME="rtd-dev-aks"
LOCATION="eastus"
NAMESPACE="rtd-dev"

# Create resource group
echo -e "${GREEN}[2/6] Creating resource group...${NC}"
if ! az group show --name $RESOURCE_GROUP &> /dev/null; then
    az group create --name $RESOURCE_GROUP --location $LOCATION --output none
    echo "✅ Resource group created"
else
    echo "✅ Resource group already exists"
fi
echo ""

# Create AKS cluster
echo -e "${GREEN}[3/6] Creating AKS Automatic cluster (this takes ~5 minutes)...${NC}"
if ! az aks show --name $CLUSTER_NAME --resource-group $RESOURCE_GROUP &> /dev/null; then
    az aks create \
        --resource-group $RESOURCE_GROUP \
        --name $CLUSTER_NAME \
        --tier automatic \
        --location $LOCATION \
        --node-count 2 \
        --generate-ssh-keys \
        --output none
    echo "✅ AKS cluster created"
else
    echo "✅ AKS cluster already exists"
fi
echo ""

# Get credentials
echo -e "${GREEN}[4/6] Getting cluster credentials...${NC}"
az aks get-credentials \
    --resource-group $RESOURCE_GROUP \
    --name $CLUSTER_NAME \
    --overwrite-existing \
    --output none
echo "✅ Credentials configured"
echo ""

# Create namespace
echo -e "${GREEN}[5/6] Creating namespace...${NC}"
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f - > /dev/null
echo "✅ Namespace created: $NAMESPACE"
echo ""

# Deploy minimal Kafka
echo -e "${GREEN}[6/6] Deploying minimal Kafka + Zookeeper...${NC}"
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

# Success message
echo "================================================"
echo -e "${GREEN}✅ Deployment Complete!${NC}"
echo "================================================"
echo ""
echo "📋 Your minimal AKS cluster is ready:"
echo ""
echo "  • Resource Group: $RESOURCE_GROUP"
echo "  • Cluster Name: $CLUSTER_NAME"
echo "  • Namespace: $NAMESPACE"
echo "  • Nodes: 2"
echo "  • Cost: ~\$213/month"
echo ""
echo "🚀 Next Steps:"
echo ""
echo "  1. View pods:"
echo "     kubectl get pods -n $NAMESPACE"
echo ""
echo "  2. Access Kafka from your laptop:"
echo "     kubectl port-forward svc/kafka 9092:9092 -n $NAMESPACE"
echo ""
echo "  3. View logs:"
echo "     kubectl logs -f deployment/kafka -c kafka -n $NAMESPACE"
echo ""
echo "  4. Deploy your app:"
echo "     kubectl apply -f your-app.yaml -n $NAMESPACE"
echo ""
echo "  5. When done, delete everything:"
echo "     az group delete --name $RESOURCE_GROUP --yes"
echo ""
echo -e "${YELLOW}💡 Pro Tip: Save money by stopping the cluster when not in use:${NC}"
echo "     az aks stop --resource-group $RESOURCE_GROUP --name $CLUSTER_NAME"
echo ""
echo "📚 Full documentation: docs/aks/MINIMAL_AKS.md"
echo ""
