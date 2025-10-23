#!/bin/bash

# LRGPS HTTP Server - Private VPN-Only Setup (No Public IP)
# Accessible only through your work VPN via Azure Private Endpoint

set -e  # Exit on error

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "========================================================"
echo "  LRGPS HTTP Server - Private VPN-Only Setup"
echo "========================================================"
echo ""

# Configuration - CUSTOMIZE THESE
RESOURCE_GROUP="${RESOURCE_GROUP:-rtd-lrgps-private-rg}"
AKS_CLUSTER_NAME="${AKS_CLUSTER_NAME:-rtd-lrgps-private}"
LOCATION="${LOCATION:-eastus}"
ACR_NAME="${ACR_NAME}"
NAMESPACE="rtd-private"

# VNet Configuration
VNET_NAME="rtd-vnet"
VNET_CIDR="10.0.0.0/16"
AKS_SUBNET_NAME="aks-subnet"
AKS_SUBNET_CIDR="10.0.1.0/24"
SERVICE_CIDR="10.1.0.0/16"
DNS_SERVICE_IP="10.1.0.10"

# Check prerequisites
echo -e "${GREEN}[1/10] Checking prerequisites...${NC}"
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
echo "  VNet: $VNET_NAME ($VNET_CIDR)"
echo "  Network: PRIVATE (VPN-only, no public IP)"
echo ""
echo -e "${YELLOW}Important: This setup creates NO public IPs (compliant with Azure policies)${NC}"
echo "  • Control plane: Private (no public endpoint)"
echo "  • Nodes: Private (no public IPs)"
echo "  • Services: ClusterIP only (no LoadBalancer)"
echo "  • Access: Requires VPN connection"
echo ""

# Create resource group
echo -e "${GREEN}[2/10] Creating resource group...${NC}"
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

# Create VNet
echo -e "${GREEN}[3/10] Creating Virtual Network...${NC}"
if az network vnet show --name $VNET_NAME --resource-group $RESOURCE_GROUP &> /dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  VNet already exists. Using existing.${NC}"
else
    az network vnet create \
        --resource-group $RESOURCE_GROUP \
        --name $VNET_NAME \
        --address-prefixes $VNET_CIDR \
        --output none
    echo "✅ VNet created"
fi
echo ""

# Create AKS subnet
echo -e "${GREEN}[4/10] Creating AKS subnet...${NC}"
if az network vnet subnet show --vnet-name $VNET_NAME --name $AKS_SUBNET_NAME --resource-group $RESOURCE_GROUP &> /dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Subnet already exists. Using existing.${NC}"
else
    az network vnet subnet create \
        --resource-group $RESOURCE_GROUP \
        --vnet-name $VNET_NAME \
        --name $AKS_SUBNET_NAME \
        --address-prefixes $AKS_SUBNET_CIDR \
        --output none
    echo "✅ AKS subnet created"
fi
echo ""

# Get subnet ID
SUBNET_ID=$(az network vnet subnet show \
    --resource-group $RESOURCE_GROUP \
    --vnet-name $VNET_NAME \
    --name $AKS_SUBNET_NAME \
    --query id -o tsv)

echo -e "${BLUE}Subnet ID: $SUBNET_ID${NC}"
echo ""

# Create private AKS cluster
echo -e "${GREEN}[5/10] Creating PRIVATE AKS cluster...${NC}"
echo "This takes ~7-10 minutes..."

# Check if cluster exists
CLUSTER_EXISTS=$(az aks show --name $AKS_CLUSTER_NAME --resource-group $RESOURCE_GROUP --query "name" -o tsv 2>/dev/null || echo "")

