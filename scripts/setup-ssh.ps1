# SSH Authentication Setup Script for Kubernetes MCP Server
# Run this script on any new machine to configure passwordless SSH to the jump server
#
# Usage:
#   .\setup-ssh.ps1 -Server "user@hostname"
#   .\setup-ssh.ps1 -JumpServerUser "ubuntu" -JumpServerHost "192.168.1.100"
#
# NOTE: This script MUST be run interactively (not from VS Code terminal integration)
#       as it requires password input during the key copy step.

param(
    [string]$Server = "",
    [string]$JumpServerUser = "",
    [string]$JumpServerHost = "",
    [string]$KeyType = "ed25519",
    [string]$KeyComment = "kubernetes-mcp-client"
)

# Parse Server parameter (user@host format)
if ($Server -and $Server -match "(.+)@(.+)") {
    $JumpServerUser = $Matches[1]
    $JumpServerHost = $Matches[2]
}

# Prompt if not provided
if (-not $JumpServerUser) {
    $JumpServerUser = Read-Host "Enter jump server username"
}
if (-not $JumpServerHost) {
    $JumpServerHost = Read-Host "Enter jump server hostname/IP"
}

if (-not $JumpServerUser -or -not $JumpServerHost) {
    Write-Host "Error: Jump server user and host are required" -ForegroundColor Red
    Write-Host "Usage: .\setup-ssh.ps1 -Server 'user@hostname'"
    exit 1
}

$ErrorActionPreference = "Continue"

function Write-Step {
    param([string]$Message)
    Write-Host "`n[$((Get-Date).ToString('HH:mm:ss'))] $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-Err {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Banner
Write-Host @"
========================================
  Kubernetes MCP SSH Setup Script
  Jump Server: $JumpServerUser@$JumpServerHost
========================================
"@ -ForegroundColor Magenta

# Step 1: Check if SSH client is available
Write-Step "Checking SSH client availability..."
$sshPath = Get-Command ssh -ErrorAction SilentlyContinue
if (-not $sshPath) {
    Write-Err "SSH client not found. Please install OpenSSH Client."
    Write-Host "Run: Add-WindowsCapability -Online -Name OpenSSH.Client*"
    exit 1
}
Write-Success "SSH client found at $($sshPath.Source)"

# Step 2: Check/Create SSH directory
Write-Step "Checking SSH directory..."
$sshDir = "$env:USERPROFILE\.ssh"
if (-not (Test-Path $sshDir)) {
    New-Item -ItemType Directory -Path $sshDir -Force | Out-Null
    Write-Success "Created SSH directory: $sshDir"
} else {
    Write-Success "SSH directory exists: $sshDir"
}

# Step 3: Check/Generate SSH key
Write-Step "Checking SSH key..."
$privateKeyPath = "$sshDir\id_$KeyType"
$publicKeyPath = "$privateKeyPath.pub"

if (Test-Path $publicKeyPath) {
    Write-Success "SSH key already exists: $publicKeyPath"
    $generateNew = Read-Host "Do you want to generate a new key? (y/N)"
    if ($generateNew -ne "y" -and $generateNew -ne "Y") {
        Write-Host "Using existing key."
    } else {
        Write-Warning "Backing up existing key..."
        $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
        Move-Item $privateKeyPath "$privateKeyPath.backup_$timestamp" -ErrorAction SilentlyContinue
        Move-Item $publicKeyPath "$publicKeyPath.backup_$timestamp" -ErrorAction SilentlyContinue
        
        Write-Host "Generating new SSH key..."
        ssh-keygen -t $KeyType -C $KeyComment -f $privateKeyPath -N '""'
        Write-Success "New SSH key generated"
    }
} else {
    Write-Host "Generating SSH key ($KeyType)..."
    ssh-keygen -t $KeyType -C $KeyComment -f $privateKeyPath -N '""'
    Write-Success "SSH key generated: $publicKeyPath"
}

# Step 4: Display the public key
Write-Step "Your public key:"
$publicKey = Get-Content $publicKeyPath -Raw
$publicKey = $publicKey.Trim()
Write-Host $publicKey -ForegroundColor Yellow

# Step 5: Test existing connection
Write-Step "Testing existing SSH connection..."
$testResult = & ssh -o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=accept-new "$JumpServerUser@$JumpServerHost" "echo SUCCESS" 2>&1 | Out-String
if ($testResult -match "SUCCESS") {
    Write-Success "SSH key authentication is already configured!"
    Write-Host "`nYou're all set! The MCP server should work." -ForegroundColor Green
    exit 0
}

Write-Host "Passwordless auth not yet configured. Setting it up now..." -ForegroundColor Yellow

# Step 6: Copy public key to jump server
Write-Step "Copying public key to jump server..."
Write-Host ""
Write-Host "============================================" -ForegroundColor Magenta
Write-Host "  ENTER YOUR PASSWORD WHEN PROMPTED BELOW  " -ForegroundColor Magenta
Write-Host "  (This is a one-time setup)               " -ForegroundColor Magenta
Write-Host "============================================" -ForegroundColor Magenta
Write-Host ""

# Create a temporary script file to send to the server
$remoteCommand = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"

# Use Get-Content and pipe to SSH - this allows interactive password entry
Get-Content $publicKeyPath | & ssh -o StrictHostKeyChecking=accept-new "$JumpServerUser@$JumpServerHost" $remoteCommand

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Success "Public key copied to jump server"
} else {
    Write-Host ""
    Write-Warning "Key copy may have failed. Verifying connection..."
}

