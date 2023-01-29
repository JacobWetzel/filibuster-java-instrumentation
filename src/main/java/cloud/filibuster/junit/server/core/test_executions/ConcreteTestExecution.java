package cloud.filibuster.junit.server.core.test_executions;

import cloud.filibuster.dei.DistributedExecutionIndex;
import org.json.JSONObject;

import java.util.Map;

public class ConcreteTestExecution extends TestExecution {
    public ConcreteTestExecution() {

    }

    public ConcreteTestExecution(AbstractTestExecution abstractTestExecution) {
        for (Map.Entry<DistributedExecutionIndex, JSONObject> faultToInject : abstractTestExecution.faultsToInject.entrySet()) {
            faultsToInject.put(faultToInject.getKey(), faultToInject.getValue());
        }
    }

    @SuppressWarnings("Varifier")
    public AbstractTestExecution toAbstractTestExecution() {
        AbstractTestExecution abstractTestExecution = new AbstractTestExecution(this);

        for (Map.Entry<DistributedExecutionIndex, JSONObject> mapEntry : executedRPCs.entrySet()) {
            abstractTestExecution.executedRPCs.put(mapEntry.getKey(), mapEntry.getValue());
        }

        for (Map.Entry<DistributedExecutionIndex, JSONObject> mapEntry : nondeterministicExecutedRPCs.entrySet()) {
            abstractTestExecution.nondeterministicExecutedRPCs.put(mapEntry.getKey(), mapEntry.getValue());
        }

        for (Map.Entry<DistributedExecutionIndex, JSONObject> mapEntry : faultsToInject.entrySet()) {
            abstractTestExecution.faultsToInject.put(mapEntry.getKey(), mapEntry.getValue());
        }

        return abstractTestExecution;
    }
}
