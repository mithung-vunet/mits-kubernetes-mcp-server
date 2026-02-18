#!/bin/bash
# SSH Authentication Setup Script for Kubernetes MCP Server
# Run this script on any new Linux/Mac machine to configure passwordless SSH
#
# Usage:
#   ./setup-ssh.sh user@hostname
#   ./setup-ssh.sh -u myuser -h 192.168.1.100
#   JUMP_SERVER_USER=myuser JUMP_SERVER_HOST=192.168.1.100 ./setup-ssh.sh

set -e

# Default values (can be overridden by args or environment variables)
JUMP_SERVER_USER="${JUMP_SERVER_USER:-}"
JUMP_SERVER_HOST="${JUMP_SERVER_HOST:-}"
KEY_TYPE="${KEY_TYPE:-ed25519}"
KEY_COMMENT="kubernetes-mcp-client"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -u|--user)
            JUMP_SERVER_USER="$2"
            shift 2
            ;;
        -h|--host)
            JUMP_SERVER_HOST="$2"
            shift 2
            ;;
        -k|--key-type)
            KEY_TYPE="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [user@host] [-u user] [-h host] [-k key_type]"
            echo ""
            echo "Examples:"
            echo "  $0 ubuntu@192.168.1.100"
            echo "  $0 -u ubuntu -h 192.168.1.100"
            exit 0
            ;;
        *@*)
            # Parse user@host format
            JUMP_SERVER_USER="${1%@*}"
            JUMP_SERVER_HOST="${1#*@}"
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Prompt if not provided
if [ -z "$JUMP_SERVER_USER" ]; then
    read -p "Enter jump server username: " JUMP_SERVER_USER
fi
if [ -z "$JUMP_SERVER_HOST" ]; then
    read -p "Enter jump server hostname/IP: " JUMP_SERVER_HOST
fi

if [ -z "$JUMP_SERVER_USER" ] || [ -z "$JUMP_SERVER_HOST" ]; then
    echo "Error: Jump server user and host are required"
    echo "Usage: $0 user@hostname"
    exit 1
fi

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

step() { echo -e "\n${CYAN}[$(date +%H:%M:%S)] $1${NC}"; }
success() { echo -e "${GREEN}[OK] $1${NC}"; }
warn() { echo -e "${YELLOW}[WARN] $1${NC}"; }
error() { echo -e "${RED}[ERROR] $1${NC}"; }

# Banner
echo -e "${CYAN}"
echo "========================================"
echo "  Kubernetes MCP SSH Setup Script"
echo "  Jump Server: ${JUMP_SERVER_USER}@${JUMP_SERVER_HOST}"
echo "========================================"
echo -e "${NC}"

# Step 1: Check SSH client
step "Checking SSH client availability..."
if ! command -v ssh &> /dev/null; then
    error "SSH client not found. Please install openssh-client."
    exit 1
fi
success "SSH client found"

# Step 2: Check/Create SSH directory
step "Checking SSH directory..."
SSH_DIR="$HOME/.ssh"
if [ ! -d "$SSH_DIR" ]; then
    mkdir -p "$SSH_DIR"
    chmod 700 "$SSH_DIR"
    success "Created SSH directory: $SSH_DIR"
else
    success "SSH directory exists: $SSH_DIR"
fi

# Step 3: Check/Generate SSH key
step "Checking SSH key..."
PRIVATE_KEY="$SSH_DIR/id_$KEY_TYPE"
PUBLIC_KEY="$PRIVATE_KEY.pub"

if [ -f "$PUBLIC_KEY" ]; then
    success "SSH key already exists: $PUBLIC_KEY"
    read -p "Do you want to generate a new key? (y/N): " GENERATE_NEW
    if [ "$GENERATE_NEW" = "y" ] || [ "$GENERATE_NEW" = "Y" ]; then
        warn "Backing up existing key..."
        TIMESTAMP=$(date +%Y%m%d_%H%M%S)
        mv "$PRIVATE_KEY" "$PRIVATE_KEY.backup_$TIMESTAMP" 2>/dev/null || true
        mv "$PUBLIC_KEY" "$PUBLIC_KEY.backup_$TIMESTAMP" 2>/dev/null || true
        
        echo "Generating new SSH key..."
        ssh-keygen -t "$KEY_TYPE" -C "$KEY_COMMENT" -f "$PRIVATE_KEY" -N ""
        success "New SSH key generated"
    else
        echo "Using existing key."
    fi
