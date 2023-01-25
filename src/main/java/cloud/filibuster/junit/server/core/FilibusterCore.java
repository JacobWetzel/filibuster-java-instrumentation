package cloud.filibuster.junit.server.core;

import cloud.filibuster.dei.DistributedExecutionIndex;
import cloud.filibuster.dei.implementations.DistributedExecutionIndexV1;
import cloud.filibuster.exceptions.filibuster.FilibusterCoreLogicException;
import cloud.filibuster.exceptions.filibuster.FilibusterFaultInjectionException;
import cloud.filibuster.junit.configuration.FilibusterAnalysisConfiguration;
import cloud.filibuster.junit.configuration.FilibusterConfiguration;
import cloud.filibuster.junit.configuration.FilibusterCustomAnalysisConfigurationFile;
import cloud.filibuster.junit.server.core.test_executions.ConcreteTestExecution;
import cloud.filibuster.junit.server.core.test_executions.PartialTestExecution;
import cloud.filibuster.junit.server.core.test_executions.TestExecution;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("Varifier")
public class FilibusterCore {
    private static final Logger logger = Logger.getLogger(FilibusterCore.class.getName());

    // The current instance of the FilibusterCore.
    // Required as the instrumentation has no direct way of being instantiated with this object.
    private static FilibusterCore currentInstance;

    // The current instance of the FilibusterCore.
    // Required as the instrumentation has no direct way of being instantiated with this object.
    public static FilibusterCore getCurrentInstance() {
        return currentInstance;
    }

    public FilibusterCore(FilibusterConfiguration filibusterConfiguration) {
        currentInstance = this;
        this.filibusterConfiguration = filibusterConfiguration;
    }

    // The current configuration of Filibuster being used.
    private final FilibusterConfiguration filibusterConfiguration;

    // Queue containing the unexplored test executions.
    // These are partial executions, as they are only prefix executions.
    Queue<PartialTestExecution> unexploredTestExecutions = new LinkedBlockingDeque<>();

    // Queue containing the test executions searched.
    // This includes both partial executions we attempted to explore and the actual realized concrete executions.
    Queue<TestExecution> exploredTestExecutions = new LinkedBlockingDeque<>();

    // The partial test execution that we are exploring currently.
    @Nullable
    PartialTestExecution currentPartialTestExecution;

    // The concrete test execution that we are exploring currently.
    // Contains:
    // * a prefix execution that matches the partial test execution.
    // * the same fault profile of the current, concrete test execution.
    @Nullable
    ConcreteTestExecution currentConcreteTestExecution = new ConcreteTestExecution();

    // Analysis file, populated only once received from the test suite.
    @Nullable
    private FilibusterCustomAnalysisConfigurationFile filibusterCustomAnalysisConfigurationFile;

    private int numberOfPartialExecutionsAttempted = 0;
    private int numberOfPartialExecutionsExecuted = 0;
    private int numberOfConcreteExecutionsExecuted = 0;
    private int numberOfUniqueConcreteExecutionsExecuted = 0;

    // RPC hooks.

