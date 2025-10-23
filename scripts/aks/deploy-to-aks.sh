#!/bin/bash

# RTD GTFS Pipeline - AKS Automatic Deployment Script
# This script deploys the entire RTD pipeline to Azure Kubernetes Service (AKS) Automatic

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."

    if ! command -v az &> /dev/null; then
        print_error "Azure CLI not found. Please install: https://aka.ms/InstallAzureCLIDeb"
        exit 1
    fi

    if ! command -v kubectl &> /dev/null; then
        print_error "kubectl not found. Please install: az aks install-cli"
        exit 1
    fi

    if ! az account show &> /dev/null; then
        print_error "Not logged into Azure. Please run: az login"
        exit 1
    fi

    print_info "Prerequisites check passed ✓"
}

# Set environment variables
set_environment() {
    print_info "Setting environment variables..."

    export RESOURCE_GROUP="${RESOURCE_GROUP:-rtd-pipeline-rg}"
    export LOCATION="${LOCATION:-eastus}"
    export AKS_CLUSTER_NAME="${AKS_CLUSTER_NAME:-rtd-pipeline-aks}"
    export ACR_NAME="${ACR_NAME:-rtdpipelineacr}"
    export KEY_VAULT_NAME="${KEY_VAULT_NAME:-rtd-pipeline-kv}"

    print_info "Resource Group: $RESOURCE_GROUP"
    print_info "Location: $LOCATION"
    print_info "AKS Cluster: $AKS_CLUSTER_NAME"
}

# Create Azure resources
create_azure_resources() {
    print_info "Creating Azure resources..."

    # Create resource group
    if ! az group show --name $RESOURCE_GROUP &> /dev/null; then
        print_info "Creating resource group..."
        az group create --name $RESOURCE_GROUP --location $LOCATION
    else
        print_warn "Resource group already exists"
    fi

    # Create ACR
    if ! az acr show --name $ACR_NAME --resource-group $RESOURCE_GROUP &> /dev/null; then
        print_info "Creating Azure Container Registry..."
        az acr create --resource-group $RESOURCE_GROUP --name $ACR_NAME --sku Standard
    else
        print_warn "ACR already exists"
    fi

    # Create Key Vault
    if ! az keyvault show --name $KEY_VAULT_NAME --resource-group $RESOURCE_GROUP &> /dev/null; then
        print_info "Creating Azure Key Vault..."
        az keyvault create \
            --name $KEY_VAULT_NAME \
            --resource-group $RESOURCE_GROUP \
            --location $LOCATION \
            --enable-rbac-authorization
    else
        print_warn "Key Vault already exists"
    fi
}

# Create AKS Automatic cluster
create_aks_cluster() {
    print_info "Creating AKS Automatic cluster (this may take 10-15 minutes)..."

    if ! az aks show --name $AKS_CLUSTER_NAME --resource-group $RESOURCE_GROUP &> /dev/null; then
        az aks create \
            --resource-group $RESOURCE_GROUP \
            --name $AKS_CLUSTER_NAME \
            --tier automatic \
            --location $LOCATION \
            --generate-ssh-keys \
            --attach-acr $ACR_NAME \
            --enable-workload-identity \
            --enable-oidc-issuer

        print_info "AKS cluster created ✓"
    else
        print_warn "AKS cluster already exists"
    fi

    # Get credentials
    print_info "Getting AKS credentials..."
    az aks get-credentials \
        --resource-group $RESOURCE_GROUP \
        --name $AKS_CLUSTER_NAME \
        --overwrite-existing

    # Verify cluster
    kubectl get nodes
}

# Build and push Docker images
build_and_push_images() {
    print_info "Building and pushing Docker images..."

    # Login to ACR
    az acr login --name $ACR_NAME

    # Build image
    print_info "Building Docker image..."
    docker build -t $ACR_NAME.azurecr.io/rtd-pipeline:latest .

    # Push image
    print_info "Pushing image to ACR..."
    docker push $ACR_NAME.azurecr.io/rtd-pipeline:latest

    print_info "Image pushed successfully ✓"
}

