package co.org.ccb.lambda.handler.main;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import co.org.ccb.lambda.handler.LambdaHandler;

public class LambdaLocalTest {

	public static void main(String[] args) {

		String jsonBody = "{" + "\"quantityRecords\": 1," + "\"years\": [2023, 2024]" + "}";

		APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();
		requestEvent.setBody(jsonBody);

		Context context = new Context() {
			@Override
			public String getAwsRequestId() {
				return "local-test-id";
			}

			@Override
			public String getLogGroupName() {
				return "local-test-log-group";
			}

			@Override
			public String getLogStreamName() {
				return "local-test-log-stream";
			}

			@Override
			public String getFunctionName() {
				return "DocumentExtractionLambda";
			}

			@Override
			public String getFunctionVersion() {
				return "1.0";
			}

			@Override
			public String getInvokedFunctionArn() {
				return "arn:aws:lambda:local:test";
			}

			@Override
			public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() {
				return null;
			}

			@Override
			public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() {
				return null;
			}

			@Override
			public int getRemainingTimeInMillis() {
				return 300000;
			}

			@Override
			public int getMemoryLimitInMB() {
				return 512;
			}

			@Override
			public com.amazonaws.services.lambda.runtime.LambdaLogger getLogger() {
				return new com.amazonaws.services.lambda.runtime.LambdaLogger() {
					@Override
					public void log(String message) {
						System.out.println("LOG: " + message);
					}

					@Override
					public void log(byte[] message) {
						System.out.println("LOG: " + new String(message));
					}
				};
			}
		};

		// Ejecutar Lambda localmente
		LambdaHandler handler = new LambdaHandler();
		System.out.println("Invocando Lambda localmente...");

		APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

		System.out.println("Código de respuesta: " + response.getStatusCode());
		System.out.println("Body de respuesta: " + response.getBody());
	}
}