else
    echo "Generating SSH key ($KEY_TYPE)..."
    ssh-keygen -t "$KEY_TYPE" -C "$KEY_COMMENT" -f "$PRIVATE_KEY" -N ""
    success "SSH key generated: $PUBLIC_KEY"
fi

# Step 4: Display the public key
step "Your public key:"
PUBLIC_KEY_CONTENT=$(cat "$PUBLIC_KEY")
echo -e "${YELLOW}$PUBLIC_KEY_CONTENT${NC}"

# Step 5: Test existing connection
step "Testing existing SSH connection..."
if ssh -o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=no "${JUMP_SERVER_USER}@${JUMP_SERVER_HOST}" "echo 'SUCCESS'" 2>/dev/null | grep -q "SUCCESS"; then
    success "SSH key authentication is already configured!"
    echo -e "\n${GREEN}You're all set! The MCP server should work.${NC}"
    exit 0
fi

# Step 6: Copy public key to jump server
step "Copying public key to jump server..."
echo "You will be prompted for the password for ${JUMP_SERVER_USER}@${JUMP_SERVER_HOST}"
echo -e "${YELLOW}This is a one-time setup.${NC}"

if command -v ssh-copy-id &> /dev/null; then
    ssh-copy-id -o StrictHostKeyChecking=no "${JUMP_SERVER_USER}@${JUMP_SERVER_HOST}"
else
    # Manual method if ssh-copy-id is not available
    cat "$PUBLIC_KEY" | ssh -o StrictHostKeyChecking=no "${JUMP_SERVER_USER}@${JUMP_SERVER_HOST}" \
        "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
fi

if [ $? -eq 0 ]; then
    success "Public key copied to jump server"
else
    error "Failed to copy key. You may need to manually add the key to the server."
    echo ""
    echo "Manual steps:"
    echo "1. SSH into the server: ssh ${JUMP_SERVER_USER}@${JUMP_SERVER_HOST}"
    echo "2. Run: mkdir -p ~/.ssh && chmod 700 ~/.ssh"
    echo "3. Add this key to ~/.ssh/authorized_keys:"
    echo "$PUBLIC_KEY_CONTENT"
    exit 1
fi

# Step 7: Verify passwordless connection
step "Verifying passwordless SSH connection..."
sleep 1
if ssh -o BatchMode=yes -o ConnectTimeout=10 "${JUMP_SERVER_USER}@${JUMP_SERVER_HOST}" "echo 'VERIFIED'" 2>/dev/null | grep -q "VERIFIED"; then
    success "Passwordless SSH authentication configured successfully!"
else
    warn "Verification inconclusive. Please test manually:"
    echo "  ssh ${JUMP_SERVER_USER}@${JUMP_SERVER_HOST}"
fi

# Step 8: Configure SSH alias
step "Configuring SSH alias..."
SSH_CONFIG="$SSH_DIR/config"

if ! grep -q "Host mcp-jump" "$SSH_CONFIG" 2>/dev/null; then
    cat >> "$SSH_CONFIG" << EOF

# Kubernetes MCP Jump Server
Host mcp-jump
    HostName ${JUMP_SERVER_HOST}
    User ${JUMP_SERVER_USER}
    IdentityFile ${PRIVATE_KEY}
    StrictHostKeyChecking no
EOF
    chmod 600 "$SSH_CONFIG"
    success "Added 'mcp-jump' alias to SSH config"
    echo "You can now connect using: ssh mcp-jump"
else
    echo "SSH alias 'mcp-jump' already configured"
fi

# Summary
echo -e "${GREEN}"
echo "========================================"
echo "  Setup Complete!"
echo "========================================"
echo "SSH Key: $PUBLIC_KEY"
echo "Jump Server: ${JUMP_SERVER_USER}@${JUMP_SERVER_HOST}"
echo "Alias: ssh mcp-jump"
echo ""
echo "Next steps:"
echo "1. Copy the MCP server JAR to the jump server:"
echo "   scp kubernetes-mcp-server-1.0.0.jar ${JUMP_SERVER_USER}@${JUMP_SERVER_HOST}:~/mcp/"
echo ""
echo "2. Test the MCP server connection:"
echo "   ssh mcp-jump java -jar ~/mcp/kubernetes-mcp-server-1.0.0.jar"
echo -e "${NC}"
