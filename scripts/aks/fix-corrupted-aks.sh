#!/bin/bash

# Fix Corrupted AKS Cluster
# Use this when you get "ControlPlaneNotFound" or similar errors

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

RESOURCE_GROUP="${1:-rtd-lrgps-private-rg}"
AKS_CLUSTER_NAME="${2:-rtd-lrgps-private}"

echo "========================================================"
echo "  Fix Corrupted AKS Cluster"
echo "========================================================"
echo ""
echo "Resource Group: $RESOURCE_GROUP"
echo "Cluster Name: $AKS_CLUSTER_NAME"
echo ""

# Check if cluster exists
if ! az aks show --name $AKS_CLUSTER_NAME --resource-group $RESOURCE_GROUP &> /dev/null; then
    echo -e "${YELLOW}⚠️  Cluster not found. Nothing to fix.${NC}"
    exit 0
fi

echo -e "${BLUE}Current cluster state:${NC}"
PROVISIONING_STATE=$(az aks show --name $AKS_CLUSTER_NAME --resource-group $RESOURCE_GROUP --query "provisioningState" -o tsv 2>/dev/null || echo "Unknown")
echo "  Provisioning State: $PROVISIONING_STATE"
echo ""

# Option 1: Try to fix with update
echo -e "${GREEN}Option 1: Try to fix with 'az aks update'${NC}"
echo ""
read -p "Try to fix the cluster? (y/n) " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Attempting to reconcile cluster..."

    if az aks update \
        --resource-group $RESOURCE_GROUP \
        --name $AKS_CLUSTER_NAME \
        --output none; then

        echo "✅ Update succeeded"
        echo ""
        echo "Waiting 30 seconds for cluster to stabilize..."
        sleep 30

        # Try to get credentials
        echo "Testing cluster access..."
        if az aks get-credentials \
            --resource-group $RESOURCE_GROUP \
            --name $AKS_CLUSTER_NAME \
            --overwrite-existing \
            --output none 2>/dev/null; then

            echo "✅ Cluster is now accessible!"
            echo ""
            echo "Test with: kubectl get nodes"
            exit 0
        else
            echo -e "${YELLOW}⚠️  Still can't access cluster${NC}"
        fi
    else
        echo -e "${RED}❌ Update failed${NC}"
    fi

    echo ""
fi

# Option 2: Delete and recreate
echo -e "${YELLOW}Option 2: Delete and recreate the cluster${NC}"
echo ""
echo -e "${RED}WARNING: This will DELETE the existing cluster!${NC}"
echo "You will need to run the setup script again to recreate it."
echo ""
read -p "Delete the corrupted cluster? (y/n) " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Deleting cluster..."

    if az aks delete \
        --resource-group $RESOURCE_GROUP \
        --name $AKS_CLUSTER_NAME \
        --yes \
        --no-wait; then

        echo "✅ Cluster deletion started (running in background)"
        echo ""
        echo "Next steps:"
        echo "  1. Wait 5-10 minutes for deletion to complete"
        echo "  2. Run: ./scripts/aks/setup-lrgps-private.sh"
        echo ""
        echo "Check deletion status with:"
        echo "  az aks show --name $AKS_CLUSTER_NAME --resource-group $RESOURCE_GROUP"
    else
        echo -e "${RED}❌ Failed to delete cluster${NC}"
        echo ""
        echo "Try manually in Azure Portal or with:"
        echo "  az group delete --name $RESOURCE_GROUP --yes"
    fi
else
    echo "Cancelled."
fi

echo ""
echo "========================================================"
echo "Done"
echo "========================================================"
