package cloud.filibuster.junit.configuration.examples;

import cloud.filibuster.junit.configuration.FilibusterAnalysisConfiguration;
import cloud.filibuster.junit.configuration.FilibusterAnalysisConfigurationFile;
import cloud.filibuster.junit.configuration.FilibusterCustomAnalysisConfigurationFile;
import cloud.filibuster.junit.configuration.examples.byzantine.decoders.ByzantineDecoder;

import java.util.HashMap;
import java.util.Map;

import static cloud.filibuster.instrumentation.Constants.REDIS_MODULE_NAME;

public class RedisSingleGetStringByzantineFaultAnalysisConfigurationFile implements FilibusterAnalysisConfigurationFile {
    private static final FilibusterCustomAnalysisConfigurationFile filibusterCustomAnalysisConfigurationFile;

    private static <T> Map<String, T> createBzyantineFaultMap(T value) {
        Map<String, T> myMap = new HashMap<>();
        myMap.put("value", value);
        return myMap;
    }

    static {
        FilibusterCustomAnalysisConfigurationFile.Builder filibusterCustomAnalysisConfigurationFileBuilder = new FilibusterCustomAnalysisConfigurationFile.Builder();

        FilibusterAnalysisConfiguration.Builder filibusterAnalysisConfigurationBuilderRedisExceptions = new FilibusterAnalysisConfiguration.Builder()
                .name("my_string_get_byzantine_fault")
                .pattern(REDIS_MODULE_NAME + "/(get)\\b");


        // Potentially use junit-quickcheck to generate the possible values -> Would make the tests more "flaky"
        String[] possibleValues = {null, "123", "", "abcd", "-123ABC", "ThisIsATestString"};
        for (String value : possibleValues) {
            filibusterAnalysisConfigurationBuilderRedisExceptions.byzantine("my_string_get_byzantine_fault", createBzyantineFaultMap(value), ByzantineDecoder.STRING);
        }

        filibusterCustomAnalysisConfigurationFileBuilder.analysisConfiguration(filibusterAnalysisConfigurationBuilderRedisExceptions.build());

        filibusterCustomAnalysisConfigurationFile = filibusterCustomAnalysisConfigurationFileBuilder.build();
    }

    @Override
    public FilibusterCustomAnalysisConfigurationFile toFilibusterCustomAnalysisConfigurationFile() {
        return filibusterCustomAnalysisConfigurationFile;
    }
}
