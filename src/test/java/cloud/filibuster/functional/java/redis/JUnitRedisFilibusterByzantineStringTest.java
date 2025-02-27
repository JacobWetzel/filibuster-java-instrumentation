package cloud.filibuster.functional.java.redis;

import cloud.filibuster.exceptions.filibuster.FilibusterUnsupportedAPIException;
import cloud.filibuster.functional.java.JUnitAnnotationBaseTest;
import cloud.filibuster.instrumentation.libraries.lettuce.RedisInterceptorFactory;
import cloud.filibuster.integration.examples.armeria.grpc.test_services.RedisClientService;
import cloud.filibuster.junit.TestWithFilibuster;
import cloud.filibuster.junit.configuration.examples.db.byzantine.redis.RedisSingleGetStringByzantineFaultAnalysisConfigurationFile;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cloud.filibuster.junit.Assertions.wasFaultInjected;
import static cloud.filibuster.junit.Assertions.wasFaultInjectedOnMethod;
import static cloud.filibuster.junit.Assertions.wasFaultInjectedOnService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unchecked")
public class JUnitRedisFilibusterByzantineStringTest extends JUnitAnnotationBaseTest {
    static final String key = "test";
    static final String value = "example";
    static StatefulRedisConnection<String, String> statefulRedisConnection;
    static String redisConnectionString;
    private static int numberOfTestExecutions = 0;
    private final List<String> expectedValues = Arrays.asList(null, "123", "", "abcd", "-123ABC", "ThisIsATestString");
    private static final Set<String> actualValues = new HashSet<>();

    @BeforeAll
    public static void primeCache() {
        statefulRedisConnection = RedisClientService.getInstance().redisClient.connect();
        redisConnectionString = RedisClientService.getInstance().connectionString;
        statefulRedisConnection.sync().set(key, value);
    }

    @DisplayName("Tests whether Redis sync interceptor can read from existing key - Byzantine string fault injection")
    @Order(1)
    @TestWithFilibuster(analysisConfigurationFile = RedisSingleGetStringByzantineFaultAnalysisConfigurationFile.class)
    public void testRedisByzantineGet() {
        numberOfTestExecutions++;

        StatefulRedisConnection<String, String> myStatefulRedisConnection = new RedisInterceptorFactory<>(statefulRedisConnection, redisConnectionString).getProxy(StatefulRedisConnection.class);
        RedisCommands<String, String> myRedisCommands = myStatefulRedisConnection.sync();
        String returnVal = myRedisCommands.get(key);

        if (!wasFaultInjected()) {
            assertEquals(value, returnVal, "The value returned from Redis was not the expected value although no byzantine fault was injected.");
        } else {
            actualValues.add(returnVal);
            assertTrue(expectedValues.contains(returnVal), "An unexpected value was returned: " + returnVal);
            assertThrows(FilibusterUnsupportedAPIException.class, () -> wasFaultInjectedOnService("io.lettuce.core.api.sync.RedisStringCommands"), "Expected FilibusterUnsupportedAPIException to be thrown");
            assertTrue(wasFaultInjectedOnMethod("io.lettuce.core.api.sync.RedisStringCommands/get"), "Fault was not injected on the expected Redis method");
        }
    }

    @DisplayName("Verify correct number of test executions.")
    @Test
    @Order(2)
    public void testNumExecutions() {
        // One execution for each expected value + 1 for the non-faulty execution
        assertEquals(expectedValues.size() + 1, numberOfTestExecutions);
    }

    @DisplayName("Verify whether all expected values were returned.")
    @Test
    @Order(2)
    public void testNumReturnValues() {
        assertEquals(expectedValues.size(), actualValues.size());
    }

}