if [ -n "$CLUSTER_EXISTS" ]; then
    echo -e "${YELLOW}⚠️  AKS cluster already exists.${NC}"

    # Check cluster state
    PROVISIONING_STATE=$(az aks show --name $AKS_CLUSTER_NAME --resource-group $RESOURCE_GROUP --query "provisioningState" -o tsv 2>/dev/null || echo "Unknown")

    if [ "$PROVISIONING_STATE" != "Succeeded" ]; then
        echo -e "${RED}❌ Cluster is in state: $PROVISIONING_STATE${NC}"
        echo -e "${YELLOW}Attempting to fix with 'az aks update'...${NC}"

        az aks update \
            --resource-group $RESOURCE_GROUP \
            --name $AKS_CLUSTER_NAME \
            --output none || true

        echo "Waiting 30 seconds for cluster to reconcile..."
        sleep 30
    else
        echo "✅ Cluster is in Succeeded state. Using existing."
    fi
else
    echo "Creating new private AKS cluster..."

    # Create cluster with error handling
    # Using --disable-public-fqdn and --private-dns-zone to prevent ANY public IP
    # This requires VPN/ExpressRoute for access
    echo -e "${YELLOW}Note: This cluster will have NO public IPs (compliant with your Azure policy)${NC}"
    echo "You will need VPN to access the cluster."
    echo ""

    if az aks create \
        --resource-group $RESOURCE_GROUP \
        --name $AKS_CLUSTER_NAME \
        --location $LOCATION \
        --network-plugin azure \
        --vnet-subnet-id $SUBNET_ID \
        --service-cidr $SERVICE_CIDR \
        --dns-service-ip $DNS_SERVICE_IP \
        --enable-private-cluster \
        --disable-public-fqdn \
        --private-dns-zone system \
        --node-count 2 \
        --node-vm-size Standard_B2s \
        --enable-managed-identity \
        --generate-ssh-keys \
        --attach-acr $ACR_NAME \
        --output none; then

        echo "✅ Private AKS cluster created"
    else
        echo -e "${RED}❌ Failed to create AKS cluster${NC}"
        echo "Check Azure Portal for details or try:"
        echo "  az aks delete --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER_NAME --yes"
        echo "  Then run this script again"
        exit 1
    fi
fi
echo ""

# Get AKS credentials
echo -e "${GREEN}[6/10] Getting AKS credentials...${NC}"

# Wait a moment for cluster to be fully ready
echo "Waiting for cluster to be fully ready..."
sleep 10

# Try to get credentials with error handling
if az aks get-credentials \
    --resource-group $RESOURCE_GROUP \
    --name $AKS_CLUSTER_NAME \
    --overwrite-existing \
    --output none 2>/dev/null; then

    echo "✅ Credentials configured"
else
    echo -e "${YELLOW}⚠️  Failed to get credentials on first try. Attempting fix...${NC}"

    # Try to reconcile the cluster
    az aks update \
        --resource-group $RESOURCE_GROUP \
        --name $AKS_CLUSTER_NAME \
        --output none || true

    echo "Waiting 30 seconds for cluster to reconcile..."
    sleep 30

    # Try again
    if az aks get-credentials \
        --resource-group $RESOURCE_GROUP \
        --name $AKS_CLUSTER_NAME \
        --overwrite-existing \
        --output none; then

        echo "✅ Credentials configured after reconciliation"
    else
        echo -e "${RED}❌ Failed to get AKS credentials${NC}"
        echo ""
        echo "The cluster may be corrupted. To fix:"
        echo "  1. Delete the cluster:"
        echo "     az aks delete --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER_NAME --yes"
        echo ""
        echo "  2. Run this script again"
        exit 1
    fi
fi
echo ""

# Create namespace
echo -e "${GREEN}[7/10] Creating namespace...${NC}"
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f - > /dev/null
echo "✅ Namespace created: $NAMESPACE"
echo ""

# Create ConfigMap
echo -e "${GREEN}[8/10] Creating ConfigMap...${NC}"
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

# Deploy minimal Kafka
echo -e "${GREEN}[9/10] Deploying Kafka...${NC}"
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
  selector:
    app: kafka
EOF
echo "✅ Kafka deployed"
echo ""