# Step 7: Verify passwordless connection
Write-Step "Verifying passwordless SSH connection..."
Start-Sleep -Seconds 2
$verifyResult = & ssh -o BatchMode=yes -o ConnectTimeout=10 "$JumpServerUser@$JumpServerHost" "echo VERIFIED" 2>&1 | Out-String
if ($verifyResult -match "VERIFIED") {
    Write-Success "Passwordless SSH authentication configured successfully!"
} else {
    Write-Warning "Verification failed. Please test manually:"
    Write-Host "  ssh $JumpServerUser@$JumpServerHost"
    Write-Host ""
    Write-Host "If it still asks for password, run this command manually:" -ForegroundColor Yellow
    Write-Host "  Get-Content `"$publicKeyPath`" | ssh $JumpServerUser@$JumpServerHost `"mkdir -p ~/.ssh; cat >> ~/.ssh/authorized_keys`""
    exit 1
}

# Step 8: Configure SSH for convenience (optional)
Write-Step "Configuring SSH alias..."
$sshConfigPath = "$sshDir\config"
$aliasConfig = @"

# Kubernetes MCP Jump Server
Host mcp-jump
    HostName $JumpServerHost
    User $JumpServerUser
    IdentityFile $privateKeyPath
    StrictHostKeyChecking no
"@

$configExists = Test-Path $sshConfigPath
$aliasExists = $false
if ($configExists) {
    $existingConfig = Get-Content $sshConfigPath -Raw
    $aliasExists = $existingConfig -match "Host mcp-jump"
}

if (-not $aliasExists) {
    Add-Content -Path $sshConfigPath -Value $aliasConfig
    Write-Success "Added 'mcp-jump' alias to SSH config"
    Write-Host "You can now connect using: ssh mcp-jump"
} else {
    Write-Host "SSH alias 'mcp-jump' already configured"
}

# Summary
Write-Host @"

========================================
  Setup Complete!
========================================
SSH Key: $publicKeyPath
Jump Server: $JumpServerUser@$JumpServerHost
Alias: ssh mcp-jump

Next steps:
1. Copy the MCP server JAR to the jump server:
   scp kubernetes-mcp-server-1.0.0.jar $JumpServerUser@${JumpServerHost}:~/mcp/

2. Test the MCP server connection:
   ssh mcp-jump java -jar ~/mcp/kubernetes-mcp-server-1.0.0.jar

"@ -ForegroundColor Green
