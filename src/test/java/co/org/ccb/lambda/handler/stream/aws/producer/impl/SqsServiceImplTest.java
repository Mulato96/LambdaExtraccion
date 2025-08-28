package co.org.ccb.lambda.handler.stream.aws.producer.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.org.ccb.lambda.handler.model.dto.SqsMessageDTO;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class SqsServiceImplTest {

	private IParameterRepository parameterRepository;
	private SqsClient mockSqsClient;

	private SqsServiceImpl service;

	@BeforeEach
	void setUp() throws Exception {
		parameterRepository = mock(IParameterRepository.class);
		mockSqsClient = mock(SqsClient.class);

		// Simulamos la respuesta para usar credenciales estáticas
		when(parameterRepository.findValueByTypeState("AUTH_FLAG")).thenReturn("1");

		service = new SqsServiceImpl(parameterRepository);

		// Inyectamos manualmente los campos privados
		setPrivateField(service, "queueUrl", "https://sqs.test.com/mi-cola");
		setPrivateField(service, "region", "us-east-1");
		setPrivateField(service, "accessKey", "FAKE_ACCESS");
		setPrivateField(service, "secretKey", "FAKE_SECRET");
		setPrivateField(service, "typeParameterEnabledAuthSqs", "AUTH_FLAG");
		setPrivateField(service, "sqsClient", mockSqsClient);
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
	void testInit_withIamRoleCredentials() throws Exception {
		when(parameterRepository.findValueByTypeState("AUTH_FLAG")).thenReturn("0");

		SqsServiceImpl serviceIam = new SqsServiceImpl(parameterRepository);

		assertNotNull(getPrivateField(serviceIam, "sqsClient"));
	}

	@Test
	void testInit_withStaticCredentials() throws Exception {
		when(parameterRepository.findValueByTypeState("AUTH_FLAG")).thenReturn("1");

		SqsServiceImpl serviceStatic = new SqsServiceImpl(parameterRepository);

		assertNotNull(getPrivateField(serviceStatic, "sqsClient"));
	}

	// Métodos utilitarios
	private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	private Object getPrivateField(Object target, String fieldName) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(target);
	}
}