    // Record an outgoing RPC and conditionally inject faults.
    public JSONObject beginInvocation(JSONObject payload) {
        logger.info("[FILIBUSTER-CORE]: beginInvocation called, payload: " + payload.toString(4));

        if (currentConcreteTestExecution == null) {
            throw new FilibusterCoreLogicException("currentConcreteTestExecution should not be null at this point, something fatal occurred.");
        }

        // Register the RPC using the distributed execution index.
        String distributedExecutionIndexString = payload.getString("execution_index");
        DistributedExecutionIndex distributedExecutionIndex = new DistributedExecutionIndexV1().deserialize(distributedExecutionIndexString);
        int generatedId = currentConcreteTestExecution.addDistributedExecutionIndexWithPayload(distributedExecutionIndex, payload);
        logger.info("[FILIBUSTER-CORE]: beginInvocation called, distributedExecutionIndex: " + distributedExecutionIndex);

        // Generate new partial executions to run and queue them into the unexplored list.
        if (filibusterCustomAnalysisConfigurationFile != null) {
            String serviceName = payload.getString("module");
            String methodName = payload.getString("method");
            generateFaultsUsingAnalysisConfiguration(filibusterConfiguration, distributedExecutionIndex, serviceName, methodName);
        }

        // Return either success or fault (if, this execution contains a fault to inject.)
        JSONObject response = new JSONObject();

        if (currentPartialTestExecution != null && currentPartialTestExecution.shouldFault(distributedExecutionIndex)) {
            JSONObject faultObject = currentPartialTestExecution.getFault(distributedExecutionIndex);

            // This is a bit redundant, we could just take the fault object and insert it directly into the response
            // if we change the API we call.
            if (faultObject.has("forced_exception")) {
                JSONObject forcedExceptionFaultObject = faultObject.getJSONObject("forced_exception");
                logger.info("[FILIBUSTER-CORE]: beginInvocation, injecting faults using forced_exception: " + forcedExceptionFaultObject.toString(4));
                response.put("forced_exception", forcedExceptionFaultObject);
            } else if (faultObject.has("failure_metadata")) {
                JSONObject failureMetadataFaultObject = faultObject.getJSONObject("failure_metadata");
                logger.info("[FILIBUSTER-CORE]: beginInvocation, injecting faults using failure_metadata: " + failureMetadataFaultObject.toString(4));
                response.put("failure_metadata", failureMetadataFaultObject);
            } else {
                logger.info("[FILIBUSTER-CORE]: beginInvocation, failing to inject unknown fault: " + faultObject.toString(4));
                throw new FilibusterFaultInjectionException("Unknown fault configuration: " + faultObject);
            }
        }

        // Legacy, not used, but helpful in debugging and required by instrumentation libraries.
        response.put("generated_id", generatedId);

        logger.info("[FILIBUSTER-CORE]: beginInvocation returning, response: " + response.toString(4));

        return response;
    }

    // JUnit hooks.

    // This is an old callback used to exit the Python server with code = 1 or code = 0 upon failure.
    public void completeIteration(int currentIteration) {
        logger.info("[FILIBUSTER-CORE]: completeIteration called, currentIteration: " + currentIteration);

        if (currentConcreteTestExecution != null) {
            currentConcreteTestExecution.printRPCs();
        } else {
            throw new FilibusterCoreLogicException("currentConcreteTestExecution should not be null at this point, something fatal occurred.");
        }

        printSummary();

        logger.info("[FILIBUSTER-CORE]: completeIteration returning");
    }

    // This is an old callback used to exit the Python server with code = 1 or code = 0 upon failure.
    public void completeIteration(int currentIteration, int exceptionOccurred) {
        logger.info("[FILIBUSTER-CORE]: completeIteration called, currentIteration: " + currentIteration + ", exceptionOccurred: " + exceptionOccurred);


        if (currentConcreteTestExecution != null) {
            currentConcreteTestExecution.printRPCs();
        } else {
            throw new FilibusterCoreLogicException("currentConcreteTestExecution should not be null at this point, something fatal occurred.");
        }

        printSummary();

        logger.info("[FILIBUSTER-CORE]: completeIteration returning");
    }

    // Is there a test execution?
    public boolean hasNextIteration(int currentIteration) {
        logger.info("[FILIBUSTER-CORE]: hasNextiteration called, currentIteration: " + currentIteration);
        boolean result = currentConcreteTestExecution != null;
        logger.info("[FILIBUSTER-CORE]: hasNextiteration returning: " + result);
        return result;
    }

    // Is there a test execution?
    public boolean hasNextIteration(int currentIteration, String caller) {
        logger.info("[FILIBUSTER-CORE]: hasNextiteration called, currentIteration: " + currentIteration + ", caller: " + caller);
        boolean result = currentConcreteTestExecution != null;
        logger.info("[FILIBUSTER-CORE]: hasNextiteration returning: " + result);
        return result;
    }

