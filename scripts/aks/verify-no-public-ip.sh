#!/bin/bash

# Verify LRGPS Private Setup Has NO Public IP

set -e

NAMESPACE="${1:-rtd-private}"

echo "========================================================"
echo "  Verifying NO Public IP Exists"
echo "========================================================"
echo ""

# Check if connected to cluster
if ! kubectl cluster-info &> /dev/null; then
    echo "❌ Not connected to Kubernetes cluster"
    echo "Run: az aks get-credentials --resource-group <rg> --name <cluster>"
    exit 1
fi

echo "✅ Connected to cluster"
echo ""

# Get service details
echo "Checking LRGPS receiver service..."
echo ""

SERVICE_TYPE=$(kubectl get svc lrgps-receiver -n $NAMESPACE -o jsonpath='{.spec.type}' 2>/dev/null || echo "NOT_FOUND")

if [ "$SERVICE_TYPE" == "NOT_FOUND" ]; then
    echo "❌ Service 'lrgps-receiver' not found in namespace '$NAMESPACE'"
    exit 1
fi

echo "Service Type: $SERVICE_TYPE"
echo ""

# Verify it's ClusterIP (no public IP)
if [ "$SERVICE_TYPE" == "ClusterIP" ]; then
    echo "✅ CORRECT: Service is ClusterIP (NO public IP)"
    echo ""

    CLUSTER_IP=$(kubectl get svc lrgps-receiver -n $NAMESPACE -o jsonpath='{.spec.clusterIP}')
    echo "Internal ClusterIP: $CLUSTER_IP"
    echo ""
    echo "This IP is ONLY accessible:"
    echo "  • Via kubectl port-forward"
    echo "  • From within the Azure VNet (via VPN/ExpressRoute)"
    echo ""

elif [ "$SERVICE_TYPE" == "LoadBalancer" ]; then
    echo "⚠️  WARNING: Service is LoadBalancer type!"
    echo ""

    EXTERNAL_IP=$(kubectl get svc lrgps-receiver -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "pending")

    if [ "$EXTERNAL_IP" != "pending" ] && [ -n "$EXTERNAL_IP" ]; then
        echo "❌ PROBLEM: Public IP exists: $EXTERNAL_IP"
        echo ""
        echo "To remove public IP, run:"
        echo "  kubectl patch svc lrgps-receiver -n $NAMESPACE -p '{\"spec\":{\"type\":\"ClusterIP\"}}'"
    else
        echo "⚠️  LoadBalancer IP is pending (no public IP yet)"
        echo ""
        echo "To prevent public IP assignment, run:"
        echo "  kubectl patch svc lrgps-receiver -n $NAMESPACE -p '{\"spec\":{\"type\":\"ClusterIP\"}}'"
    fi
    echo ""

else
    echo "⚠️  Unknown service type: $SERVICE_TYPE"
    echo ""
fi

# Check for any LoadBalancer services in namespace
echo "Checking for ANY LoadBalancer services in namespace '$NAMESPACE'..."
echo ""

LB_SERVICES=$(kubectl get svc -n $NAMESPACE -o json | jq -r '.items[] | select(.spec.type=="LoadBalancer") | .metadata.name' 2>/dev/null || echo "")

if [ -z "$LB_SERVICES" ]; then
    echo "✅ CORRECT: No LoadBalancer services found"
    echo ""
else
    echo "⚠️  WARNING: LoadBalancer services found:"
    echo "$LB_SERVICES"
    echo ""
fi

# Final verdict
echo "========================================================"
if [ "$SERVICE_TYPE" == "ClusterIP" ] && [ -z "$LB_SERVICES" ]; then
    echo "✅ VERIFICATION PASSED"
    echo ""
    echo "NO public IP exists. Service is private (VPN-only)."
else
    echo "⚠️  VERIFICATION FAILED"
    echo ""
    echo "Public IP may exist or service is not ClusterIP."
fi
echo "========================================================"
echo ""

# Show access instructions
echo "To access the LRGPS endpoint:"
echo ""
echo "  1. Connect to your work VPN"
echo "  2. Run: kubectl port-forward svc/lrgps-receiver 8083:8083 -n $NAMESPACE"
echo "  3. Access: http://localhost:8083/lrgps"
echo ""
