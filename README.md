# Kubernetes MCP Server

A Model Context Protocol (MCP) server for querying Kubernetes clusters. Enables AI assistants (VS Code Copilot, Claude Desktop) to retrieve cluster information via natural language.

## Features

- **25 tools** for Kubernetes troubleshooting
- Pods, Deployments, Services, Nodes, Events, ConfigMaps, Secrets, PVCs, Jobs, CronJobs, StatefulSets, DaemonSets, HPAs
- Failing pod detection, node pressure checks, resource events
- Single JAR deployment (no dependencies needed at runtime)

## Quick Start

### Step 1: Set Up Passwordless SSH Access

Configure passwordless SSH authentication to the jump server (one-time setup).

**Windows (PowerShell):**
```powershell
# Option A: Double-click scripts/setup-ssh.bat
# Option B: Run in PowerShell
.\scripts\setup-ssh.ps1 -Server "user@jumpserver"
```

**Linux/Mac:**
```bash
chmod +x scripts/setup-ssh.sh
./scripts/setup-ssh.sh user@jumpserver
```

**Manual setup (if preferred):**
```bash
# Generate SSH key (skip if you already have one)
ssh-keygen -t ed25519 -C "mcp-server"

# Copy public key to jump server (enter password once)
cat ~/.ssh/id_ed25519.pub | ssh user@jumpserver "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"

# Verify passwordless access works
ssh user@jumpserver "echo 'SSH OK'"
```

### Step 2: Install Prerequisites on Jump Server

SSH into the jump server and install Java 21:

```bash
ssh user@jumpserver

# Ubuntu/Debian
sudo apt update && sudo apt install -y openjdk-21-jre-headless

# RHEL/CentOS
sudo yum install -y java-21-openjdk

# Verify installation
java -version
```

Ensure kubectl is configured with cluster access:
```bash
kubectl get nodes  # Should show cluster nodes
```

### Step 3: Deploy MCP Server JAR

Copy the MCP server JAR file to the jump server:

```bash
# Create target directory
ssh user@jumpserver "mkdir -p ~/mcp"

# Copy JAR file
scp java-server/kubernetes-mcp-server-1.0.0.jar user@jumpserver:~/mcp/

# Verify
ssh user@jumpserver "ls -la ~/mcp/"
```

### Step 4: Configure VS Code

Copy the template and customize for your environment:

```bash
cp .vscode/mcp.json.example .vscode/mcp.json
```

Edit `.vscode/mcp.json` with your server details:

```json
{
  "servers": {
    "kubernetes": {
      "command": "ssh",
      "args": ["user@jumpserver", "java", "-jar", "~/mcp/kubernetes-mcp-server-1.0.0.jar"]
    }
  }
}
```

**Multiple clusters example:**
```json
{
  "servers": {
    "prod-cluster": {
      "command": "ssh",
      "args": ["admin@prod-jump.example.com", "java", "-jar", "~/mcp/kubernetes-mcp-server-1.0.0.jar"]
    },
    "dev-cluster": {
      "command": "ssh",
      "args": ["dev@dev-jump.example.com", "java", "-jar", "~/mcp/kubernetes-mcp-server-1.0.0.jar"]
    }
  }
}
```

### Step 5: Verify Connection

1. Open VS Code and go to **Copilot Chat**
2. Click the **Tools** icon (wrench) and verify the MCP server shows as connected
3. Ask a question like: *"List all namespaces in the cluster"*

### Troubleshooting

| Issue | Solution |
|-------|----------|
| SSH asks for password | Re-run Step 1 to configure passwordless SSH |
| "java: command not found" | Install Java on jump server (Step 2) |
| "kubectl: command not found" | Install kubectl on jump server |
| "error: no configuration" | Copy kubeconfig: `sudo cp /root/.kube/config ~/.kube/config && sudo chown $USER:$USER ~/.kube/config` |
| MCP shows disconnected | Check VS Code Output > MCP for error messages |

### Example Usage

Ask Copilot questions like:
- "List all failing pods in the cluster"
- "Show events for namespace production"  
- "Get logs from pod nginx-abc in default namespace"
- "Check if any nodes have memory pressure"
- "What's the CPU and memory usage of the cluster?"

## Requirements

- **Jump Server**: Java 11+ and kubectl configured with cluster access
- **Local**: VS Code with GitHub Copilot

## Building from Source

```bash
cd java-server
mvn clean package -DskipTests
# Output: target/kubernetes-mcp-server-1.0.0.jar
```

## Available Tools

| Tool | Description |
|------|-------------|
| `list_namespaces` | List all namespaces |
| `list_pods` | List pods (optional namespace, label selector) |
| `get_pod_details` | Get detailed pod info |
| `get_pod_logs` | Get pod logs |
| `list_failing_pods` | Find non-running pods |
| `list_deployments` | List deployments |
| `get_deployment_details` | Get deployment info |
| `list_services` | List services |
| `get_service_details` | Get service info |
| `list_nodes` | List cluster nodes |
| `get_node_details` | Get node info |
| `list_events` | List events |
| `get_resource_events` | Get events for specific resource |
| `list_configmaps` | List ConfigMaps |
| `list_secrets` | List Secrets (metadata only) |
| `list_pvcs` | List PersistentVolumeClaims |
| `list_pvs` | List PersistentVolumes |
| `list_ingresses` | List Ingresses |
| `list_jobs` | List Jobs |
| `list_cronjobs` | List CronJobs |
| `list_statefulsets` | List StatefulSets |
| `list_daemonsets` | List DaemonSets |
| `list_hpas` | List HorizontalPodAutoscalers |
| `get_cluster_info` | Get cluster summary |
| `check_node_pressure` | Check nodes for pressure conditions |

## How It Works

MCP uses stdio communication:

```
VS Code ──stdin──▶ SSH ──stdin──▶ java -jar ──▶ Kubernetes API
VS Code ◀─stdout── SSH ◀─stdout── java -jar ◀── Kubernetes API
```

The server reads kubeconfig from the machine where it runs (jump server).

## License

MIT