    // A test has completed and all callbacks have fired.
    public void teardownsCompleted(int currentIteration) {
        logger.info("[FILIBUSTER-CORE]: teardownsCompleted called, currentIteration: " + currentIteration);

        if (currentConcreteTestExecution != null) {
            // We're executing a test and not just running empty iterations (i.e., JUnit maxIterations > number of actual tests.)

            // Add both the current concrete and partial execution to the explored list.
            // * currentPartialTestExecution: the prefix of the concrete execution that was realized by the concrete execution.
            //   this may or may not be set if it's the initial execution.
            // * currentConcreteTestExecution: the actual concrete, realized trace of the test execution.
            if (currentPartialTestExecution != null) {
                numberOfPartialExecutionsAttempted++;

                if (!exploredTestExecutions.contains(currentPartialTestExecution)) {
                    // Don't add to explored queue if it's already there.
                    numberOfPartialExecutionsExecuted++;

                    exploredTestExecutions.add(currentPartialTestExecution);
                } else {
                    logger.severe("[FILIBUSTER-CORE]: teardownsCompleted called, currentPartialTestExecution already exists in the explored queue, this could indicate a problem in Filibuster.");
                }
            }

            if (!exploredTestExecutions.contains(currentConcreteTestExecution)) {
                exploredTestExecutions.add(currentConcreteTestExecution);
                numberOfConcreteExecutionsExecuted++;
                numberOfUniqueConcreteExecutionsExecuted++;
            } else {
                numberOfConcreteExecutionsExecuted++;
            }

            // Unset fields.
            currentPartialTestExecution = null;
            currentConcreteTestExecution = null;

            // If we have another test to run (it will be partial...)
            if (!unexploredTestExecutions.isEmpty()) {
                logger.info("[FILIBUSTER-CORE]: teardownsCompleted, scheduling next test execution.");

                PartialTestExecution nextPartialTestExecution = unexploredTestExecutions.remove();

                // Set the partial execution, which drives fault injection and copy the faults into the concrete execution for the record.
                currentPartialTestExecution = nextPartialTestExecution;
                currentConcreteTestExecution = new ConcreteTestExecution(nextPartialTestExecution);
            }
        }

        logger.info("[FILIBUSTER-CORE]: teardownsCompleted returning.");
    }

    // Fault injection helpers.

    // Was any fault injected?
    public boolean wasFaultInjected() {
        logger.info("[FILIBUSTER-CORE]: wasFaultInjected called");
        if (currentPartialTestExecution == null) {
            return false;
        }
        boolean result = currentPartialTestExecution.wasFaultInjected();

        logger.info("[FILIBUSTER-CORE]: wasFaultInjected returning: " + result);

        return result;
    }

    // Was a fault injected on a particular service?
    public boolean wasFaultInjectedOnService(String serviceName) {
        logger.info("[FILIBUSTER-CORE]: wasFaultInjectedOnService called, serviceName: " + serviceName);
        if (currentPartialTestExecution == null) {
            return false;
        }
        boolean result = currentPartialTestExecution.wasFaultInjectedOnService(serviceName);

        logger.info("[FILIBUSTER-CORE]: wasFaultInjectedOnService returning: " + result);

        return result;
    }

    // Was a fault injected on a particular GRPC call?
    public boolean wasFaultInjectedOnMethod(String serviceName, String methodName) {
        logger.info("[FILIBUSTER-CORE]: wasFaultInjectedOnMethod called, serviceName: " + serviceName + ", methodName: " + methodName);

        if (currentPartialTestExecution == null) {
            return false;
        }
        boolean result = currentPartialTestExecution.wasFaultInjectedOnMethod(serviceName, methodName);

        logger.info("[FILIBUSTER-CORE]: wasFaultInjectedOnMethod returning: " + result);

        return result;
    }

