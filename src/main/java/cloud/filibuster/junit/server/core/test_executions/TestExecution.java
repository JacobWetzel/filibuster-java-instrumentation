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

    HashMap<String, Boolean> firstRequestSeenByService = new HashMap<>();

    public boolean hasSeenFirstRequestFromService(String serviceName) {
        return firstRequestSeenByService.containsKey(serviceName);
    }

    public void registerFirstRequestFromService(String serviceName) {
        firstRequestSeenByService.put(serviceName, true);
    }

    public void printRPCs() {
        logger.info("RPCs executed and interposed by Filibuster:");

        for (DistributedExecutionIndex name: executedRPCs.keySet()) {
            String key = name.toString();
            JSONObject value = executedRPCs.get(name);
            if (key != null && value != null) {
                logger.info("\n" +
                        "distributedExecutionIndex: " + key + "\n" +
                        "payload: " + value.toString(4));
            }
        }

        if (!faultsToInject.isEmpty()) {
            logger.info("Faults injected by Filibuster (" + faultsToInject.size() + "): ");

            for (DistributedExecutionIndex name: faultsToInject.keySet()) {
                String key = name.toString();
                JSONObject value = faultsToInject.get(name);
                JSONObject request = executedRPCs.getOrDefault(name, new JSONObject().put("error", "no request information found")); // eventually remove this.
                logger.info("\n" +
                        "distributedExecutionIndex: " + key + "\n" +
                        "payload: " + value.toString(4) + "\n" +
                        "request: " + request.toString(4));
            }
        } else {
            logger.info("No faults injected by Filibuster:");
        }
    }

    public void addDistributedExecutionIndexWithPayload(DistributedExecutionIndex distributedExecutionIndex, JSONObject payload) {
        // Add to the list of executed RPCs.
        JSONObject payloadWithoutInstrumentationType = cleanPayloadOfInstrumentationType(payload);
        executedRPCs.put(distributedExecutionIndex, payloadWithoutInstrumentationType);

        // Add to the list of nondeterministic executed RPCs.
        JSONObject deterministicPayload = cleanPayloadOfArguments(payload);
        nondeterministicExecutedRPCs.put(distributedExecutionIndex, deterministicPayload);
    }

    public void addDistributedExecutionIndexWithResponsePayload(DistributedExecutionIndex distributedExecutionIndex, JSONObject payload) {
        // Nothing for now.
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

            if (executedRPCObject.getString("args").equals(serializedRequest)) {
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
                            if (executedRPCObject.getString("args").contains(contains)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
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
