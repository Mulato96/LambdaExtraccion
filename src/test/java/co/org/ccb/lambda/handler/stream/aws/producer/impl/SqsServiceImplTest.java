package co.org.ccb.lambda.handler.stream.aws.producer.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.org.ccb.lambda.handler.model.dto.SqsMessageDTO;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SqsServiceImplTest {

	private IParameterRepository parameterRepository;
	private SqsClient mockSqsClient;
	private SqsServiceImpl service;

	@BeforeEach
	void setUp() throws Exception {
		parameterRepository = mock(IParameterRepository.class);
		mockSqsClient = mock(SqsClient.class);

		// The service will be created with properties from application.properties
		service = new SqsServiceImpl(parameterRepository);

		// We only inject the mock client via reflection
		setPrivateField(service, "sqsClient", mockSqsClient);

        // Set environment variable for queue URL for testing purposes
        System.setProperty("QUEUE_URL_ENV_VAR", "https://sqs.test.com/mi-cola");
	}

	@Test
	void testSendMessage_success() {
		SqsMessageDTO message = new SqsMessageDTO("test123");
		when(mockSqsClient.sendMessage(any(SendMessageRequest.class)))
				.thenReturn(SendMessageResponse.builder().messageId("123").build());

		service.sendMessage(message);

		verify(mockSqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
	}

	@Test
	void testSendMessageBatch_success() {
		List<SqsMessageDTO> messages = List.of(new SqsMessageDTO("1"), new SqsMessageDTO("2"));
		when(mockSqsClient.sendMessageBatch(any(SendMessageBatchRequest.class)))
				.thenReturn(SendMessageBatchResponse.builder().build());

		service.sendMessageBatch(messages);

		verify(mockSqsClient, times(1)).sendMessageBatch(any(SendMessageBatchRequest.class));
	}

	@Test
	void testSendMessageBatch_partitionsCorrectly() {
		List<SqsMessageDTO> messages = IntStream.range(0, 15)
				.mapToObj(i -> new SqsMessageDTO(String.valueOf(i)))
				.collect(Collectors.toList());

		when(mockSqsClient.sendMessageBatch(any(SendMessageBatchRequest.class)))
				.thenReturn(SendMessageBatchResponse.builder().build());

		service.sendMessageBatch(messages);

		verify(mockSqsClient, times(2)).sendMessageBatch(any(SendMessageBatchRequest.class));
	}

	@Test
	void testInit_withIamRoleCredentials() {
		when(parameterRepository.findValueByTypeState(anyString())).thenReturn("0");
		SqsServiceImpl serviceIam = new SqsServiceImpl(parameterRepository);
		assertNotNull(serviceIam);
	}

	@Test
	void testInit_withStaticCredentials() {
		when(parameterRepository.findValueByTypeState(anyString())).thenReturn("1");
		SqsServiceImpl serviceStatic = new SqsServiceImpl(parameterRepository);
		assertNotNull(serviceStatic);
	}

	private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
