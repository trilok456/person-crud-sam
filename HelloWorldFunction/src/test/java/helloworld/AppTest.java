package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AppTest {

  @Test
  public void successfulResponse() {
    // Create a mock APIGatewayProxyRequestEvent
    APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();
    requestEvent.setPath("/hi"); // Set path to test
    requestEvent.setHttpMethod("GET");

    // Mock Context and LambdaLogger
    Context context = Mockito.mock(Context.class);
    LambdaLogger lambdaLogger = Mockito.mock(LambdaLogger.class);

    Mockito.when(context.getLogger()).thenReturn(lambdaLogger);

    // Instantiate App and call handleRequest
    App app = new App();
    APIGatewayProxyResponseEvent result = app.handleRequest(requestEvent, context);

    // Validate the response
    assertNotNull(result);
    assertEquals(200, result.getStatusCode().intValue());
    assertEquals("application/json", result.getHeaders().get("Content-Type"));
    String content = result.getBody();
    assertNotNull(content);

    // Log and validate response content
    System.out.println("Response Body: " + content);
    assertTrue(content.contains("\"message\""));
    assertTrue(content.contains("\"hello world\""));
    assertTrue(content.contains("\"location\""));
  }
}