    public boolean wasFaultInjectedOnRequest(String serializedRequest) {
        logger.info("[FILIBUSTER-CORE]: wasFaultInjectedOnRequest called, serializedRequest: " + serializedRequest);

        if (currentPartialTestExecution == null) {
            return false;
        }
        boolean result = currentPartialTestExecution.wasFaultInjectedOnRequest(serializedRequest);

        logger.info("[FILIBUSTER-CORE]: wasFaultInjectedOnRequest returning: " + result);

        return result;
    }

    public boolean wasFaultInjectedOnMethodWherePayloadContains(String serviceName, String methodName, String contains) {
        logger.info("[FILIBUSTER-CORE]: wasFaultInjectedOnMethodWherePayloadContains called, serviceName: " + serviceName + ", methodName: " + methodName + ", contains: " + contains);

        if (currentPartialTestExecution == null) {
            return false;
        }
        boolean result = currentPartialTestExecution.wasFaultInjectedOnMethodWherePayloadContains(serviceName, methodName, contains);

        logger.info("[FILIBUSTER-CORE]: wasFaultInjectedOnMethodWherePayloadContains returning: " + result);

        return result;
    }

    // Record that an RPC completed with a particular value.
    // Only needed for:
    // 1. Dynamic Reduction because we need to keep track of responses.
    // 2. HTTP calls, so we know which service we actually invoked.
    public JSONObject endInvocation(JSONObject payload) {
        logger.info("[FILIBUSTER-CORE]: endInvocation called");

        String distributedExecutionIndexString = payload.getString("execution_index");
        DistributedExecutionIndex distributedExecutionIndex = new DistributedExecutionIndexV1().deserialize(distributedExecutionIndexString);

        logger.info("[FILIBUSTER-CORE]: endInvocation called, distributedExecutionIndex: " + distributedExecutionIndex);

        JSONObject response = new JSONObject();
        response.put("execution_index", payload.getString("execution_index"));

        logger.info("[FILIBUSTER-CORE]: endInvocation returning: " + response.toString(4));

        return response;
    }

    // Is this the first time that we are seeing an RPC from this service?
    // Used to control when vclocks, etc. are reset to ensure they are consistent across executions.
    public boolean isNewTestExecution(String serviceName) {
        logger.info("[FILIBUSTER-CORE]: isNewTestExecution called, serviceName: " + serviceName);

        boolean result = false;

        if (currentConcreteTestExecution == null) {
            // Doesn't really matter, because if this isn't set, no tests will execute.
            result = false;
        } else {
            if (!currentConcreteTestExecution.hasSeenFirstRequestromService(serviceName)) {
                currentConcreteTestExecution.registerFirstRequestFromService(serviceName);
                result = true;
            } else {
                result = false;
            }
        }

        logger.info("[FILIBUSTER-CORE]: isNewTestExecution returning: " + result);

        return result;
    }

    // This callback was used to terminate the Filibuster python server -- required if using certain backends for
    // writing counterexample files, etc., but should automatically be handled by the JUnit invocation interceptors now.
    public void terminateFilibuster() {
        logger.info("[FILIBUSTER-CORE]: terminate called.");
        // Nothing.
        logger.info("[FILIBUSTER-CORE]: terminate returning.");
    }

    // Configuration.

