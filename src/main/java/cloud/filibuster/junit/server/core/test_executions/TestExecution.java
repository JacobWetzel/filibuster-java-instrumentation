package cloud.filibuster.junit.server.core.test_executions;

import cloud.filibuster.dei.DistributedExecutionIndex;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

@SuppressWarnings("Varifier")
public abstract class TestExecution {
    private static final Logger logger = Logger.getLogger(TestExecution.class.getName());

    // Legacy value used to number the RPCs for fault injection.
    // Superseded by DistributedExecutionIndex, but kept in for compatibility and debugging.
    int generatedId = 0;

    // What RPCs were executed?
    HashMap<DistributedExecutionIndex, JSONObject> executedRPCs = new HashMap<>();

    // What RPCs were executed (without their arguments, which may be nondeterministic across executions)?
    HashMap<DistributedExecutionIndex, JSONObject> nondeterministicExecutedRPCs = new HashMap<>();

    // What faults should be injected in this execution?
    HashMap<DistributedExecutionIndex, JSONObject> faultsToInject = new HashMap<>();

    // What RPCs failed?
    HashMap<DistributedExecutionIndex, JSONObject> failedRPCs = new HashMap<>();

    HashMap<String, Boolean> firstRequestSeenByService = new HashMap<>();

    public boolean hasSeenFirstRequestFromService(String serviceName) {
        return firstRequestSeenByService.containsKey(serviceName);
    }

    public void registerFirstRequestFromService(String serviceName) {
        firstRequestSeenByService.put(serviceName, true);
    }

    public void printRPCs() {
        StringBuilder logMessage = new StringBuilder("\n");

        logMessage.append("[FILIBUSTER-CORE]: RPCs executed and interposed by Filibuster");
        logMessage.append("\n").append("\n");

        for (DistributedExecutionIndex name: executedRPCs.keySet()) {
            String key = name.toString();
            JSONObject value = executedRPCs.get(name);
            if (key != null && value != null) {
                logMessage.append(key).append(" => ").append(value.toString(4)).append("\n");
            }
        }

        if (!faultsToInject.isEmpty()) {
            logMessage.append("[FILIBUSTER-CORE]: Faults injected by Filibuster");
            logMessage.append("\n").append("\n");

            for (DistributedExecutionIndex name: faultsToInject.keySet()) {
                String key = name.toString();
                JSONObject value = faultsToInject.get(name);

                // getOrDefault needed because when application is nondeterministic the lookup for the request will fail because of a lack of DEI matches.
                JSONObject request = executedRPCs.getOrDefault(name, new JSONObject().put("error", "no request information found"));

                logMessage.append(key).append(" => ").append(value.toString(4)).append(" => ").append(request.toString(4)).append("\n");
            }
        } else {
            logMessage.append("[FILIBUSTER-CORE]: No faults injected by Filibuster.");
            logMessage.append("\n").append("\n");
        }

        logger.info(logMessage.toString());
    }

    public boolean hasSeenRpcUnderSameOrDifferentDistributedExecutionIndex(JSONObject payload) {
        JSONObject payloadCacheCleaned = cleanPayloadForCacheComparison(payload);

        for (Map.Entry<DistributedExecutionIndex, JSONObject> executedRPC : executedRPCs.entrySet()) {
            JSONObject seenPayload = executedRPC.getValue();
            JSONObject seenPayloadCacheCleaned = cleanPayloadForCacheComparison(seenPayload);

            if (seenPayloadCacheCleaned.similar(payloadCacheCleaned)) {
                return true;
            }
        }

        return false;
    }

    public void addDistributedExecutionIndexWithRequestPayload(DistributedExecutionIndex distributedExecutionIndex, JSONObject payload) {
        // Add to the list of executed RPCs.
        JSONObject payloadWithoutInstrumentationType = cleanPayloadOfInstrumentationType(payload);
        executedRPCs.put(distributedExecutionIndex, payloadWithoutInstrumentationType);

        // Add to the list of nondeterministic executed RPCs.
        JSONObject deterministicPayload = cleanPayloadOfArguments(payload);
        nondeterministicExecutedRPCs.put(distributedExecutionIndex, deterministicPayload);
    }

    public void addDistributedExecutionIndexWithResponsePayload(DistributedExecutionIndex distributedExecutionIndex, JSONObject payload) {
        if (payload.has("exception")) {
            failedRPCs.put(distributedExecutionIndex, payload);
        }
    }

    public int incrementGeneratedId() {
        // Increment the generated_id; not used for anything anymore and merely here for debugging and because callers require it.
        generatedId++;

        return generatedId;
    }

    public boolean shouldFault(DistributedExecutionIndex distributedExecutionIndex) {
        if (this.faultsToInject.containsKey(distributedExecutionIndex)) {
            return true;
        } else {
            return false;
        }
    }

