package io.contract_testing.contractcase.example;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.contract_testing.contractcase.ContractCaseConfig;
import io.contract_testing.contractcase.ContractDefiner;
import io.contract_testing.contractcase.ExampleDefinition;
import io.contract_testing.contractcase.IndividualFailedTestConfig.IndividualFailedTestConfigBuilder;
import io.contract_testing.contractcase.IndividualSuccessTestConfig.IndividualSuccessTestConfigBuilder;
import io.contract_testing.contractcase.LogLevel;
import io.contract_testing.contractcase.PublishType;
import io.contract_testing.contractcase.Trigger;
import io.contract_testing.contractcase.case_example_mock_types.mocks.http.HttpExample;
import io.contract_testing.contractcase.case_example_mock_types.mocks.http.WillSendHttpRequest;
import io.contract_testing.contractcase.case_example_mock_types.states.AnyState;
import io.contract_testing.contractcase.case_example_mock_types.states.InState;
import io.contract_testing.contractcase.case_example_mock_types.states.InStateWithVariables;
import io.contract_testing.contractcase.test_equivalence_matchers.convenience.NamedMatch;
import io.contract_testing.contractcase.test_equivalence_matchers.convenience.ReferenceMatch;
import io.contract_testing.contractcase.test_equivalence_matchers.convenience.StateVariable;
import io.contract_testing.contractcase.test_equivalence_matchers.http.HttpRequest;
import io.contract_testing.contractcase.test_equivalence_matchers.http.HttpRequestExample;
import io.contract_testing.contractcase.test_equivalence_matchers.http.HttpResponse;
import io.contract_testing.contractcase.test_equivalence_matchers.http.HttpResponseExample;
import io.contract_testing.contractcase.test_equivalence_matchers.strings.AnyString;
import io.contract_testing.contractcase.test_equivalence_matchers.strings.StringPrefix;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class HttpApiExampleTest {

  private static final ContractDefiner contract = new ContractDefiner(ContractCaseConfig.ContractCaseConfigBuilder.aContractCaseConfig()
      .providerName("Java Example HTTP Server")
      .consumerName("Java Example HTTP Client")
      .publish(PublishType.NEVER)
      .logLevel(LogLevel.DEBUG)
      .contractDir("temp-contracts")
      .build());

  Trigger<String> getHealth = (Map<String, Object> config) -> {
    try {
      return new ApiClient((String) config.get("baseUrl")).getHealth();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  };

  NamedMatch GET_USER_VIA_QUERY = new NamedMatch(
      "Get user via query",
      new HttpRequest(HttpRequestExample.builder()
          .path("/users")
          .query(Map.of("id", new StateVariable("userId")))
          .method("GET")
          .build())
  );

  NamedMatch GET_USER_VIA_PATH = new NamedMatch(
      "Get user via path",
      new HttpRequest(HttpRequestExample.builder()
          .path(new StringPrefix("/users/", new StateVariable("userId")))
          .method("GET")
          .build())
  );

  @Test
  public void testHealthUp() {
    contract.runExample(
        new ExampleDefinition<>(
            List.of(new InState("Server is up")),
            new WillSendHttpRequest(HttpExample.builder()
                .request(new NamedMatch(
                    "Get health",
                    new HttpRequest(HttpRequestExample.builder()
                        .path("/health")
                        .method("GET")
                        .build())
                ))
                .response(new HttpResponse(HttpResponseExample.builder()
                    .status(200)
                    .body(Map.ofEntries(Map.entry("status", "up")))
                    .build()))
                .build())
        ),
        IndividualSuccessTestConfigBuilder.<String>builder()
            .withProviderName("Java Example HTTP Server")
            .withTrigger(getHealth)
            .withTestResponse(status -> {
              assertThat(status, is("up"));
            })
    );
  }

  @Test
  public void testHealthDown() {
    contract.runExample(
        new ExampleDefinition<>(
            List.of(new InState("Server is down")),
            new WillSendHttpRequest(HttpExample.builder()
                .request(new ReferenceMatch("Get health"))
                .response(new HttpResponse(HttpResponseExample.builder()
                    .status(200)
                    .body(Map.ofEntries(Map.entry("status", "down")))
                    .build()))
                .build())
        ),
        IndividualSuccessTestConfigBuilder.<String>builder()
            .withProviderName("Java Example HTTP Server")
            .withTrigger(getHealth)
            .withTestResponse(status -> {
              assertThat(status, is("down"));
            })
    );
  }

  @Test
  public void testHealthUnavailable() {
    contract.runThrowingExample(
        new ExampleDefinition<>(
            List.of(new InState("Server is broken")),
            new WillSendHttpRequest(HttpExample.builder()
                .request(new ReferenceMatch("Get health"))
                .response(new HttpResponse(HttpResponseExample.builder().status(503).build()))
                .build())
        ),
        IndividualFailedTestConfigBuilder.<String>builder()
            .withProviderName("Java Example HTTP Server")
            .withTrigger(getHealth)
            .withTestErrorResponse(exception -> {
              assertThat(exception.getMessage(), is("The server is not ready"));
            })
    );
  }


  @Test
  public void testGetUserWithPathVariable() {

    contract.runExample(
        new ExampleDefinition<>(
            List.<AnyState>of(
                new InState("Server is up"),
                new InStateWithVariables("A user exists", Map.of("userId", "123"))
            ),
            new WillSendHttpRequest(HttpExample.builder()
                .request(GET_USER_VIA_PATH)
                .response(new HttpResponse(HttpResponseExample.builder()
                    .status(200)
                    .body(Map.ofEntries(
                        Map.entry("name", new AnyString("john smith")),
                        Map.entry("userId", new StateVariable("userId"))
                    ))
                    .build()))
                .build())
        ),
        IndividualSuccessTestConfigBuilder.<User>builder()
            .withProviderName("Java Example HTTP Server")
            .withTrigger((config) -> {
              try {
                return new ApiClient((String) config.get("baseUrl"))
                    .getUser(((Map<String, String>) config.get("variables")).get("userId"));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
            .withTestResponse(user -> {
              assertThat(user.userId(), is("123"));
            })
    );
  }


  @Test
  public void testGetUserWithQueryVariable() {

    contract.runExample(
        new ExampleDefinition<>(
            List.<AnyState>of(
                new InState("Server is up"),
                new InStateWithVariables("A user exists", Map.of("userId", "123"))
            ),
            new WillSendHttpRequest(HttpExample.builder()
                .request(GET_USER_VIA_QUERY)
                .response(new HttpResponse(HttpResponseExample.builder()
                    .status(200)
                    .body(Map.ofEntries(
                        Map.entry("name", new AnyString("john smith")),
                        Map.entry("userId", new StateVariable("userId"))
                    ))
                    .build()))
                .build())
        ),
        IndividualSuccessTestConfigBuilder.<User>builder()
            .withProviderName("Java Example HTTP Server")
            .withTrigger((config) -> {
              try {
                return new ApiClient((String) config.get("baseUrl"))
                    .getUserQuery(((Map<String, String>) config.get("variables")).get("userId"));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
            .withTestResponse(user -> {
              assertThat(user.userId(), is("123"));
            })
    );
  }

  @Test
  public void testGetUserWithQueryVariableWhenUserNotExist() {
    contract.runThrowingExample(
        new ExampleDefinition<>(
            List.<AnyState>of(
                new InState("Server is up"),
                new InStateWithVariables("No users exist", Map.of("userId", "123"))
            ),
            new WillSendHttpRequest(HttpExample.builder()
                .request(GET_USER_VIA_QUERY)
                .response(new HttpResponse(HttpResponseExample.builder()
                    .status(404).build())
                ).build()
            )
        ),
        IndividualFailedTestConfigBuilder.<User>builder()
            .withProviderName("Java Example HTTP Server")
            .withTrigger((config) -> {
              try {
                return new ApiClient((String) config.get("baseUrl"))
                    .getUserQuery(((Map<String, String>) config.get("variables")).get("userId"));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }).withLogLevel(LogLevel.DEEP_MAINTAINER_DEBUG)
            .withTestErrorResponse(exception -> {
              assertThat(exception.getClass(), is(UserNotFoundException.class));
            })
    );
  }


  @AfterAll
  static void after() {
    contract.endRecord();
  }

}