    public void analysisFile(JSONObject analysisFile) {
        logger.info("[FILIBUSTER-CORE]: analysisFile called, payload: " + analysisFile.toString(4));

        FilibusterCustomAnalysisConfigurationFile.Builder filibusterCustomAnalysisConfigurationFileBuilder = new FilibusterCustomAnalysisConfigurationFile.Builder();

        for (String name : analysisFile.keySet()) {
            FilibusterAnalysisConfiguration.Builder filibusterAnalysisConfigurationBuilder = new FilibusterAnalysisConfiguration.Builder();
            filibusterAnalysisConfigurationBuilder.name(name);

            JSONObject nameObject = analysisFile.getJSONObject(name);

            if (nameObject.has("pattern")) {
                filibusterAnalysisConfigurationBuilder.pattern(nameObject.getString("pattern"));
            }

            if (nameObject.has("exceptions")) {
                JSONArray jsonArray = nameObject.getJSONArray("exceptions");

                for (Object obj : jsonArray) {
                    JSONObject exceptionObject = (JSONObject) obj;

                    String exceptionName = exceptionObject.getString("name");
                    JSONObject exceptionMetadata = exceptionObject.getJSONObject("metadata");

                    HashMap<String, String> exceptionMetadataMap = new HashMap<>();
                    for (String metadataObjectKey : exceptionMetadata.keySet()) {
                        exceptionMetadataMap.put(metadataObjectKey, exceptionMetadata.getString(metadataObjectKey));
                    }

                    filibusterAnalysisConfigurationBuilder.exception(exceptionName, exceptionMetadataMap);
                    logger.info("[FILIBUSTER-CORE]: analysisFile, found new configuration, exceptionName: " + exceptionName + ", exceptionMetadataMap: " + exceptionMetadataMap);
                }
            }

            if (nameObject.has("errors")) {
                JSONArray jsonArray = nameObject.getJSONArray("errors");

                for (Object obj : jsonArray) {
                    JSONObject errorObject = (JSONObject) obj;

                    String errorServiceName = errorObject.getString("service_name");
                    JSONArray errorTypes = errorObject.getJSONArray("types");

                    List<JSONObject> errorTypesList = new ArrayList<>();
                    for (Object errorType : errorTypes) {
                        errorTypesList.add((JSONObject) errorType);
                    }

                    filibusterAnalysisConfigurationBuilder.error(errorServiceName, errorTypesList);
                    logger.info("[FILIBUSTER-CORE]: analysisFile, found new configuration, errorServiceName: " + errorServiceName + ", errorTypesList: " + errorTypesList);
                }
            }

            FilibusterAnalysisConfiguration filibusterAnalysisConfiguration = filibusterAnalysisConfigurationBuilder.build();
            filibusterCustomAnalysisConfigurationFileBuilder.analysisConfiguration(filibusterAnalysisConfiguration);
        }

        filibusterCustomAnalysisConfigurationFile = filibusterCustomAnalysisConfigurationFileBuilder.build();

        logger.info("[FILIBUSTER-CORE]: analysisFile, set instance variable, returning.");
    }

    private void generateFaultsUsingAnalysisConfiguration(
            FilibusterConfiguration filibusterConfiguration,
            DistributedExecutionIndex distributedExecutionIndex,
            String serviceName,
            String methodName
    ) {
        logger.info("[FILIBUSTER-CORE]: generateFaultsUsingAnalysisConfiguration called.");

        if (filibusterCustomAnalysisConfigurationFile != null) {
            for (FilibusterAnalysisConfiguration filibusterAnalysisConfiguration : filibusterCustomAnalysisConfigurationFile.getFilibusterAnalysisConfigurations()) {
                if (filibusterAnalysisConfiguration.isPatternMatch(methodName)) {
                    // Exceptions.
                    List<JSONObject> exceptionFaultObjects = filibusterAnalysisConfiguration.getExceptionFaultObjects();

                    for(JSONObject faultObject : exceptionFaultObjects) {
                        createAndSchedulePartialTestExecution(filibusterConfiguration, distributedExecutionIndex, faultObject);
                    }

                    // Errors.
                    List<JSONObject> errorFaultObjects = filibusterAnalysisConfiguration.getErrorFaultObjects();

                    for(JSONObject faultObject : errorFaultObjects) {
                        JSONObject failureMetadataObject = faultObject.getJSONObject("failure_metadata");

                        String faultServiceName = failureMetadataObject.getString("service_name");
                        Pattern faultServiceNamePattern = Pattern.compile(faultServiceName, Pattern.CASE_INSENSITIVE);
                        Matcher matcher = faultServiceNamePattern.matcher(serviceName);

                        List<Object> faultTypesArray = failureMetadataObject.getJSONArray("types").toList();

                        if (matcher.find()) {
                            for (Object obj : faultTypesArray) {
                                JSONObject faultTypeObject = (JSONObject) obj;
                                createAndSchedulePartialTestExecution(filibusterConfiguration, distributedExecutionIndex, faultTypeObject);
                            }
                        }
                    }
                }
            }
        }

        logger.info("[FILIBUSTER-CORE]: generateFaultsUsingAnalysisConfiguration returning.");
    }

