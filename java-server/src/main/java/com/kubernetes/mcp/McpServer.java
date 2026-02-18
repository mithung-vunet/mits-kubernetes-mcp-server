package com.kubernetes.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.*;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class McpServer {
    private static final String SERVER_NAME = "kubernetes-mcp-server";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String PROTOCOL_VERSION = "2024-11-05";
    
    private final ObjectMapper mapper = new ObjectMapper();
    private final PrintWriter stdout;
    private CoreV1Api coreApi;
    private AppsV1Api appsApi;
    private BatchV1Api batchApi;
    private NetworkingV1Api networkingApi;
    private RbacAuthorizationV1Api rbacApi;
    private AutoscalingV2Api autoscalingApi;
    
    public McpServer() { this.stdout = new PrintWriter(System.out, true); }
    
    public static void main(String[] args) { new McpServer().run(); }
    
    public void run() {
        try {
            ApiClient client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);
            coreApi = new CoreV1Api();
            appsApi = new AppsV1Api();
            batchApi = new BatchV1Api();
            networkingApi = new NetworkingV1Api();
            rbacApi = new RbacAuthorizationV1Api();
            autoscalingApi = new AutoscalingV2Api();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    JsonNode request = mapper.readTree(line);
                    JsonNode response = handleRequest(request);
                    if (response != null) stdout.println(response.toString());
                } catch (Exception e) {
                    sendError(null, -32700, "Parse error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Fatal: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private JsonNode handleRequest(JsonNode request) {
        String method = request.path("method").asText();
        JsonNode id = request.get("id");
        JsonNode params = request.get("params");
        try {
            switch (method) {
                case "initialize": return handleInitialize(id);
                case "notifications/initialized": return null;
                case "tools/list": return handleToolsList(id);
                case "tools/call": return handleToolsCall(id, params);
                case "ping": return createResult(id, mapper.createObjectNode());
                default: return createError(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            return createError(id, -32603, "Error: " + e.getMessage());
        }
    }
    
    private JsonNode handleInitialize(JsonNode id) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode caps = mapper.createObjectNode();
        ObjectNode tools = mapper.createObjectNode();
        tools.put("listChanged", false);
        caps.set("tools", tools);
        result.set("capabilities", caps);
        ObjectNode info = mapper.createObjectNode();
        info.put("name", SERVER_NAME);
        info.put("version", SERVER_VERSION);
        result.set("serverInfo", info);
        return createResult(id, result);
    }
    
    private JsonNode handleToolsList(JsonNode id) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = mapper.createArrayNode();
        tools.add(tool("list_namespaces", "List all namespaces"));
        tools.add(tool("list_pods", "List pods", param("namespace", "Namespace (optional)", false), param("labelSelector", "Label selector", false)));
        tools.add(tool("get_pod_details", "Get pod details", param("namespace", "Namespace", true), param("name", "Pod name", true)));
        tools.add(tool("get_pod_logs", "Get pod logs", param("namespace", "Namespace", true), param("name", "Pod name", true), param("container", "Container", false), paramInt("tailLines", "Lines", false)));
        tools.add(tool("list_failing_pods", "List failing pods", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("list_deployments", "List deployments", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("get_deployment_details", "Get deployment details", param("namespace", "Namespace", true), param("name", "Name", true)));
        tools.add(tool("list_services", "List services", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("get_service_details", "Get service details", param("namespace", "Namespace", true), param("name", "Name", true)));
        tools.add(tool("list_nodes", "List cluster nodes"));
        tools.add(tool("get_node_details", "Get node details", param("name", "Node name", true)));
        tools.add(tool("list_events", "List events", param("namespace", "Namespace (optional)", false), paramInt("limit", "Limit", false)));
        tools.add(tool("get_resource_events", "Get resource events", param("namespace", "Namespace", true), param("kind", "Kind", true), param("name", "Name", true)));
        tools.add(tool("list_configmaps", "List ConfigMaps", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("list_secrets", "List Secrets", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("list_pvcs", "List PVCs", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("list_pvs", "List PVs"));
        tools.add(tool("list_ingresses", "List Ingresses", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("list_jobs", "List Jobs", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("list_cronjobs", "List CronJobs", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("list_statefulsets", "List StatefulSets", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("list_daemonsets", "List DaemonSets", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("list_hpas", "List HPAs", param("namespace", "Namespace (optional)", false)));
        tools.add(tool("get_cluster_info", "Get cluster info"));
        tools.add(tool("check_node_pressure", "Check node pressure"));
        result.set("tools", tools);
        return createResult(id, result);
    }
    
    private ObjectNode tool(String name, String desc, ObjectNode... params) {
        ObjectNode t = mapper.createObjectNode();
        t.put("name", name);
        t.put("description", desc);
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = mapper.createObjectNode();
        ArrayNode req = mapper.createArrayNode();
        for (ObjectNode p : params) {
            String pname = p.get("name").asText();
            props.set(pname, p.get("schema"));
            if (p.get("required").asBoolean()) req.add(pname);
        }
        schema.set("properties", props);
        if (req.size() > 0) schema.set("required", req);
        t.set("inputSchema", schema);
        return t;
    }
    
    private ObjectNode param(String name, String desc, boolean req) {
        ObjectNode p = mapper.createObjectNode();
        p.put("name", name);
        p.put("required", req);
        ObjectNode s = mapper.createObjectNode();
        s.put("type", "string");
        s.put("description", desc);
        p.set("schema", s);
        return p;
    }
    
    private ObjectNode paramInt(String name, String desc, boolean req) {
        ObjectNode p = mapper.createObjectNode();
        p.put("name", name);
        p.put("required", req);
        ObjectNode s = mapper.createObjectNode();
        s.put("type", "integer");
        s.put("description", desc);
        p.set("schema", s);
        return p;
    }
    
    private JsonNode handleToolsCall(JsonNode id, JsonNode params) {
        String name = params.path("name").asText();
        JsonNode args = params.get("arguments");
        try {
            Object result = executeTool(name, args);
            return toolResult(id, result);
        } catch (ApiException e) {
            return toolError(id, "K8s API error: " + e.getResponseBody());
        } catch (Exception e) {
            return toolError(id, "Error: " + e.getMessage());
        }
    }
    
    private Object executeTool(String name, JsonNode args) throws Exception {
        String ns = str(args, "namespace");
        String n = str(args, "name");
        switch (name) {
            case "list_namespaces": return listNamespaces();
            case "list_pods": return listPods(ns, str(args, "labelSelector"));
            case "get_pod_details": return getPodDetails(ns, n);
            case "get_pod_logs": return getPodLogs(ns, n, str(args, "container"), intArg(args, "tailLines"));
            case "list_failing_pods": return listFailingPods(ns);
            case "list_deployments": return listDeployments(ns);
            case "get_deployment_details": return getDeploymentDetails(ns, n);
            case "list_services": return listServices(ns);
            case "get_service_details": return getServiceDetails(ns, n);
            case "list_nodes": return listNodes();
            case "get_node_details": return getNodeDetails(n);
            case "list_events": return listEvents(ns, intArg(args, "limit"));
            case "get_resource_events": return getResourceEvents(ns, str(args, "kind"), n);
            case "list_configmaps": return listConfigMaps(ns);
            case "list_secrets": return listSecrets(ns);
            case "list_pvcs": return listPVCs(ns);
            case "list_pvs": return listPVs();
            case "list_ingresses": return listIngresses(ns);
            case "list_jobs": return listJobs(ns);
            case "list_cronjobs": return listCronJobs(ns);
            case "list_statefulsets": return listStatefulSets(ns);
            case "list_daemonsets": return listDaemonSets(ns);
            case "list_hpas": return listHPAs(ns);
            case "get_cluster_info": return getClusterInfo();
            case "check_node_pressure": return checkNodePressure();
            default: throw new IllegalArgumentException("Unknown: " + name);
        }
    }
    
    private String str(JsonNode a, String k) { return a != null && a.has(k) ? a.get(k).asText() : null; }
    private Integer intArg(JsonNode a, String k) { return a != null && a.has(k) ? a.get(k).asInt() : null; }
    
    // ===== Tool Implementations using fluent API =====
    
    private List<Map<String,Object>> listNamespaces() throws ApiException {
        V1NamespaceList list = coreApi.listNamespace().execute();
        return list.getItems().stream().map(ns -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", ns.getMetadata().getName());
            m.put("status", ns.getStatus().getPhase());
            m.put("age", age(ns.getMetadata().getCreationTimestamp()));
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listPods(String ns, String label) throws ApiException {
        V1PodList list;
        if (ns == null || ns.isEmpty()) {
            CoreV1Api.APIlistPodForAllNamespacesRequest req = coreApi.listPodForAllNamespaces();
            if (label != null) req = req.labelSelector(label);
            list = req.execute();
        } else {
            CoreV1Api.APIlistNamespacedPodRequest req = coreApi.listNamespacedPod(ns);
            if (label != null) req = req.labelSelector(label);
            list = req.execute();
        }
        return list.getItems().stream().map(p -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", p.getMetadata().getName());
            m.put("namespace", p.getMetadata().getNamespace());
            m.put("status", p.getStatus().getPhase());
            m.put("ready", ready(p));
            m.put("restarts", restarts(p));
            m.put("node", p.getSpec().getNodeName());
            m.put("age", age(p.getMetadata().getCreationTimestamp()));
            return m;
        }).collect(Collectors.toList());
    }
    
    private Map<String,Object> getPodDetails(String ns, String name) throws ApiException {
        V1Pod p = coreApi.readNamespacedPod(name, ns).execute();
        Map<String,Object> m = new HashMap<>();
        m.put("name", p.getMetadata().getName());
        m.put("namespace", p.getMetadata().getNamespace());
        m.put("status", p.getStatus().getPhase());
        m.put("node", p.getSpec().getNodeName());
        m.put("ip", p.getStatus().getPodIP());
        m.put("labels", p.getMetadata().getLabels());
        if (p.getSpec().getContainers() != null) {
            m.put("containers", p.getSpec().getContainers().stream()
                .map(c -> c.getName() + ":" + c.getImage()).collect(Collectors.toList()));
        }
        m.put("conditions", p.getStatus().getConditions());
        return m;
    }
    
    private String getPodLogs(String ns, String name, String container, Integer tail) throws ApiException {
        CoreV1Api.APIreadNamespacedPodLogRequest req = coreApi.readNamespacedPodLog(name, ns);
        if (container != null) req = req.container(container);
        req = req.tailLines(tail != null ? tail : 100);
        return req.execute();
    }
    
    private List<Map<String,Object>> listFailingPods(String ns) throws ApiException {
        V1PodList list;
        if (ns == null || ns.isEmpty()) {
            list = coreApi.listPodForAllNamespaces().execute();
        } else {
            list = coreApi.listNamespacedPod(ns).execute();
        }
        return list.getItems().stream()
            .filter(p -> !"Running".equals(p.getStatus().getPhase()) && !"Succeeded".equals(p.getStatus().getPhase()))
            .map(p -> {
                Map<String,Object> m = new HashMap<>();
                m.put("name", p.getMetadata().getName());
                m.put("namespace", p.getMetadata().getNamespace());
                m.put("status", p.getStatus().getPhase());
                m.put("reason", p.getStatus().getReason());
                return m;
            }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listDeployments(String ns) throws ApiException {
        V1DeploymentList list;
        if (ns == null || ns.isEmpty()) {
            list = appsApi.listDeploymentForAllNamespaces().execute();
        } else {
            list = appsApi.listNamespacedDeployment(ns).execute();
        }
        return list.getItems().stream().map(d -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", d.getMetadata().getName());
            m.put("namespace", d.getMetadata().getNamespace());
            Integer ready = d.getStatus().getReadyReplicas();
            m.put("ready", (ready != null ? ready : 0) + "/" + d.getSpec().getReplicas());
            m.put("age", age(d.getMetadata().getCreationTimestamp()));
            return m;
        }).collect(Collectors.toList());
    }
    
    private Map<String,Object> getDeploymentDetails(String ns, String name) throws ApiException {
        V1Deployment d = appsApi.readNamespacedDeployment(name, ns).execute();
        Map<String,Object> m = new HashMap<>();
        m.put("name", d.getMetadata().getName());
        m.put("replicas", d.getSpec().getReplicas());
        m.put("ready", d.getStatus().getReadyReplicas());
        m.put("strategy", d.getSpec().getStrategy() != null ? d.getSpec().getStrategy().getType() : null);
        m.put("conditions", d.getStatus().getConditions());
        return m;
    }
    
    private List<Map<String,Object>> listServices(String ns) throws ApiException {
        V1ServiceList list;
        if (ns == null || ns.isEmpty()) {
            list = coreApi.listServiceForAllNamespaces().execute();
        } else {
            list = coreApi.listNamespacedService(ns).execute();
        }
        return list.getItems().stream().map(s -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", s.getMetadata().getName());
            m.put("namespace", s.getMetadata().getNamespace());
            m.put("type", s.getSpec().getType());
            m.put("clusterIP", s.getSpec().getClusterIP());
            m.put("ports", s.getSpec().getPorts());
            return m;
        }).collect(Collectors.toList());
    }
    
    private Map<String,Object> getServiceDetails(String ns, String name) throws ApiException {
        V1Service s = coreApi.readNamespacedService(name, ns).execute();
        Map<String,Object> m = new HashMap<>();
        m.put("name", s.getMetadata().getName());
        m.put("type", s.getSpec().getType());
        m.put("clusterIP", s.getSpec().getClusterIP());
        m.put("ports", s.getSpec().getPorts());
        m.put("selector", s.getSpec().getSelector());
        return m;
    }
    
    private List<Map<String,Object>> listNodes() throws ApiException {
        V1NodeList list = coreApi.listNode().execute();
        return list.getItems().stream().map(n -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", n.getMetadata().getName());
            m.put("status", nodeStatus(n));
            m.put("version", n.getStatus().getNodeInfo().getKubeletVersion());
            m.put("age", age(n.getMetadata().getCreationTimestamp()));
            return m;
        }).collect(Collectors.toList());
    }
    
    private Map<String,Object> getNodeDetails(String name) throws ApiException {
        V1Node n = coreApi.readNode(name).execute();
        Map<String,Object> m = new HashMap<>();
        m.put("name", n.getMetadata().getName());
        m.put("labels", n.getMetadata().getLabels());
        m.put("conditions", n.getStatus().getConditions());
        m.put("capacity", n.getStatus().getCapacity());
        m.put("allocatable", n.getStatus().getAllocatable());
        m.put("nodeInfo", n.getStatus().getNodeInfo());
        return m;
    }
    
    private List<Map<String,Object>> listEvents(String ns, Integer limit) throws ApiException {
        CoreV1EventList list;
        if (ns == null || ns.isEmpty()) {
            CoreV1Api.APIlistEventForAllNamespacesRequest req = coreApi.listEventForAllNamespaces();
            if (limit != null) req = req.limit(limit);
            list = req.execute();
        } else {
            CoreV1Api.APIlistNamespacedEventRequest req = coreApi.listNamespacedEvent(ns);
            if (limit != null) req = req.limit(limit);
            list = req.execute();
        }
        return list.getItems().stream()
            .sorted((a,b) -> {
                OffsetDateTime ta = a.getLastTimestamp();
                OffsetDateTime tb = b.getLastTimestamp();
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            })
            .map(e -> {
                Map<String,Object> m = new HashMap<>();
                m.put("type", e.getType());
                m.put("reason", e.getReason());
                m.put("message", e.getMessage());
                m.put("object", e.getInvolvedObject().getKind() + "/" + e.getInvolvedObject().getName());
                m.put("count", e.getCount());
                return m;
            }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> getResourceEvents(String ns, String kind, String name) throws ApiException {
        String fs = "involvedObject.kind=" + kind + ",involvedObject.name=" + name;
        CoreV1EventList list = coreApi.listNamespacedEvent(ns).fieldSelector(fs).execute();
        return list.getItems().stream().map(e -> {
            Map<String,Object> m = new HashMap<>();
            m.put("type", e.getType());
            m.put("reason", e.getReason());
            m.put("message", e.getMessage());
            m.put("count", e.getCount());
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listConfigMaps(String ns) throws ApiException {
        V1ConfigMapList list;
        if (ns == null || ns.isEmpty()) {
            list = coreApi.listConfigMapForAllNamespaces().execute();
        } else {
            list = coreApi.listNamespacedConfigMap(ns).execute();
        }
        return list.getItems().stream().map(cm -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", cm.getMetadata().getName());
            m.put("namespace", cm.getMetadata().getNamespace());
            m.put("keys", cm.getData() != null ? cm.getData().keySet() : Collections.emptySet());
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listSecrets(String ns) throws ApiException {
        V1SecretList list;
        if (ns == null || ns.isEmpty()) {
            list = coreApi.listSecretForAllNamespaces().execute();
        } else {
            list = coreApi.listNamespacedSecret(ns).execute();
        }
        return list.getItems().stream().map(s -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", s.getMetadata().getName());
            m.put("namespace", s.getMetadata().getNamespace());
            m.put("type", s.getType());
            m.put("keys", s.getData() != null ? s.getData().keySet() : Collections.emptySet());
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listPVCs(String ns) throws ApiException {
        V1PersistentVolumeClaimList list;
        if (ns == null || ns.isEmpty()) {
            list = coreApi.listPersistentVolumeClaimForAllNamespaces().execute();
        } else {
            list = coreApi.listNamespacedPersistentVolumeClaim(ns).execute();
        }
        return list.getItems().stream().map(pvc -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", pvc.getMetadata().getName());
            m.put("namespace", pvc.getMetadata().getNamespace());
            m.put("status", pvc.getStatus().getPhase());
            m.put("capacity", pvc.getStatus().getCapacity());
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listPVs() throws ApiException {
        V1PersistentVolumeList list = coreApi.listPersistentVolume().execute();
        return list.getItems().stream().map(pv -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", pv.getMetadata().getName());
            m.put("capacity", pv.getSpec().getCapacity());
            m.put("status", pv.getStatus().getPhase());
            m.put("storageClass", pv.getSpec().getStorageClassName());
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listIngresses(String ns) throws ApiException {
        V1IngressList list;
        if (ns == null || ns.isEmpty()) {
            list = networkingApi.listIngressForAllNamespaces().execute();
        } else {
            list = networkingApi.listNamespacedIngress(ns).execute();
        }
        return list.getItems().stream().map(ing -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", ing.getMetadata().getName());
            m.put("namespace", ing.getMetadata().getNamespace());
            m.put("hosts", ing.getSpec().getRules() != null ? 
                ing.getSpec().getRules().stream().map(r -> r.getHost()).collect(Collectors.toList()) 
                : Collections.emptyList());
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listJobs(String ns) throws ApiException {
        V1JobList list;
        if (ns == null || ns.isEmpty()) {
            list = batchApi.listJobForAllNamespaces().execute();
        } else {
            list = batchApi.listNamespacedJob(ns).execute();
        }
        return list.getItems().stream().map(j -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", j.getMetadata().getName());
            m.put("namespace", j.getMetadata().getNamespace());
            m.put("succeeded", j.getStatus().getSucceeded());
            m.put("failed", j.getStatus().getFailed());
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listCronJobs(String ns) throws ApiException {
        V1CronJobList list;
        if (ns == null || ns.isEmpty()) {
            list = batchApi.listCronJobForAllNamespaces().execute();
        } else {
            list = batchApi.listNamespacedCronJob(ns).execute();
        }
        return list.getItems().stream().map(cj -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", cj.getMetadata().getName());
            m.put("namespace", cj.getMetadata().getNamespace());
            m.put("schedule", cj.getSpec().getSchedule());
            m.put("suspend", cj.getSpec().getSuspend());
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listStatefulSets(String ns) throws ApiException {
        V1StatefulSetList list;
        if (ns == null || ns.isEmpty()) {
            list = appsApi.listStatefulSetForAllNamespaces().execute();
        } else {
            list = appsApi.listNamespacedStatefulSet(ns).execute();
        }
        return list.getItems().stream().map(sts -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", sts.getMetadata().getName());
            m.put("namespace", sts.getMetadata().getNamespace());
            Integer ready = sts.getStatus().getReadyReplicas();
            m.put("ready", (ready != null ? ready : 0) + "/" + sts.getSpec().getReplicas());
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listDaemonSets(String ns) throws ApiException {
        V1DaemonSetList list;
        if (ns == null || ns.isEmpty()) {
            list = appsApi.listDaemonSetForAllNamespaces().execute();
        } else {
            list = appsApi.listNamespacedDaemonSet(ns).execute();
        }
        return list.getItems().stream().map(ds -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", ds.getMetadata().getName());
            m.put("namespace", ds.getMetadata().getNamespace());
            m.put("desired", ds.getStatus().getDesiredNumberScheduled());
            m.put("ready", ds.getStatus().getNumberReady());
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String,Object>> listHPAs(String ns) throws ApiException {
        V2HorizontalPodAutoscalerList list;
        if (ns == null || ns.isEmpty()) {
            list = autoscalingApi.listHorizontalPodAutoscalerForAllNamespaces().execute();
        } else {
            list = autoscalingApi.listNamespacedHorizontalPodAutoscaler(ns).execute();
        }
        return list.getItems().stream().map(hpa -> {
            Map<String,Object> m = new HashMap<>();
            m.put("name", hpa.getMetadata().getName());
            m.put("namespace", hpa.getMetadata().getNamespace());
            m.put("minReplicas", hpa.getSpec().getMinReplicas());
            m.put("maxReplicas", hpa.getSpec().getMaxReplicas());
            m.put("currentReplicas", hpa.getStatus().getCurrentReplicas());
            return m;
        }).collect(Collectors.toList());
    }
    
    private Map<String,Object> getClusterInfo() throws ApiException {
        V1NodeList nodes = coreApi.listNode().execute();
        V1NamespaceList nss = coreApi.listNamespace().execute();
        Map<String,Object> m = new HashMap<>();
        m.put("nodeCount", nodes.getItems().size());
        m.put("namespaceCount", nss.getItems().size());
        if (!nodes.getItems().isEmpty()) {
            m.put("kubeVersion", nodes.getItems().get(0).getStatus().getNodeInfo().getKubeletVersion());
        }
        return m;
    }
    
    private List<Map<String,Object>> checkNodePressure() throws ApiException {
        List<Map<String,Object>> result = new ArrayList<>();
        V1NodeList nodes = coreApi.listNode().execute();
        for (V1Node n : nodes.getItems()) {
            List<String> pressures = new ArrayList<>();
            if (n.getStatus().getConditions() != null) {
                for (V1NodeCondition c : n.getStatus().getConditions()) {
                    if ("True".equals(c.getStatus()) && (c.getType().contains("Pressure"))) {
                        pressures.add(c.getType());
                    }
                }
            }
            if (!pressures.isEmpty()) {
                Map<String,Object> m = new HashMap<>();
                m.put("node", n.getMetadata().getName());
                m.put("pressures", pressures);
                result.add(m);
            }
        }
        return result;
    }
    
    // ===== Helper Methods =====
    
    private String ready(V1Pod p) {
        if (p.getStatus().getContainerStatuses() == null) return "0/0";
        long r = p.getStatus().getContainerStatuses().stream().filter(c -> c.getReady()).count();
        return r + "/" + p.getStatus().getContainerStatuses().size();
    }
    
    private int restarts(V1Pod p) {
        if (p.getStatus().getContainerStatuses() == null) return 0;
        return p.getStatus().getContainerStatuses().stream().mapToInt(c -> c.getRestartCount()).sum();
    }
    
    private String nodeStatus(V1Node n) {
        if (n.getStatus().getConditions() == null) return "Unknown";
        for (V1NodeCondition c : n.getStatus().getConditions()) {
            if ("Ready".equals(c.getType())) return "True".equals(c.getStatus()) ? "Ready" : "NotReady";
        }
        return "Unknown";
    }
    
    private String age(OffsetDateTime t) {
        if (t == null) return "?";
        long s = java.time.Duration.between(t, OffsetDateTime.now()).getSeconds();
        if (s < 60) return s + "s";
        if (s < 3600) return (s/60) + "m";
        if (s < 86400) return (s/3600) + "h";
        return (s/86400) + "d";
    }
    
    // ===== JSON-RPC Helpers =====
    
    private void sendError(JsonNode id, int code, String msg) {
        ObjectNode r = mapper.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        ObjectNode e = mapper.createObjectNode();
        e.put("code", code);
        e.put("message", msg);
        r.set("error", e);
        stdout.println(r.toString());
    }
    
    private JsonNode createResult(JsonNode id, JsonNode result) {
        ObjectNode r = mapper.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        r.set("result", result);
        return r;
    }
    
    private JsonNode createError(JsonNode id, int code, String msg) {
        ObjectNode r = mapper.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        ObjectNode e = mapper.createObjectNode();
        e.put("code", code);
        e.put("message", msg);
        r.set("error", e);
        return r;
    }
    
    private JsonNode toolResult(JsonNode id, Object result) {
        ObjectNode r = mapper.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        ObjectNode res = mapper.createObjectNode();
        ArrayNode content = mapper.createArrayNode();
        ObjectNode txt = mapper.createObjectNode();
        txt.put("type", "text");
        try { txt.put("text", mapper.writeValueAsString(result)); } 
        catch (Exception e) { txt.put("text", result.toString()); }
        content.add(txt);
        res.set("content", content);
        r.set("result", res);
        return r;
    }
    
    private JsonNode toolError(JsonNode id, String msg) {
        ObjectNode r = mapper.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        ObjectNode res = mapper.createObjectNode();
        ArrayNode content = mapper.createArrayNode();
        ObjectNode txt = mapper.createObjectNode();
        txt.put("type", "text");
        txt.put("text", msg);
        content.add(txt);
        res.set("content", content);
        res.put("isError", true);
        r.set("result", res);
        return r;
    }
}
