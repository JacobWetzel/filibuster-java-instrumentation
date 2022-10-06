package cloud.filibuster.examples.armeria.http.tests;

import cloud.filibuster.dei.DistributedExecutionIndex;
import cloud.filibuster.instrumentation.FilibusterServer;
import cloud.filibuster.instrumentation.TestHelper;
import cloud.filibuster.instrumentation.datatypes.VectorClock;
import cloud.filibuster.instrumentation.helpers.Networking;
import cloud.filibuster.instrumentation.libraries.armeria.http.FilibusterDecoratingHttpClient;
import cloud.filibuster.instrumentation.libraries.armeria.http.FilibusterDecoratingHttpService;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.ResponseHeaders;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static cloud.filibuster.examples.test_servers.HelloServer.resetInitialDistributedExecutionIndex;
import static cloud.filibuster.examples.test_servers.HelloServer.setInitialDistributedExecutionIndex;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HelloServerWithHelloAndWorldAndFilibusterServerTest extends HelloServerTest {
    @BeforeEach
    public void startServices() throws IOException, InterruptedException {
        super.startHelloServer();
        super.startWorldServer();
        super.startExternalServer();
        super.startFilibuster();

        FilibusterServer.oneNewTestExecution = true;

        DistributedExecutionIndex startingDistributedExecutionIndex = createNewDistributedExecutionIndex();
        startingDistributedExecutionIndex.push("some-random-location-1337");

        setInitialDistributedExecutionIndex(startingDistributedExecutionIndex.toString());
    }

    @AfterEach
    public void stopServices() throws InterruptedException {
        super.stopHelloServer();
        super.stopWorldServer();
        super.stopExternalServer();
        super.stopFilibuster();

        FilibusterServer.noNewTestExecution = false;

        resetInitialDistributedExecutionIndex();
    }

    @BeforeEach
    public void enableFilibuster() {
        FilibusterDecoratingHttpClient.disableInstrumentation = false;
        FilibusterDecoratingHttpService.disableInstrumentation = false;
    }

    @Test
    @DisplayName("Test hello server world route (with Filibuster.)")
    public void testWorldWithFilibuster() {
        // Get remote resource.
        String baseURI = "http://" + Networking.getHost("hello") + ":" + Networking.getPort("hello") + "/";
        WebClient webClient = TestHelper.getTestWebClient(baseURI);
        RequestHeaders getHeaders = RequestHeaders.of(HttpMethod.GET, "/world", HttpHeaderNames.ACCEPT, "application/json");
        AggregatedHttpResponse response = webClient.execute(getHeaders).aggregate().join();

        // Get headers and verify a 200 response.
        ResponseHeaders headers = response.headers();
        String statusCode = headers.get(HttpHeaderNames.STATUS);
        assertEquals("200", statusCode);

        // Assemble execution index.
        DistributedExecutionIndex requestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        requestDistributedExecutionIndex.push("some-random-location-1337");
        requestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-0830c708354ff68b5b6798d5e3e5e632c6517de9-b4604597df48b0eae13cf0c110562e150ace79ff");

        // Very proper number of Filibuster records.
        assertEquals(3, FilibusterServer.payloadsReceived.size());

        // These are the required Filibuster instrumentation fields for this call.
        JSONObject invocationCompletedPayload = FilibusterServer.payloadsReceived.get(2);
        assertEquals("invocation_complete", invocationCompletedPayload.getString("instrumentation_type"));
        assertEquals(0, invocationCompletedPayload.getInt("generated_id"));
        assertEquals(requestDistributedExecutionIndex.toString(), invocationCompletedPayload.getString("execution_index"));
    }

    @Test
    @DisplayName("Test hello server world twice route (with Filibuster.)")
    public void testWorldTwiceWithFilibuster() {
        // Get remote resource.
        String baseURI = "http://" + Networking.getHost("hello") + ":" + Networking.getPort("hello") + "/";
        WebClient webClient = TestHelper.getTestWebClient(baseURI);
        RequestHeaders getHeaders = RequestHeaders.of(HttpMethod.GET, "/world-twice", HttpHeaderNames.ACCEPT, "application/json");
        AggregatedHttpResponse response = webClient.execute(getHeaders).aggregate().join();

        // Get headers and verify a 200 response.
        ResponseHeaders headers = response.headers();
        String statusCode = headers.get(HttpHeaderNames.STATUS);
        assertEquals("200", statusCode);

        // Very proper number of Filibuster records.
        assertEquals(6, FilibusterServer.payloadsReceived.size());

        // Assemble execution indexes.

        DistributedExecutionIndex firstRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        firstRequestDistributedExecutionIndex.push("some-random-location-1337");
        firstRequestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-6fdd5dfe36863b9b76b0603d086b1dc8910c7be4-b4604597df48b0eae13cf0c110562e150ace79ff");

        DistributedExecutionIndex secondRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        secondRequestDistributedExecutionIndex.push("some-random-location-1337");
        secondRequestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-9255a8c4480abb70f36a5c91ea835269deec2991-b4604597df48b0eae13cf0c110562e150ace79ff");

        // These are the required Filibuster instrumentation fields for this call.

        JSONObject firstInvocationCompletedPayload = FilibusterServer.payloadsReceived.get(2);
        assertEquals("invocation_complete", firstInvocationCompletedPayload.getString("instrumentation_type"));
        assertEquals(firstRequestDistributedExecutionIndex.toString(), firstInvocationCompletedPayload.getString("execution_index"));

        JSONObject secondInvocationCompletedPayload = FilibusterServer.payloadsReceived.get(5);
        assertEquals("invocation_complete", secondInvocationCompletedPayload.getString("instrumentation_type"));
        assertEquals(secondRequestDistributedExecutionIndex.toString(), secondInvocationCompletedPayload.getString("execution_index"));
    }

    @Test
    @DisplayName("Test hello server cycle route (with Filibuster.)")
    public void testCycleWithFilibuster() {
        // Get remote resource.
        String baseURI = "http://" + Networking.getHost("hello") + ":" + Networking.getPort("hello") + "/";
        WebClient webClient = TestHelper.getTestWebClient(baseURI);
        RequestHeaders getHeaders = RequestHeaders.of(HttpMethod.GET, "/cycle", HttpHeaderNames.ACCEPT, "application/json");
        AggregatedHttpResponse response = webClient.execute(getHeaders).aggregate().join();

        // Get headers and verify a 200 response.
        ResponseHeaders headers = response.headers();
        String statusCode = headers.get(HttpHeaderNames.STATUS);
        assertEquals("200", statusCode);

        // Very proper number of Filibuster records.
        assertEquals(6, FilibusterServer.payloadsReceived.size());

        // Assemble execution indexes.

        DistributedExecutionIndex firstRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        firstRequestDistributedExecutionIndex.push("some-random-location-1337");
        firstRequestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-54d4cf484c2bb92267575ccdb396c8c67879c552-3faf16c421edacc1b4954b48cfd5b5e08d77f3f1");

        DistributedExecutionIndex secondRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        secondRequestDistributedExecutionIndex.push("some-random-location-1337");
        secondRequestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-54d4cf484c2bb92267575ccdb396c8c67879c552-3faf16c421edacc1b4954b48cfd5b5e08d77f3f1");
        secondRequestDistributedExecutionIndex.push("V1-7c211433f02071597741e6ff5a8ea34789abbf43-bf801c417a24769c151e3729f35ee3e62e4e04d4-c36469a105518a038019d230d75b2e575a7e2604-f05ca0f49ff94c87bb10dd4265940b0bba02dcb2");

        // Assemble vector clocks.

        VectorClock firstRequestVectorClock = new VectorClock();
        firstRequestVectorClock.incrementClock("hello");

        VectorClock secondRequestVectorClock = new VectorClock();
        secondRequestVectorClock.incrementClock("hello");
        secondRequestVectorClock.incrementClock("world");

        // These are the required Filibuster instrumentation fields for this call.

        JSONObject firstInvocationPayload = FilibusterServer.payloadsReceived.get(0);
        assertEquals("invocation", firstInvocationPayload.getString("instrumentation_type"));
        assertEquals(firstRequestDistributedExecutionIndex.toString(), firstInvocationPayload.getString("execution_index"));
        assertEquals(firstRequestVectorClock.toJSONObject().toString(), firstInvocationPayload.getJSONObject("vclock").toString());

        JSONObject firstRequestReceivedPayload = FilibusterServer.payloadsReceived.get(1);
        assertEquals("request_received", firstRequestReceivedPayload.getString("instrumentation_type"));
        assertEquals(firstRequestDistributedExecutionIndex.toString(), firstRequestReceivedPayload.getString("execution_index"));
        assertEquals(firstRequestVectorClock.toJSONObject().toString(), firstInvocationPayload.getJSONObject("vclock").toString());

        JSONObject secondInvocationPayload = FilibusterServer.payloadsReceived.get(2);
        assertEquals("invocation", secondInvocationPayload.getString("instrumentation_type"));
        assertEquals(secondRequestDistributedExecutionIndex.toString(), secondInvocationPayload.getString("execution_index"));
        assertEquals(secondRequestVectorClock.toJSONObject().toString(), secondInvocationPayload.getJSONObject("vclock").toString());

        JSONObject secondRequestReceivedPayload = FilibusterServer.payloadsReceived.get(3);
        assertEquals("request_received", secondRequestReceivedPayload.getString("instrumentation_type"));
        assertEquals(secondRequestDistributedExecutionIndex.toString(), secondRequestReceivedPayload.getString("execution_index"));
        assertEquals(secondRequestVectorClock.toJSONObject().toString(), secondInvocationPayload.getJSONObject("vclock").toString());

        JSONObject secondInvocationCompletePayload = FilibusterServer.payloadsReceived.get(4);
        assertEquals("invocation_complete", secondInvocationCompletePayload.getString("instrumentation_type"));
        assertEquals(secondRequestDistributedExecutionIndex.toString(), secondInvocationCompletePayload.getString("execution_index"));
        assertEquals(secondRequestVectorClock.toJSONObject().toString(), secondInvocationPayload.getJSONObject("vclock").toString());

        JSONObject firstInvocationCompletePayload = FilibusterServer.payloadsReceived.get(5);
        assertEquals("invocation_complete", firstInvocationCompletePayload.getString("instrumentation_type"));
        assertEquals(firstRequestDistributedExecutionIndex.toString(), firstInvocationCompletePayload.getString("execution_index"));
        assertEquals(firstRequestVectorClock.toJSONObject().toString(), firstInvocationPayload.getJSONObject("vclock").toString());
    }

    @Test
    @DisplayName("Test hello server cycle 2 route (with Filibuster.)")
    public void testCycle2WithFilibuster() {
        // Get remote resource.
        String baseURI = "http://" + Networking.getHost("hello") + ":" + Networking.getPort("hello") + "/";
        WebClient webClient = TestHelper.getTestWebClient(baseURI);
        RequestHeaders getHeaders = RequestHeaders.of(HttpMethod.GET, "/cycle2", HttpHeaderNames.ACCEPT, "application/json");
        AggregatedHttpResponse response = webClient.execute(getHeaders).aggregate().join();

        // Get headers and verify a 200 response.
        ResponseHeaders headers = response.headers();
        String statusCode = headers.get(HttpHeaderNames.STATUS);
        assertEquals("200", statusCode);

        // Very proper number of Filibuster records.
        assertEquals(9, FilibusterServer.payloadsReceived.size());

        // Assemble execution indexes.

        DistributedExecutionIndex firstRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        firstRequestDistributedExecutionIndex.push("some-random-location-1337");
        firstRequestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-b23e700ccfa00bdc887c06b84a6f3a8754784453-cb508e66d0f8ee1ef4546567356eb01c51cb9598");

        DistributedExecutionIndex secondRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        secondRequestDistributedExecutionIndex.push("some-random-location-1337");
        secondRequestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-b23e700ccfa00bdc887c06b84a6f3a8754784453-cb508e66d0f8ee1ef4546567356eb01c51cb9598");
        secondRequestDistributedExecutionIndex.push("V1-7c211433f02071597741e6ff5a8ea34789abbf43-bf801c417a24769c151e3729f35ee3e62e4e04d4-7e7340bf6a4a6ae52d91786740892a93abd44b0c-44f8aba85b6dc43f841d4ae2648ad1c7a922a331");

        DistributedExecutionIndex thirdRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        thirdRequestDistributedExecutionIndex.push("some-random-location-1337");
        thirdRequestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-b23e700ccfa00bdc887c06b84a6f3a8754784453-cb508e66d0f8ee1ef4546567356eb01c51cb9598");
        thirdRequestDistributedExecutionIndex.push("V1-7c211433f02071597741e6ff5a8ea34789abbf43-bf801c417a24769c151e3729f35ee3e62e4e04d4-7e7340bf6a4a6ae52d91786740892a93abd44b0c-44f8aba85b6dc43f841d4ae2648ad1c7a922a331");
        thirdRequestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-d1a2df9cfc30644b41ab3490aeca2e787b1683d1-b4604597df48b0eae13cf0c110562e150ace79ff");

        // Assemble vector clocks.

        VectorClock firstRequestVectorClock = new VectorClock();
        firstRequestVectorClock.incrementClock("hello");

        VectorClock secondRequestVectorClock = new VectorClock();
        secondRequestVectorClock.incrementClock("hello");
        secondRequestVectorClock.incrementClock("world");

        VectorClock thirdRequestVectorClock = new VectorClock();
        thirdRequestVectorClock.incrementClock("hello");
        thirdRequestVectorClock.incrementClock("world");
        thirdRequestVectorClock.incrementClock("hello");

        // These are the required Filibuster instrumentation fields for this call.

        JSONObject firstInvocationPayload = FilibusterServer.payloadsReceived.get(0);
        assertEquals("invocation", firstInvocationPayload.getString("instrumentation_type"));
        assertEquals(firstRequestDistributedExecutionIndex.toString(), firstInvocationPayload.getString("execution_index"));
        assertEquals(firstRequestVectorClock.toJSONObject().toString(), firstInvocationPayload.getJSONObject("vclock").toString());

        JSONObject firstRequestReceivedPayload = FilibusterServer.payloadsReceived.get(1);
        assertEquals("request_received", firstRequestReceivedPayload.getString("instrumentation_type"));
        assertEquals(firstRequestDistributedExecutionIndex.toString(), firstRequestReceivedPayload.getString("execution_index"));

        JSONObject secondInvocationPayload = FilibusterServer.payloadsReceived.get(2);
        assertEquals("invocation", secondInvocationPayload.getString("instrumentation_type"));
        assertEquals(secondRequestDistributedExecutionIndex.toString(), secondInvocationPayload.getString("execution_index"));
        assertEquals(secondRequestVectorClock.toJSONObject().toString(), secondInvocationPayload.getJSONObject("vclock").toString());

        JSONObject secondRequestReceivedPayload = FilibusterServer.payloadsReceived.get(3);
        assertEquals("request_received", secondRequestReceivedPayload.getString("instrumentation_type"));
        assertEquals(secondRequestDistributedExecutionIndex.toString(), secondRequestReceivedPayload.getString("execution_index"));

        JSONObject thirdInvocationPayload = FilibusterServer.payloadsReceived.get(4);
        assertEquals("invocation", thirdInvocationPayload.getString("instrumentation_type"));
        assertEquals(thirdRequestDistributedExecutionIndex.toString(), thirdInvocationPayload.getString("execution_index"));
        assertEquals(thirdRequestVectorClock.toJSONObject().toString(), thirdInvocationPayload.getJSONObject("vclock").toString());

        JSONObject thirdRequestReceivedPayload = FilibusterServer.payloadsReceived.get(5);
        assertEquals("request_received", thirdRequestReceivedPayload.getString("instrumentation_type"));
        assertEquals(thirdRequestDistributedExecutionIndex.toString(), thirdRequestReceivedPayload.getString("execution_index"));

        JSONObject thirdInvocationCompletePayload = FilibusterServer.payloadsReceived.get(6);
        assertEquals("invocation_complete", thirdInvocationCompletePayload.getString("instrumentation_type"));
        assertEquals(thirdRequestDistributedExecutionIndex.toString(), thirdInvocationCompletePayload.getString("execution_index"));
        assertEquals(thirdRequestVectorClock.toJSONObject().toString(), thirdInvocationCompletePayload.getJSONObject("vclock").toString());

        JSONObject secondInvocationCompletePayload = FilibusterServer.payloadsReceived.get(7);
        assertEquals("invocation_complete", secondInvocationCompletePayload.getString("instrumentation_type"));
        assertEquals(secondRequestDistributedExecutionIndex.toString(), secondInvocationCompletePayload.getString("execution_index"));
        assertEquals(secondRequestVectorClock.toJSONObject().toString(), secondInvocationCompletePayload.getJSONObject("vclock").toString());

        JSONObject firstInvocationCompletePayload = FilibusterServer.payloadsReceived.get(8);
        assertEquals("invocation_complete", firstInvocationCompletePayload.getString("instrumentation_type"));
        assertEquals(firstRequestDistributedExecutionIndex.toString(), firstInvocationCompletePayload.getString("execution_index"));
        assertEquals(firstRequestVectorClock.toJSONObject().toString(), firstInvocationCompletePayload.getJSONObject("vclock").toString());
    }

    @Test
    @DisplayName("Test hello server cycle 3 route (with Filibuster.)")
    public void testCycle3WithFilibuster() {
        // Get remote resource.
        String baseURI = "http://" + Networking.getHost("hello") + ":" + Networking.getPort("hello") + "/";
        WebClient webClient = TestHelper.getTestWebClient(baseURI);
        RequestHeaders getHeaders = RequestHeaders.of(HttpMethod.GET, "/cycle3", HttpHeaderNames.ACCEPT, "application/json");
        AggregatedHttpResponse response = webClient.execute(getHeaders).aggregate().join();

        // Get headers and verify a 200 response.
        ResponseHeaders headers = response.headers();
        String statusCode = headers.get(HttpHeaderNames.STATUS);
        assertEquals("200", statusCode);

        // Very proper number of Filibuster records.
        assertEquals(12, FilibusterServer.payloadsReceived.size());

        // Assemble execution indexes.

        String eiString1 = "some-random-location-1337";
        String eiString2 = "V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-c0772d7ec39d99d1c70f4dc9742c651f10400d0a-cb508e66d0f8ee1ef4546567356eb01c51cb9598";
        String eiString3 = "V1-7c211433f02071597741e6ff5a8ea34789abbf43-bf801c417a24769c151e3729f35ee3e62e4e04d4-7e7340bf6a4a6ae52d91786740892a93abd44b0c-44f8aba85b6dc43f841d4ae2648ad1c7a922a331";
        String eiString4 = "V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-d1a2df9cfc30644b41ab3490aeca2e787b1683d1-b4604597df48b0eae13cf0c110562e150ace79ff";
        String eiString5 = "V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-bf801c417a24769c151e3729f35ee3e62e4e04d4-ddeedf2dc6a63766551df3cf222fbe419770f357-b4604597df48b0eae13cf0c110562e150ace79ff";

        DistributedExecutionIndex firstRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        firstRequestDistributedExecutionIndex.push(eiString1);
        firstRequestDistributedExecutionIndex.push(eiString2);

        DistributedExecutionIndex secondRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        secondRequestDistributedExecutionIndex.push(eiString1);
        secondRequestDistributedExecutionIndex.push(eiString2);
        secondRequestDistributedExecutionIndex.push(eiString3);

        DistributedExecutionIndex thirdRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        thirdRequestDistributedExecutionIndex.push(eiString1);
        thirdRequestDistributedExecutionIndex.push(eiString2);
        thirdRequestDistributedExecutionIndex.push(eiString3);
        thirdRequestDistributedExecutionIndex.push(eiString4);

        DistributedExecutionIndex fourthRequestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        fourthRequestDistributedExecutionIndex.push(eiString1);
        fourthRequestDistributedExecutionIndex.push(eiString5);

        // Assemble vector clocks.

        VectorClock firstRequestVectorClock = new VectorClock();
        firstRequestVectorClock.incrementClock("hello");

        VectorClock secondRequestVectorClock = new VectorClock();
        secondRequestVectorClock.incrementClock("hello");
        secondRequestVectorClock.incrementClock("world");

        VectorClock thirdRequestVectorClock = new VectorClock();
        thirdRequestVectorClock.incrementClock("hello");
        thirdRequestVectorClock.incrementClock("world");
        thirdRequestVectorClock.incrementClock("hello");

        VectorClock fourthRequestVectorClock = new VectorClock();
        fourthRequestVectorClock.incrementClock("hello");
        fourthRequestVectorClock.incrementClock("hello");

        // These are the required Filibuster instrumentation fields for this call.

        JSONObject firstInvocationPayload = FilibusterServer.payloadsReceived.get(0);
        assertEquals("invocation", firstInvocationPayload.getString("instrumentation_type"));
        assertEquals(firstRequestDistributedExecutionIndex.toString(), firstInvocationPayload.getString("execution_index"));
        assertEquals(firstRequestVectorClock.toJSONObject().toString(), firstInvocationPayload.getJSONObject("vclock").toString());

        JSONObject firstRequestReceivedPayload = FilibusterServer.payloadsReceived.get(1);
        assertEquals("request_received", firstRequestReceivedPayload.getString("instrumentation_type"));
        assertEquals(firstRequestDistributedExecutionIndex.toString(), firstRequestReceivedPayload.getString("execution_index"));

        JSONObject secondInvocationPayload = FilibusterServer.payloadsReceived.get(2);
        assertEquals("invocation", secondInvocationPayload.getString("instrumentation_type"));
        assertEquals(secondRequestDistributedExecutionIndex.toString(), secondInvocationPayload.getString("execution_index"));
        assertEquals(secondRequestVectorClock.toJSONObject().toString(), secondInvocationPayload.getJSONObject("vclock").toString());

        JSONObject secondRequestReceivedPayload = FilibusterServer.payloadsReceived.get(3);
        assertEquals("request_received", secondRequestReceivedPayload.getString("instrumentation_type"));
        assertEquals(secondRequestDistributedExecutionIndex.toString(), secondRequestReceivedPayload.getString("execution_index"));

        JSONObject thirdInvocationPayload = FilibusterServer.payloadsReceived.get(4);
        assertEquals("invocation", thirdInvocationPayload.getString("instrumentation_type"));
        assertEquals(thirdRequestDistributedExecutionIndex.toString(), thirdInvocationPayload.getString("execution_index"));
        assertEquals(thirdRequestVectorClock.toJSONObject().toString(), thirdInvocationPayload.getJSONObject("vclock").toString());

        JSONObject thirdRequestReceivedPayload = FilibusterServer.payloadsReceived.get(5);
        assertEquals("request_received", thirdRequestReceivedPayload.getString("instrumentation_type"));
        assertEquals(thirdRequestDistributedExecutionIndex.toString(), thirdRequestReceivedPayload.getString("execution_index"));

        JSONObject thirdInvocationCompletePayload = FilibusterServer.payloadsReceived.get(6);
        assertEquals("invocation_complete", thirdInvocationCompletePayload.getString("instrumentation_type"));
        assertEquals(thirdRequestDistributedExecutionIndex.toString(), thirdInvocationCompletePayload.getString("execution_index"));
        assertEquals(thirdRequestVectorClock.toJSONObject().toString(), thirdInvocationCompletePayload.getJSONObject("vclock").toString());

        JSONObject secondInvocationCompletePayload = FilibusterServer.payloadsReceived.get(7);
        assertEquals("invocation_complete", secondInvocationCompletePayload.getString("instrumentation_type"));
        assertEquals(secondRequestDistributedExecutionIndex.toString(), secondInvocationCompletePayload.getString("execution_index"));
        assertEquals(secondRequestVectorClock.toJSONObject().toString(), secondInvocationCompletePayload.getJSONObject("vclock").toString());

        JSONObject firstInvocationCompletePayload = FilibusterServer.payloadsReceived.get(8);
        assertEquals("invocation_complete", firstInvocationCompletePayload.getString("instrumentation_type"));
        assertEquals(firstRequestDistributedExecutionIndex.toString(), firstInvocationCompletePayload.getString("execution_index"));
        assertEquals(firstRequestVectorClock.toJSONObject().toString(), firstInvocationCompletePayload.getJSONObject("vclock").toString());

        JSONObject fourthInvocationPayload = FilibusterServer.payloadsReceived.get(9);
        assertEquals("invocation", fourthInvocationPayload.getString("instrumentation_type"));
        assertEquals(fourthRequestDistributedExecutionIndex.toString(), fourthInvocationPayload.getString("execution_index"));
        assertEquals(fourthRequestVectorClock.toJSONObject().toString(), fourthInvocationPayload.getJSONObject("vclock").toString());

        JSONObject fourthRequestReceivedPayload = FilibusterServer.payloadsReceived.get(10);
        assertEquals("request_received", fourthRequestReceivedPayload.getString("instrumentation_type"));
        assertEquals(fourthRequestDistributedExecutionIndex.toString(), fourthRequestReceivedPayload.getString("execution_index"));

        JSONObject fourthInvocationCompletePayload = FilibusterServer.payloadsReceived.get(11);
        assertEquals("invocation_complete", fourthInvocationCompletePayload.getString("instrumentation_type"));
        assertEquals(fourthRequestDistributedExecutionIndex.toString(), fourthInvocationCompletePayload.getString("execution_index"));
        assertEquals(fourthRequestVectorClock.toJSONObject().toString(), fourthInvocationCompletePayload.getJSONObject("vclock").toString());
    }

    @Test
    @DisplayName("Test hello server external POST route (with Filibuster.)")
    public void testExternalPostWithFilibuster() {
        // Get remote resource.
        String baseURI = "http://" + Networking.getHost("hello") + ":" + Networking.getPort("hello") + "/";
        WebClient webClient = TestHelper.getTestWebClient(baseURI);
        RequestHeaders getHeaders = RequestHeaders.of(HttpMethod.GET, "/external-post", HttpHeaderNames.ACCEPT, "application/json");
        AggregatedHttpResponse response = webClient.execute(getHeaders).aggregate().join();

        // Get headers and verify a 200 response.
        ResponseHeaders headers = response.headers();
        String statusCode = headers.get(HttpHeaderNames.STATUS);
        assertEquals("200", statusCode);

        // Assemble execution index.
        DistributedExecutionIndex requestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        requestDistributedExecutionIndex.push("some-random-location-1337");
        requestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-102f766bb1dc5da807c4d20fbc6d9bfdd8347df2-dca7a9c7a4f34e8ab84b01a0d4ae82e7de540516-2d1ef8410983c2242adbaa0457f03575b8c06b92");

        // Very proper number of Filibuster records.
        assertEquals(2, FilibusterServer.payloadsReceived.size());

        // These are the required Filibuster instrumentation fields for this call.

        JSONObject invocationPayload = FilibusterServer.payloadsReceived.get(0);
        assertEquals("invocation", invocationPayload.getString("instrumentation_type"));
        assertEquals(requestDistributedExecutionIndex.toString(), invocationPayload.getString("execution_index"));

        JSONObject invocationCompletePayload = FilibusterServer.payloadsReceived.get(1);
        assertEquals("invocation_complete", invocationCompletePayload.getString("instrumentation_type"));
        assertEquals(requestDistributedExecutionIndex.toString(), invocationCompletePayload.getString("execution_index"));
    }

    @Test
    @DisplayName("Test hello server external PUT route (with Filibuster.)")
    public void testExternalPutWithFilibuster() {
        // Get remote resource.
        String baseURI = "http://" + Networking.getHost("hello") + ":" + Networking.getPort("hello") + "/";
        WebClient webClient = TestHelper.getTestWebClient(baseURI);
        RequestHeaders getHeaders = RequestHeaders.of(HttpMethod.GET, "/external-put", HttpHeaderNames.ACCEPT, "application/json");
        AggregatedHttpResponse response = webClient.execute(getHeaders).aggregate().join();

        // Get headers and verify a 200 response.
        ResponseHeaders headers = response.headers();
        String statusCode = headers.get(HttpHeaderNames.STATUS);
        assertEquals("200", statusCode);

        // Assemble execution index.
        DistributedExecutionIndex requestDistributedExecutionIndex = createNewDistributedExecutionIndex();
        requestDistributedExecutionIndex.push("some-random-location-1337");
        requestDistributedExecutionIndex.push("V1-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d-602a8b352203c37ab065a38dcef581db609159ff-e9914cf744758f75a543979daca6ad499c5574b5-a547a5665dc2aca5823133e2152f4333fb9a36cc");

        // Very proper number of Filibuster records.
        assertEquals(2, FilibusterServer.payloadsReceived.size());

        // These are the required Filibuster instrumentation fields for this call.

        JSONObject invocationPayload = FilibusterServer.payloadsReceived.get(0);
        assertEquals("invocation", invocationPayload.getString("instrumentation_type"));
        assertEquals(requestDistributedExecutionIndex.toString(), invocationPayload.getString("execution_index"));

        JSONObject invocationCompletePayload = FilibusterServer.payloadsReceived.get(1);
        assertEquals("invocation_complete", invocationCompletePayload.getString("instrumentation_type"));
        assertEquals(requestDistributedExecutionIndex.toString(), invocationCompletePayload.getString("execution_index"));
    }
}