# Deploy LRGPS receiver with PRIVATE service (ClusterIP, not LoadBalancer)
echo -e "${GREEN}[10/10] Deploying LRGPS receiver (private service)...${NC}"

kubectl apply -f - > /dev/null <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: lrgps-receiver
  namespace: $NAMESPACE
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
---
apiVersion: v1
kind: Service
metadata:
  name: lrgps-receiver
  namespace: $NAMESPACE
spec:
  type: ClusterIP
  ports:
  - port: 8083
    targetPort: 8083
    protocol: TCP
    name: http
  selector:
    app: lrgps-receiver
EOF

echo "✅ LRGPS receiver deployed (private access only)"
echo ""

# Wait for deployment
echo -e "${BLUE}Waiting for LRGPS receiver to be ready...${NC}"
kubectl rollout status deployment/lrgps-receiver -n $NAMESPACE --timeout=300s
echo ""

# Get internal IP
INTERNAL_IP=$(kubectl get svc lrgps-receiver -n $NAMESPACE -o jsonpath='{.spec.clusterIP}')

# Success message
echo "========================================================"
echo -e "${GREEN}✅ Private LRGPS Environment Ready!${NC}"
echo "========================================================"
echo ""
echo -e "${BLUE}🔒 Security: PRIVATE (VPN-Only Access)${NC}"
echo ""
echo "  • No public IP exposed"
echo "  • Accessible only through VPN"
echo "  • Internal cluster IP: $INTERNAL_IP:8083"
echo ""

echo -e "${BLUE}📡 How to Access (NO PUBLIC IP):${NC}"
echo ""
echo "  The LRGPS endpoint has NO PUBLIC IP and is accessible ONLY via:"
echo ""
echo "  Method 1: kubectl port-forward (Recommended for initial testing)"
echo "    # Connect to VPN, then run:"
echo "    kubectl port-forward svc/lrgps-receiver 8083:8083 -n $NAMESPACE"
echo "    # Access: http://localhost:8083/lrgps"
echo ""
echo "  Method 2: From inside VNet (for on-premise ExpressLink)"
echo "    # Requires VPN Gateway or ExpressRoute to connect on-premise to Azure VNet"
echo "    # ExpressLink can then access: http://$INTERNAL_IP:8083/lrgps"
echo ""
echo -e "${YELLOW}⚠️  IMPORTANT: This setup does NOT create a LoadBalancer or public IP${NC}"
echo ""

echo -e "${BLUE}🧪 Test Commands (from VPN):${NC}"
echo ""
echo "  1. Start port-forward:"
echo "     kubectl port-forward svc/lrgps-receiver 8083:8083 -n $NAMESPACE"
echo ""
echo "  2. In another terminal, test:"
echo "     curl http://localhost:8083/health"
echo ""
echo "  3. Send test data:"
echo "     curl -X POST http://localhost:8083/lrgps \\"
echo "       -H 'Content-Type: application/json' \\"
echo "       -d '{\"vehicle_id\":\"LRV-TEST\",\"latitude\":39.7392,\"longitude\":-104.9903}'"
echo ""

echo -e "${BLUE}📊 Monitor:${NC}"
echo "  kubectl get pods -n $NAMESPACE"
echo "  kubectl logs -f deployment/lrgps-receiver -n $NAMESPACE"
echo ""

echo -e "${BLUE}🔗 VPN Requirements:${NC}"
echo "  • Must be connected to work VPN to access cluster"
echo "  • AKS API server is private (no public endpoint)"
echo "  • All traffic stays within Azure VNet"
echo ""

echo -e "${BLUE}🗑️  Cleanup:${NC}"
echo "  az group delete --name $RESOURCE_GROUP --yes --no-wait"
echo ""

echo -e "${BLUE}💰 Estimated Cost:${NC}"
echo "  ~\$80-100/month (same as public setup, but more secure)"
echo ""

echo "========================================================"
echo -e "${GREEN}Setup Complete! (Private Network)${NC}"
echo "========================================================"
