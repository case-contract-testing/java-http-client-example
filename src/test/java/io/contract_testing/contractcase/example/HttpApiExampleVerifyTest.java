package io.contract_testing.contractcase.example;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;

import io.contract_testing.contractcase.ContractCaseConfig;
import io.contract_testing.contractcase.ContractVerifier;
import io.contract_testing.contractcase.PublishType;
import io.contract_testing.contractcase.TestErrorResponseFunction;
import io.contract_testing.contractcase.TestResponseFunction;
import io.contract_testing.contractcase.Trigger;
import io.contract_testing.contractcase.TriggerGroup;
import io.contract_testing.contractcase.TriggerGroups;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class HttpApiExampleVerifyTest {

  private static final ContractVerifier contract = new ContractVerifier(ContractCaseConfig.ContractCaseConfigBuilder.aContractCaseConfig()
      .providerName("http request provider")
      .publish(PublishType.NEVER)
      .contractDir("./packages/contract-case-jest/case-contracts")
      .build());

  Trigger<String> getHealth = (Map<String, Object> config) -> {
    try {
      return new ApiClient((String) config.get("baseUrl")).getHealth();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  };

  Trigger<User> getUserFromConfig = (config) -> {
    try {
      return new ApiClient((String) config.get("baseUrl"))
          .getUser(((Map<String, String>) config.get("variables")).get("userId"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  };

  Trigger<User> getUserByQuery = (config) -> {
    try {
      return new ApiClient((String) config.get("baseUrl"))
          .getUserQuery(((Map<String, String>) config.get("variables")).get("userId"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  };

  Trigger<User> getUser123 = (config) -> {
    try {
      return new ApiClient((String) config.get("baseUrl"))
          .getUser("123");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  };
  private final Map<String, TestResponseFunction<User>> userSuccessTests = Map.of(
      "a (200) response with body an object shaped like {userId: {{userId}}}",
      (s) -> {
        // TODO: get this from config
        assertThat(s.userId(), is("123"));
      }
  );
  ;
  private final Map<String, TestErrorResponseFunction> userErrorTests = Map.of(
      "a (404) response without a body",
      (e) -> {
        assertThat(e.getMessage(), is("User not found"));
      }
  );
  ;

  @Test
  public void testVerify() throws InterruptedException {
    contract.runVerification(ContractCaseConfig.ContractCaseConfigBuilder.aContractCaseConfig()
        //  .logLevel(LogLevel.MAINTAINER_DEBUG)
        .printResults(true)
        .throwOnFail(true)
        .triggers(new TriggerGroups().addTriggerGroup(
                new TriggerGroup<>(
                    "an http \"GET\" request to \"/health\" with the following headers an object shaped like {accept: \"application/json\"} without a body",
                    getHealth
                    ,
                    Map.of(
                        "a (200) response with body an object shaped like {status: \"up\"}",
                        (String result) -> {
                          assertThat(result, is("up"));
                        }
                    ),
                    new HashMap<>()
                ))
            .addTriggerGroup(
                new TriggerGroup<>(
                    "an http \"GET\" request to \"/health\" without a body",
                    getHealth,
                    Map.of(
                        "a (200) response with body an object shaped like {status: <any string>}",
                        (String result) -> {
                          assertThat(result, isA(String.class));
                        }
                    ),
                    Map.of("a (httpStatus 4XX | 5XX) response without a body", (e) -> {
                          assertThat(e.getMessage(), is("The server is not ready"));
                        },
                        "a (503) response with body an object shaped like {status: \"down\"}",
                        (e) -> {
                          assertThat(e.getMessage(), is("The server is not ready"));
                        }
                    )
                )
            )
            .addTriggerGroup(
                new TriggerGroup<>("an http \"GET\" request to \"/users/{{userId}}\" without a body",
                    getUserFromConfig,
                    userSuccessTests, Map.of()
                ))
            .addTriggerGroup(
                new TriggerGroup<>("an http \"GET\" request to \"/users/123\" without a body",
                    getUser123, Map.of(), userErrorTests
                )
            )
            .addTriggerGroup(new TriggerGroup<>(
                "an http \"GET\" request to \"/users\"?id={{userId}} without a body",
                getUserByQuery,
                userSuccessTests,
                userErrorTests
            )))
        .build());
  }

}