    public JSONObject getFault(DistributedExecutionIndex distributedExecutionIndex) {
        return this.faultsToInject.get(distributedExecutionIndex);
    }

    public boolean wasFaultInjected() {
        return !this.faultsToInject.isEmpty();
    }

    public boolean wasFaultInjectedOnRequest(String serializedRequest) {
        for (Map.Entry<DistributedExecutionIndex, JSONObject> entry : executedRPCs.entrySet()) {
            JSONObject executedRPCObject = entry.getValue();

            if (executedRPCObject.getJSONObject("args").getString("toString").equals(serializedRequest)) {
                DistributedExecutionIndex distributedExecutionIndex = entry.getKey();

                if (faultsToInject.containsKey(distributedExecutionIndex)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean wasFaultInjectedOnService(String serviceName) {
        return wasFaultInjectedMatcher("module", serviceName);
    }

    // Recombination of RPC is artifact of HTTP API.
    public boolean wasFaultInjectedOnMethod(String serviceName, String methodName) {
        return wasFaultInjectedMatcher("method", serviceName + "/" + methodName);
    }

    public boolean wasFaultInjectedOnMethodWhereRequestContains(String serviceName, String methodName, String contains) {
        return wasFaultInjectedMatcher("method", serviceName + "/" + methodName, contains);
    }

    @SuppressWarnings("Varifier")
    public boolean matchesAbstractTestExecution(Object o) {
        if (!(o instanceof TestExecution)) {
            return false;
        }

        TestExecution te = (TestExecution) o;

        // Are the key sets equivalent?
        if (!this.faultsToInject.keySet().equals(te.faultsToInject.keySet())) {
            return false;
        }

        // Are the JSON objects similar for each key?
        return this.faultsToInject.entrySet().stream().allMatch(e -> e.getValue().similar(te.faultsToInject.get(e.getKey())));
    }

    @Override
    @SuppressWarnings("Varifier")
    public boolean equals(Object o) {
        if (!(o instanceof TestExecution)) {
            return false;
        }

        TestExecution te = (TestExecution) o;

        // Are the key sets equivalent?
        if (!this.executedRPCs.keySet().equals(te.executedRPCs.keySet())) {
            return false;
        }

        // Are the JSON objects similar for each key?
        boolean equalRPCsMap = this.executedRPCs.entrySet().stream().allMatch(e -> e.getValue().similar(te.executedRPCs.get(e.getKey())));

        // Are the key sets equivalent?
        if (!this.faultsToInject.keySet().equals(te.faultsToInject.keySet())) {
            return false;
        }

        // Are the JSON objects similar for each key?
        boolean equalFaultToInjectMap = this.faultsToInject.entrySet().stream().allMatch(e -> e.getValue().similar(te.faultsToInject.get(e.getKey())));

        return equalRPCsMap && equalFaultToInjectMap;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.executedRPCs, this.faultsToInject);
    }

    private boolean wasFaultInjectedMatcher(String searchField, String stringToFind) {
        return wasFaultInjectedMatcher(searchField, stringToFind, null);
    }

    private boolean wasFaultInjectedMatcher(String searchField, String stringToFind, @Nullable String contains) {
        for (Map.Entry<DistributedExecutionIndex, JSONObject> entry : executedRPCs.entrySet()) {
            JSONObject jsonObject = entry.getValue();

            if (jsonObject.has(searchField)) {
                String field = jsonObject.getString(searchField);
                if (field.contains(stringToFind)) {
                    DistributedExecutionIndex distributedExecutionIndex = entry.getKey();

                    if (faultsToInject.containsKey(distributedExecutionIndex)) {
                        if (contains == null) {
                            return true;
                        } else {
                            JSONObject executedRPCObject = entry.getValue();
                            if (executedRPCObject.getJSONObject("args").getString("toString").contains(contains)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private static JSONObject cleanPayloadForCacheComparison(JSONObject payload) {
        JSONObject jsonObject = new JSONObject(payload.toString());
        jsonObject.remove("execution_index");
        jsonObject.remove("vclock");
        jsonObject.remove("instrumentation_type");
        jsonObject.remove("full_traceback");
        jsonObject.remove("callsite_file");
        jsonObject.remove("callsite_line");
        return jsonObject;
    }

    private static JSONObject cleanPayloadOfInstrumentationType(JSONObject payload) {
        JSONObject jsonObject = new JSONObject(payload.toString());
        jsonObject.remove("instrumentation_type");
        return jsonObject;
    }

    private static JSONObject cleanPayloadOfArguments(JSONObject payload) {
        JSONObject jsonObject = new JSONObject(payload.toString());
        jsonObject.remove("args");
        return jsonObject;
    }
}
