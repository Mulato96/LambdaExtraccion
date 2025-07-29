package co.org.ccb.lambda.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.org.ccb.lambda.handler.model.dto.ExtractionRequestDTO;
import co.org.ccb.lambda.handler.service.IDocumentExtractionService;

class LambdaHandlerTest {

	private IDocumentExtractionService documentExtractionService;
	private LambdaHandler lambdaHandler;
	private Context mockContext;

	@BeforeEach
	void setUp() {
		documentExtractionService = mock(IDocumentExtractionService.class);
		lambdaHandler = new LambdaHandler(documentExtractionService);

		mockContext = mock(Context.class);
		LambdaLogger mockLogger = mock(LambdaLogger.class);
		when(mockContext.getLogger()).thenReturn(mockLogger);
	}

	@Test
	void handleRequest_validRequest_returnsSuccessResponse() throws Exception {
		// Arrange
		ExtractionRequestDTO requestDTO = new ExtractionRequestDTO();
		requestDTO.setQuantityRecords(5);
		requestDTO.setYears(List.of(2020, 2021));
		requestDTO.setTypesCodes(List.of(1L, 2L));

		String body = new ObjectMapper().writeValueAsString(requestDTO);
		APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent().withBody(body);

		when(documentExtractionService.extractDocuments(any())).thenReturn("Extracción completada");

		// Act
		APIGatewayProxyResponseEvent response = lambdaHandler.handleRequest(requestEvent, mockContext);

		// Assert
		assertEquals(200, response.getStatusCode());
		assertTrue(response.getBody().contains("Extracción completada"));
		verify(documentExtractionService).extractDocuments(any());
	}

	@Test
	void handleRequest_missingQuantityRecords_returnsError() throws Exception {
		// Arrange
		ExtractionRequestDTO requestDTO = new ExtractionRequestDTO();
		String body = new ObjectMapper().writeValueAsString(requestDTO);
		APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent().withBody(body);

		// Act
		APIGatewayProxyResponseEvent response = lambdaHandler.handleRequest(requestEvent, mockContext);

		// Assert
		assertEquals(400, response.getStatusCode());
		assertTrue(response.getBody().contains("quantityRecords"));
	}

	@Test
	void handleRequest_nullBody_returnsError() {
		// Arrange
		APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent().withBody(null);

		// Act
		APIGatewayProxyResponseEvent response = lambdaHandler.handleRequest(requestEvent, mockContext);

		// Assert
		assertEquals(400, response.getStatusCode());
		assertTrue(response.getBody().contains("body JSON es obligatorio"));
	}

	@Test
	void handleRequest_invalidJson_returnsError() {
		// Arrange
		APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent().withBody("{invalid json}");

		// Act
		APIGatewayProxyResponseEvent response = lambdaHandler.handleRequest(requestEvent, mockContext);

		// Assert
		assertEquals(400, response.getStatusCode());
		assertTrue(response.getBody().contains("JSON inválido"));
	}

	@Test
	void handleRequest_typesCodesWithNullValue_returnsError() throws Exception {
		// Arrange
		ExtractionRequestDTO requestDTO = new ExtractionRequestDTO();
		requestDTO.setQuantityRecords(5);
		requestDTO.setTypesCodes(new ArrayList<>(Arrays.asList(1L, null, 2L))); // contiene nulo

		String body = new ObjectMapper().writeValueAsString(requestDTO);
		APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent().withBody(body);

		// Act
		APIGatewayProxyResponseEvent response = lambdaHandler.handleRequest(requestEvent, mockContext);

		// Assert
		assertEquals(400, response.getStatusCode());
		assertTrue(response.getBody().contains("typesCodes"));
	}
}
