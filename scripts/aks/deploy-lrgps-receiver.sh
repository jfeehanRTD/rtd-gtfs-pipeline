#!/bin/bash

# Deploy LRGPS HTTP Receiver to AKS
# This script deploys the LRGPS receiver that accepts data from on-premise ExpressLink

set -e  # Exit on error

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "========================================================"
echo "  RTD LRGPS HTTP Receiver - AKS Deployment"
echo "========================================================"
echo ""

# Configuration
RESOURCE_GROUP="${RESOURCE_GROUP:-rtd-pipeline-rg}"
AKS_CLUSTER_NAME="${AKS_CLUSTER_NAME:-rtd-pipeline-aks}"
NAMESPACE="${NAMESPACE:-rtd-pipeline}"
ACR_NAME="${ACR_NAME}"

# Check prerequisites
echo -e "${GREEN}[1/7] Checking prerequisites...${NC}"
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}❌ kubectl not found. Install: az aks install-cli${NC}"
    exit 1
fi

if ! command -v az &> /dev/null; then
    echo -e "${RED}❌ Azure CLI not found. Install from: https://aka.ms/InstallAzureCLIDeb${NC}"
    exit 1
fi

if [ -z "$ACR_NAME" ]; then
    echo -e "${RED}❌ ACR_NAME not set. Please set ACR_NAME environment variable.${NC}"
    exit 1
fi

echo "✅ Prerequisites OK"
echo ""

# Get AKS credentials
echo -e "${GREEN}[2/7] Getting AKS credentials...${NC}"
az aks get-credentials \
    --resource-group $RESOURCE_GROUP \
    --name $AKS_CLUSTER_NAME \
    --overwrite-existing \
    --output none

echo "✅ AKS credentials configured"
echo ""

# Check if namespace exists
echo -e "${GREEN}[3/7] Checking namespace...${NC}"
if ! kubectl get namespace $NAMESPACE &> /dev/null; then
    echo -e "${YELLOW}⚠️ Namespace '$NAMESPACE' not found. Creating...${NC}"
    kubectl create namespace $NAMESPACE
fi
echo "✅ Namespace ready: $NAMESPACE"
echo ""

# Update ACR name in deployment manifest
echo -e "${GREEN}[4/7] Updating deployment manifest with ACR name...${NC}"
TEMP_MANIFEST=$(mktemp)
sed "s/\${ACR_NAME}/$ACR_NAME/g" k8s/deployments/lrgps-receiver.yaml > $TEMP_MANIFEST
echo "✅ Manifest updated with ACR: $ACR_NAME"
echo ""

# Deploy LRGPS receiver
echo -e "${GREEN}[5/7] Deploying LRGPS receiver...${NC}"
kubectl apply -f $TEMP_MANIFEST

# Clean up temp file
rm $TEMP_MANIFEST

echo "✅ LRGPS receiver deployed"
echo ""

# Wait for deployment to be ready
echo -e "${GREEN}[6/7] Waiting for deployment to be ready...${NC}"
kubectl rollout status deployment/lrgps-receiver -n $NAMESPACE --timeout=300s
echo "✅ Deployment ready"
echo ""

# Get service external IP
echo -e "${GREEN}[7/7] Getting service information...${NC}"
echo ""
echo "Waiting for LoadBalancer IP assignment (this may take 2-3 minutes)..."

# Wait up to 5 minutes for external IP
for i in {1..30}; do
    EXTERNAL_IP=$(kubectl get svc lrgps-receiver -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    if [ -n "$EXTERNAL_IP" ]; then
        break
    fi
    echo -n "."
    sleep 10
done
echo ""

if [ -n "$EXTERNAL_IP" ]; then
    echo "✅ LoadBalancer IP assigned: $EXTERNAL_IP"
else
    echo -e "${YELLOW}⚠️ LoadBalancer IP not yet assigned. Check status with:${NC}"
    echo "   kubectl get svc lrgps-receiver -n $NAMESPACE"
fi
echo ""

# Success message
echo "========================================================"
echo -e "${GREEN}✅ LRGPS Receiver Deployment Complete!${NC}"
echo "========================================================"
echo ""
echo "📋 Deployment Information:"
echo ""
echo "  • Resource Group: $RESOURCE_GROUP"
echo "  • AKS Cluster: $AKS_CLUSTER_NAME"
echo "  • Namespace: $NAMESPACE"
echo "  • Service: lrgps-receiver"
echo "  • Port: 8083"
if [ -n "$EXTERNAL_IP" ]; then
    echo "  • External IP: $EXTERNAL_IP"
fi
echo ""
echo "🔌 LRGPS Endpoints (from Azure):"
echo ""
if [ -n "$EXTERNAL_IP" ]; then
    echo "  Data Ingestion:"
    echo "    POST http://$EXTERNAL_IP:8083/lrgps"
    echo ""
    echo "  Health Check:"
    echo "    GET http://$EXTERNAL_IP:8083/health"
    echo ""
    echo "  Status:"
    echo "    GET http://$EXTERNAL_IP:8083/status"
    echo ""
    echo "  Latest Data:"
    echo "    GET http://$EXTERNAL_IP:8083/lrgps/latest"
    echo ""
else
    echo "  Run this command to get the external IP:"
    echo "    kubectl get svc lrgps-receiver -n $NAMESPACE"
    echo ""
fi

echo "📝 Configure On-Premise ExpressLink:"
echo ""
if [ -n "$EXTERNAL_IP" ]; then
    echo "  Set ExpressLink to send LRGPS data to:"
    echo "    http://$EXTERNAL_IP:8083/lrgps"
    echo ""
else
    echo "  Wait for external IP, then configure ExpressLink to send data to:"
    echo "    http://<EXTERNAL_IP>:8083/lrgps"
    echo ""
fi

echo "🔍 Useful Commands:"
echo ""
echo "  View pods:"
echo "    kubectl get pods -n $NAMESPACE -l app=lrgps-receiver"
echo ""
echo "  View logs:"
echo "    kubectl logs -f deployment/lrgps-receiver -n $NAMESPACE"
echo ""
echo "  Check service status:"
echo "    kubectl get svc lrgps-receiver -n $NAMESPACE"
echo ""
echo "  Scale deployment:"
echo "    kubectl scale deployment lrgps-receiver --replicas=5 -n $NAMESPACE"
echo ""
echo "  Delete deployment:"
echo "    kubectl delete -f k8s/deployments/lrgps-receiver.yaml"
echo ""
echo "🎯 Data Flow:"
echo ""
echo "  On-Premise ExpressLink → Azure LoadBalancer → LRGPS Receiver Pod → Kafka (rtd.lrgps)"
echo ""
echo -e "${YELLOW}💡 Important:${NC}"
echo "  • Configure your on-premise TIS Proxy (http://tisproxy.rtd-denver.com)"
echo "    to send LRGPS data to the Azure LoadBalancer IP"
echo "  • Ensure firewall rules allow outbound HTTPS from on-premise to Azure"
echo "  • The receiver auto-scales from 2 to 10 replicas based on CPU/memory"
echo ""
