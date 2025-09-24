#!/bin/bash

# GitHub Secrets Setup Script
# This script helps configure GitHub repository secrets for the RTD GTFS Pipeline
#
# Prerequisites:
# - GitHub CLI (gh) installed and authenticated
# - Repository access with secrets management permissions

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}   RTD GTFS Pipeline - GitHub Secrets Setup${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI (gh) is not installed${NC}"
    echo -e "${YELLOW}📦 Install it from: https://cli.github.com/${NC}"
    exit 1
fi

# Check if gh is authenticated
if ! gh auth status &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI is not authenticated${NC}"
    echo -e "${YELLOW}🔐 Run: gh auth login${NC}"
    exit 1
fi

# Get repository information
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo "")

if [ -z "$REPO" ]; then
    echo -e "${YELLOW}⚠️  Could not auto-detect repository. Please enter it manually.${NC}"
    read -p "Repository (owner/repo): " REPO
fi

echo -e "${GREEN}✅ Using repository: ${REPO}${NC}"
echo

# Function to set a secret
set_secret() {
    local secret_name="$1"
    local secret_value="$2"
    local description="$3"

    if [ -z "$secret_value" ]; then
        echo -e "${YELLOW}⏭️  Skipping ${secret_name} (no value provided)${NC}"
        return
    fi

    echo -e "${BLUE}🔐 Setting ${secret_name}...${NC} ${description}"
    if echo -n "$secret_value" | gh secret set "$secret_name" -R "$REPO"; then
        echo -e "${GREEN}✅ ${secret_name} configured${NC}"
    else
        echo -e "${RED}❌ Failed to set ${secret_name}${NC}"
        return 1
    fi
}

# Function to prompt for secret
prompt_secret() {
    local secret_name="$1"
    local description="$2"
    local default_value="$3"
    local is_password="$4"

    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}${secret_name}${NC}"
    echo -e "Description: ${description}"

    if [ -n "$default_value" ]; then
        echo -e "Default: ${default_value}"
    fi

    local value=""
    if [ "$is_password" == "true" ]; then
        read -s -p "Enter value (hidden): " value
        echo
    else
        read -p "Enter value: " value
    fi

    # Use default if no value provided and default exists
    if [ -z "$value" ] && [ -n "$default_value" ]; then
        value="$default_value"
    fi

    echo "$value"
}

# Check for existing .env file for defaults
ENV_FILE=""
if [ -f "$PROJECT_ROOT/.env" ]; then
    ENV_FILE="$PROJECT_ROOT/.env"
    echo -e "${GREEN}📄 Found .env file. Using values as defaults.${NC}"
elif [ -f "$PROJECT_ROOT/.env.local" ]; then
    ENV_FILE="$PROJECT_ROOT/.env.local"
    echo -e "${GREEN}📄 Found .env.local file. Using values as defaults.${NC}"
fi

# Load defaults from .env if it exists
if [ -n "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
fi

echo
echo -e "${BLUE}🔧 Configure GitHub Secrets${NC}"
echo -e "${YELLOW}Press Enter to use defaults where available${NC}"
echo

# Required Secrets
echo -e "${RED}═══ REQUIRED SECRETS ═══${NC}"

TIS_USERNAME=$(prompt_secret "TIS_PROXY_USERNAME" "RTD TIS Proxy username (required)" "${TIS_PROXY_USERNAME:-}" "false")
TIS_PASSWORD=$(prompt_secret "TIS_PROXY_PASSWORD" "RTD TIS Proxy password (required)" "" "true")

if [ -z "$TIS_USERNAME" ] || [ -z "$TIS_PASSWORD" ]; then
    echo
    echo -e "${RED}❌ Username and password are required!${NC}"
    exit 1
fi

echo
echo -e "${BLUE}═══ OPTIONAL CONFIGURATION ═══${NC}"

TIS_HOST=$(prompt_secret "TIS_PROXY_HOST" "TIS Proxy host URL" "${TIS_PROXY_HOST:-http://tisproxy.rtd-denver.com}" "false")
TIS_SERVICE=$(prompt_secret "TIS_PROXY_SERVICE" "TIS Proxy service name" "${TIS_PROXY_SERVICE:-siri}" "false")
TIS_TTL=$(prompt_secret "TIS_PROXY_TTL" "Subscription TTL in milliseconds" "${TIS_PROXY_TTL:-90000}" "false")

echo
echo -e "${BLUE}═══ ADDITIONAL SERVICES ═══${NC}"

RAILCOMM_SERVICE=$(prompt_secret "RAILCOMM_SERVICE" "Rail communication service name" "${RAILCOMM_SERVICE:-railcomm}" "false")
RAILCOMM_TTL=$(prompt_secret "RAILCOMM_TTL" "Rail communication TTL" "${RAILCOMM_TTL:-90000}" "false")
LRGPS_SERVICE=$(prompt_secret "LRGPS_SERVICE" "LRGPS service name" "${LRGPS_SERVICE:-lrgps}" "false")
LRGPS_TTL=$(prompt_secret "LRGPS_TTL" "LRGPS TTL" "${LRGPS_TTL:-90000}" "false")

echo
echo -e "${BLUE}═══ DEPLOYMENT CONFIGURATION ═══${NC}"

KAFKA_SERVERS=$(prompt_secret "KAFKA_BOOTSTRAP_SERVERS" "Kafka bootstrap servers" "${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}" "false")

echo
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}📝 Setting GitHub Secrets...${NC}"
echo

# Set the secrets
set_secret "TIS_PROXY_USERNAME" "$TIS_USERNAME" "(Required)"
set_secret "TIS_PROXY_PASSWORD" "$TIS_PASSWORD" "(Required)"
set_secret "TIS_PROXY_HOST" "$TIS_HOST" "(Optional)"
set_secret "TIS_PROXY_SERVICE" "$TIS_SERVICE" "(Optional)"
set_secret "TIS_PROXY_TTL" "$TIS_TTL" "(Optional)"
set_secret "RAILCOMM_SERVICE" "$RAILCOMM_SERVICE" "(Optional)"
set_secret "RAILCOMM_TTL" "$RAILCOMM_TTL" "(Optional)"
set_secret "LRGPS_SERVICE" "$LRGPS_SERVICE" "(Optional)"
set_secret "LRGPS_TTL" "$LRGPS_TTL" "(Optional)"
set_secret "KAFKA_BOOTSTRAP_SERVERS" "$KAFKA_SERVERS" "(Optional)"

echo
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ GitHub Secrets configuration complete!${NC}"
echo
echo -e "${YELLOW}📋 Next steps:${NC}"
echo "  1. Verify secrets in GitHub: https://github.com/${REPO}/settings/secrets/actions"
echo "  2. Run the test workflow: gh workflow run test.yml"
echo "  3. Check workflow status: gh run list --workflow=test.yml"
echo
echo -e "${BLUE}🚀 Your GitHub Actions workflows are now ready to use!${NC}"