# Configure secrets
configure_secrets() {
    print_info "Configuring secrets in Azure Key Vault..."

    if [ -z "$TIS_PROXY_USERNAME" ] || [ -z "$TIS_PROXY_PASSWORD" ]; then
        print_warn "TIS_PROXY_USERNAME or TIS_PROXY_PASSWORD not set"
        print_warn "Please set these environment variables or manually add to Key Vault"
        return
    fi

    az keyvault secret set \
        --vault-name $KEY_VAULT_NAME \
        --name "tis-proxy-username" \
        --value "$TIS_PROXY_USERNAME"

    az keyvault secret set \
        --vault-name $KEY_VAULT_NAME \
        --name "tis-proxy-password" \
        --value "$TIS_PROXY_PASSWORD"

    print_info "Secrets configured ✓"
}

# Deploy Kubernetes resources
deploy_kubernetes() {
    print_info "Deploying Kubernetes resources..."

    # Update ACR name in deployment files
    find k8s/deployments -name "*.yaml" -type f -exec sed -i.bak "s/\${ACR_NAME}/$ACR_NAME/g" {} \;

    # Create namespace
    kubectl apply -f k8s/namespace.yaml

    # Apply ConfigMaps
    kubectl apply -f k8s/configmaps/

    # Create secrets (template - update with actual values)
    print_warn "Please update k8s/secrets/rtd-secrets-template.yaml with actual credentials"
    print_warn "Then run: kubectl apply -f k8s/secrets/"

    # Deploy storage
    kubectl apply -f k8s/storage/

    # Deploy Zookeeper and Kafka
    print_info "Deploying Kafka and Zookeeper..."
    kubectl apply -f k8s/deployments/zookeeper.yaml
    kubectl apply -f k8s/deployments/kafka.yaml

    # Wait for Kafka to be ready
    print_info "Waiting for Kafka to be ready..."
    kubectl wait --for=condition=ready pod -l app=kafka -n rtd-pipeline --timeout=300s || true

    # Deploy receivers
    print_info "Deploying HTTP receivers..."
    kubectl apply -f k8s/deployments/bus-receiver.yaml
    kubectl apply -f k8s/deployments/rail-receiver.yaml

    # Deploy services
    if [ -d "k8s/services" ]; then
        kubectl apply -f k8s/services/
    fi

    # Deploy ingress
    if [ -d "k8s/ingress" ]; then
        kubectl apply -f k8s/ingress/
    fi

    # Deploy autoscaling
    if [ -d "k8s/autoscaling" ]; then
        kubectl apply -f k8s/autoscaling/
    fi

    print_info "Kubernetes resources deployed ✓"
}

# Verify deployment
verify_deployment() {
    print_info "Verifying deployment..."

    echo ""
    print_info "Pods status:"
    kubectl get pods -n rtd-pipeline

    echo ""
    print_info "Services:"
    kubectl get svc -n rtd-pipeline

    echo ""
    print_info "Deployments:"
    kubectl get deployments -n rtd-pipeline

    echo ""
    print_info "StatefulSets:"
    kubectl get statefulsets -n rtd-pipeline
}

# Main execution
main() {
    echo "================================================"
    echo "RTD GTFS Pipeline - AKS Automatic Deployment"
    echo "================================================"
    echo ""

    check_prerequisites
    set_environment
    create_azure_resources
    create_aks_cluster
    build_and_push_images
    configure_secrets
    deploy_kubernetes
    verify_deployment

    echo ""
    echo "================================================"
    print_info "Deployment completed successfully! ✓"
    echo "================================================"
    echo ""
    print_info "Next steps:"
    echo "  1. Configure secrets: Update k8s/secrets/rtd-secrets-template.yaml"
    echo "  2. Get external IP: kubectl get svc -n rtd-pipeline"
    echo "  3. View logs: kubectl logs -f <pod-name> -n rtd-pipeline"
    echo "  4. Access API: http://<external-ip>:8080/api/health"
}

main "$@"
