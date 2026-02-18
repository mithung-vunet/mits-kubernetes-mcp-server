#!/bin/bash
# Build script for Kubernetes MCP Server (Java)
set -e

echo "Building Kubernetes MCP Server (Java)..."
cd "$(dirname "$0")"

# Check Java version
if ! command -v java &> /dev/null; then
    echo "Error: Java not found. Install JDK 17 or higher."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "Error: Java 17 or higher required. Found: $JAVA_VERSION"
    exit 1
fi

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven not found. Install Maven 3.8+ or use ./mvnw"
    exit 1
fi

# Build
echo "Compiling..."
mvn clean package -DskipTests -q

echo ""
echo "Build complete!"
echo ""
echo "Output: target/kubernetes-mcp-server-1.0.0.jar"
echo ""
echo "Deploy to jump server:"
echo "  scp target/kubernetes-mcp-server-1.0.0.jar user@jump-server:~/"
echo ""
echo "Test locally:"
echo "  java -jar target/kubernetes-mcp-server-1.0.0.jar"
