package co.org.ccb.lambda.handler.stream.aws.producer.impl;

import static co.org.ccb.lambda.handler.util.AppPropertiesLoader.get;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.org.ccb.lambda.handler.model.dto.EnrollmentMessageDTO;
import co.org.ccb.lambda.handler.model.dto.SqsMessageDTO;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import co.org.ccb.lambda.handler.stream.aws.producer.ISqsService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class SqsServiceImpl implements ISqsService {

	private final IParameterRepository parameterRepository;
	private String queueUrl;
	private String enrollmentQueueUrl;
	private String region;
	private String accessKey;
	private String secretKey;
	private String typeParameterEnabledAuthSqs;
	private SqsClient sqsClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public SqsServiceImpl(IParameterRepository parameterRepository) {
		this.parameterRepository = parameterRepository;

		this.region = get("aws.region");
		this.accessKey = get("aws.access.key");
		this.secretKey = get("aws.secret.key");
		this.typeParameterEnabledAuthSqs = get("traslado.parameter.type.enabledAuthSqs");
		this.enrollmentQueueUrl = get("aws.sqs.enrollment.queue.url");

		String enabledAuthSqs = Optional
				.ofNullable(parameterRepository.findValueByTypeState(typeParameterEnabledAuthSqs)).orElse("1");

		boolean useIamRole = "0".equals(enabledAuthSqs);

		System.out.println("Usando credenciales " + (useIamRole ? "de IAM Role" : "estáticas") + " para SQS");

		this.sqsClient = SqsClient.builder().region(Region.of(region))
				.credentialsProvider(useIamRole ? DefaultCredentialsProvider.create()
						: StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
				.build();
	}

	@Override
	public void sendMessage(SqsMessageDTO sqsMessageDTO) {
		try {
			this.queueUrl = get("aws.sqs.queue.url");
			String envVarName = this.queueUrl.substring(2, this.queueUrl.length() - 1);
			String envValue = System.getenv(envVarName);

			String jsonMessage = objectMapper.writeValueAsString(sqsMessageDTO);
			SendMessageRequest sendMsgRequest = SendMessageRequest.builder().queueUrl(envValue).messageBody(jsonMessage)
					.build();
			sqsClient.sendMessage(sendMsgRequest);
			System.out.println("Mensaje enviado a SQS correctamente: " + jsonMessage);
		} catch (Exception e) {
			System.out.println("Error al enviar mensaje a SQS: " + e.getMessage());
		}
	}

	@Override
	public void sendEnrollmentMessages(List<EnrollmentMessageDTO> messages) {
		if (messages == null || messages.isEmpty()) {
			return;
		}

		String queueUrlValue = getQueueUrl(this.enrollmentQueueUrl);
		if (queueUrlValue == null) {
			System.out.println("Error: La URL de la cola de SQS para matrículas no está configurada.");
			return;
		}

		List<List<EnrollmentMessageDTO>> messageChunks = partitionList(messages, 10);

		for (List<EnrollmentMessageDTO> chunk : messageChunks) {
			try {
				List<SendMessageBatchRequestEntry> entries = chunk.stream()
						.map(this::createBatchEntry)
						.collect(Collectors.toList());

				SendMessageBatchRequest batchRequest = SendMessageBatchRequest.builder()
						.queueUrl(queueUrlValue)
						.entries(entries)
						.build();

				sqsClient.sendMessageBatch(batchRequest);
				System.out.println("Lote de " + chunk.size() + " mensajes de matrícula enviado a SQS.");

			} catch (Exception e) {
				System.out.println("Error al enviar lote de mensajes de matrícula a SQS: " + e.getMessage());
			}
		}
	}

	private SendMessageBatchRequestEntry createBatchEntry(EnrollmentMessageDTO message) {
		try {
			String jsonMessage = objectMapper.writeValueAsString(message);
			return SendMessageBatchRequestEntry.builder()
					.id(UUID.randomUUID().toString())
					.messageBody(jsonMessage)
					.build();
		} catch (JsonProcessingException e) {
			System.out.println("Error al serializar el mensaje de matrícula a JSON: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private String getQueueUrl(String queueUrlConfig) {
		if (queueUrlConfig.startsWith("${") && queueUrlConfig.endsWith("}")) {
			String envVarName = queueUrlConfig.substring(2, queueUrlConfig.length() - 1);
			return System.getenv(envVarName);
		}
		return queueUrlConfig;
	}

	private static <T> List<List<T>> partitionList(List<T> list, int size) {
		List<List<T>> partitions = new ArrayList<>();
		for (int i = 0; i < list.size(); i += size) {
			partitions.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
		}
		return partitions;
	}
}
