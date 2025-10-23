#!/bin/bash

# LRGPS Private Setup - Using Existing NAT Gateway or Firewall
# For Azure subscriptions with public IP restrictions

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "========================================================"
echo "  LRGPS Private Setup - With Existing NAT/Firewall"
echo "========================================================"
echo ""
echo "This script is for Azure subscriptions that block public IP creation."
echo "It requires you to have an existing NAT Gateway or Azure Firewall."
echo ""

# Configuration
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
echo -e "${GREEN}[1/3] Checking prerequisites...${NC}"
if ! command -v az &> /dev/null; then
    echo -e "${RED}❌ Azure CLI not found${NC}"
    exit 1
fi

if ! az account show &> /dev/null; then
    echo -e "${RED}❌ Not logged into Azure. Run: az login${NC}"
    exit 1
fi

if [ -z "$ACR_NAME" ]; then
    echo -e "${RED}❌ ACR_NAME not set${NC}"
    exit 1
fi

echo "✅ Prerequisites OK"
echo ""

echo -e "${YELLOW}⚠️  IMPORTANT: This requires Azure admin help${NC}"
echo ""
echo "Your Azure subscription blocks public IP creation."
echo "You need ONE of the following:"
echo ""
echo "  Option A: Azure Policy Exemption"
echo "    Contact Azure admins to allow public IPs in AKS managed resource groups"
echo "    Pattern: MC_*_eastus"
echo ""
echo "  Option B: Existing NAT Gateway"
echo "    Use your organization's existing NAT Gateway for outbound traffic"
echo ""
echo "  Option C: Existing Azure Firewall"
echo "    Route AKS traffic through existing firewall"
echo ""

read -p "Do you have an existing NAT Gateway or Firewall? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Without NAT Gateway or Firewall, you must:"
    echo "  1. Contact your Azure admins"
    echo "  2. Request policy exemption for AKS"
    echo "  3. They need to allow public IPs in MC_* resource groups"
    echo ""
    echo "Once exemption is granted, run: ./scripts/aks/setup-lrgps-private.sh"
    exit 1
fi

echo ""
echo -e "${GREEN}[2/3] Checking for NAT Gateway...${NC}"

# Try to find NAT Gateway
NAT_GATEWAY=$(az network nat gateway list --resource-group $RESOURCE_GROUP --query "[0].id" -o tsv 2>/dev/null || echo "")

if [ -z "$NAT_GATEWAY" ]; then
    echo -e "${YELLOW}⚠️  No NAT Gateway found in resource group${NC}"
    echo ""
    echo "Please provide your NAT Gateway resource ID:"
    echo "(Format: /subscriptions/.../resourceGroups/.../providers/Microsoft.Network/natGateways/...)"
    read NAT_GATEWAY

    if [ -z "$NAT_GATEWAY" ]; then
        echo -e "${RED}❌ NAT Gateway required${NC}"
        exit 1
    fi
fi

echo "Using NAT Gateway: $NAT_GATEWAY"
echo ""

echo -e "${GREEN}[3/3] Configuration Instructions${NC}"
echo ""
echo "Manual steps required (Azure admin):"
echo ""
echo "1. Create AKS cluster with userDefinedRouting:"
echo ""
cat <<'EOF'
az aks create \
  --resource-group rtd-lrgps-private-rg \
  --name rtd-lrgps-private \
  --location eastus \
  --network-plugin azure \
  --vnet-subnet-id /subscriptions/72cd9ba3-1dd0-4c4b-a012-9d55d8d59c59/resourceGroups/rtd-lrgps-private-rg/providers/Microsoft.Network/virtualNetworks/rtd-vnet/subnets/aks-subnet \
  --service-cidr 10.1.0.0/16 \
  --dns-service-ip 10.1.0.10 \
  --enable-private-cluster \
  --disable-public-fqdn \
  --private-dns-zone system \
  --outbound-type userDefinedRouting \
  --node-count 2 \
  --node-vm-size Standard_B2s \
  --enable-managed-identity \
  --attach-acr rtdpipelineacr
EOF
echo ""
echo "2. Associate NAT Gateway with AKS subnet:"
echo ""
echo "az network vnet subnet update \\"
echo "  --resource-group rtd-lrgps-private-rg \\"
echo "  --vnet-name rtd-vnet \\"
echo "  --name aks-subnet \\"
echo "  --nat-gateway $NAT_GATEWAY"
echo ""
echo "3. After cluster is created, get credentials:"
echo ""
echo "az aks get-credentials --resource-group rtd-lrgps-private-rg --name rtd-lrgps-private"
echo ""
echo "4. Deploy LRGPS receiver:"
echo ""
echo "kubectl create namespace rtd-private"
echo "kubectl apply -f k8s/deployments/lrgps-receiver.yaml"
echo ""
echo "========================================================"
echo "Contact your Azure admin with these commands"
echo "========================================================"