    private void createAndSchedulePartialTestExecution(
            FilibusterConfiguration filibusterConfiguration,
            DistributedExecutionIndex distributedExecutionIndex,
            JSONObject faultObject) {
        logger.info("[FILIBUSTER-CORE]: createAndSchedulePartialTestExecution called.");

        if (currentConcreteTestExecution != null) {
            PartialTestExecution partialTestExecution = currentConcreteTestExecution.cloneToPartialTestExecution();
            partialTestExecution.addFaultToInject(distributedExecutionIndex, faultObject);

            boolean partialIsExploredExecution = exploredTestExecutions.contains(partialTestExecution);
            boolean partialIsScheduledExecution = unexploredTestExecutions.contains(partialTestExecution);
            boolean partialIsCurrentExecution = currentPartialTestExecution == null ? false : currentPartialTestExecution.equals(partialTestExecution);

            if (!partialIsExploredExecution && !partialIsScheduledExecution && !partialIsCurrentExecution) {
                if (filibusterConfiguration.getSuppressCombinations()) {
                    if (!(partialTestExecution.getFaultsToInjectSize() > 1)) {
                        unexploredTestExecutions.add(partialTestExecution);
                        logger.info("[FILIBUSTER-CORE]: createAndSchedulePartialTestExecution, adding new execution to the queue.");
                    } else {
                        logger.info("[FILIBUSTER-CORE]: createAndSchedulePartialTestExecution, not scheduling test execution because it contains > 1 fault.");
                    }
                } else {
                    logger.info("[FILIBUSTER-CORE]: createAndSchedulePartialTestExecution, adding new execution to the queue.");
                    unexploredTestExecutions.add(partialTestExecution);
                }
            }
        }

        logger.info("[FILIBUSTER-CORE]: createAndSchedulePartialTestExecution returning.");
    }

    private void printSummary() {
        logger.info("[FILIBUSTER-CORE]: Test Summary: ");
        logger.info("[FILIBUSTER-CORE]: * numberOfPartialExecutionsAttempted:       " + numberOfPartialExecutionsAttempted);
        logger.info("[FILIBUSTER-CORE]: * numberOfPartialExecutionsExecuted:        " + numberOfPartialExecutionsExecuted);

        if (numberOfPartialExecutionsAttempted != numberOfPartialExecutionsExecuted) {
            logger.warning("[FILIBUSTER-CORE]: Number of partial test executions attempted doesn't match executed: this could indicate a problem.");
        }

        logger.info("[FILIBUSTER-CORE]: * numberOfConcreteExecutionsExecuted:       " + numberOfConcreteExecutionsExecuted);
        logger.info("[FILIBUSTER-CORE]: * numberOfUniqueConcreteExecutionsExecuted: " + numberOfUniqueConcreteExecutionsExecuted);

        if (numberOfPartialExecutionsExecuted != 0 && numberOfConcreteExecutionsExecuted != 0) { // Actually ran something.
            if (numberOfPartialExecutionsExecuted != (numberOfConcreteExecutionsExecuted - 1)) {
                logger.warning("[FILIBUSTER-CORE]: Number of partial test executions attempted doesn't match concrete (-1): this could indicate a problem.");
            }
        }
    }
}
