package cloud.filibuster.functional.python.basic;

import cloud.filibuster.examples.Hello.HelloRequest;
import cloud.filibuster.examples.HelloServiceGrpc;
import cloud.filibuster.examples.HelloServiceGrpc.HelloServiceBlockingStub;
import cloud.filibuster.instrumentation.helpers.Networking;
import cloud.filibuster.junit.TestWithFilibuster;
import cloud.filibuster.junit.interceptors.GitHubActionsSkipInvocationInterceptor;
import cloud.filibuster.junit.server.backends.FilibusterLocalProcessServerBackend;
import cloud.filibuster.functional.JUnitBaseTest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static cloud.filibuster.junit.Assertions.wasFaultInjected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.hamcrest.MatcherAssert.assertThat;
import static org.testcontainers.shaded.org.hamcrest.Matchers.containsString;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JUnitFilibusterUnimplementedTest extends JUnitBaseTest {
    private final static Set<String> testExceptionsThrown = new HashSet<>();

    @DisplayName("Test partial hello server grpc route with Filibuster. (MyHelloService, MyWorldService)")
    @ExtendWith(GitHubActionsSkipInvocationInterceptor.class)
    @TestWithFilibuster(serverBackend=FilibusterLocalProcessServerBackend.class)
    @Order(1)
    public void testMyHelloAndMyWorldServiceWithFilibuster() throws InterruptedException {
        ManagedChannel helloChannel = ManagedChannelBuilder
                .forAddress(Networking.getHost("hello"), Networking.getPort("hello"))
                .usePlaintext()
                .build();

        try {
            HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(helloChannel);
            HelloRequest request = HelloRequest.newBuilder().setName("Armerian").build();
            blockingStub.unimplemented(request);
            assertTrue(false);
        } catch (StatusRuntimeException e) {
            if (wasFaultInjected()) {
                testExceptionsThrown.add(e.getMessage());
                assertThat(e.getMessage(), containsString("DATA_LOSS: io.grpc.StatusRuntimeException:"));
            } else {
                // Actual response when the remote service is online but unimplemented.
                assertEquals("DATA_LOSS: io.grpc.StatusRuntimeException: UNIMPLEMENTED: Method cloud.filibuster.examples.WorldService/WorldUnimplemented is unimplemented", e.getMessage());
            }
        }

        helloChannel.shutdownNow();
        helloChannel.awaitTermination(1000, TimeUnit.SECONDS);
    }

    /**
     * Verify that Filibuster generated the correct number of fault injections.
     */
    @DisplayName("Verify correct number of generated Filibuster tests.")
    @ExtendWith(GitHubActionsSkipInvocationInterceptor.class)
    @Test
    @Order(2)
    public void testNumAssertions() {
        assertEquals(5, testExceptionsThrown.size());
    }
}