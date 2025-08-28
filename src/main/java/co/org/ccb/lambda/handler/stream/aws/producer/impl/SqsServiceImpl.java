package co.org.ccb.lambda.handler.stream.aws.producer.impl;

import static co.org.ccb.lambda.handler.util.AppPropertiesLoader.get;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

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

	private String region;

	private String accessKey;

	private String secretKey;

	private String typeParameterEnabledAuthSqs;

	private SqsClient sqsClient;

	public SqsServiceImpl(IParameterRepository parameterRepository) {
		this.parameterRepository = parameterRepository;

		this.region = get("aws.region");
		this.accessKey = get("aws.access.key");
		this.secretKey = get("aws.secret.key");
		this.typeParameterEnabledAuthSqs = get("traslado.parameter.type.enabledAuthSqs");

		String enabledAuthSqs = Optional
				.ofNullable(parameterRepository.findValueByTypeState(typeParameterEnabledAuthSqs)).orElse("1");

		boolean useIamRole = "0".equals(enabledAuthSqs);

		System.out.println("Usando credenciales " + (useIamRole ? "de IAM Role" : "estáticas") + " para SQS");

		this.sqsClient = SqsClient.builder().region(Region.of(region))
				.credentialsProvider(useIamRole ? DefaultCredentialsProvider.create()
						: StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
				.build();
	}

	public void sendMessage(SqsMessageDTO sqsMessageDTO) {
		try {
			this.queueUrl = get("aws.sqs.queue.url");

			String envVarName = this.queueUrl.substring(2, this.queueUrl.length() - 1);
			String envValue = System.getenv(envVarName);

			String jsonMessage = new ObjectMapper().writeValueAsString(sqsMessageDTO);
			SendMessageRequest sendMsgRequest = SendMessageRequest.builder().queueUrl(envValue).messageBody(jsonMessage)
					.build();

			sqsClient.sendMessage(sendMsgRequest);

			System.out.println("Mensaje enviado a SQS correctamente: {}" + jsonMessage);
		} catch (Exception e) {
			System.out.println("Error al enviar mensaje a SQS" + e);
		}
	}

	@Override
	public void sendMessageBatch(List<SqsMessageDTO> messages) {
		if (messages == null || messages.isEmpty()) {
			return;
		}

		try {
			this.queueUrl = get("aws.sqs.queue.url");
			String envVarName = this.queueUrl.substring(2, this.queueUrl.length() - 1);
			String queueUrlValue = System.getenv(envVarName);

			// SQS allows up to 10 messages per batch
			int batchSize = 10;
			for (int i = 0; i < messages.size(); i += batchSize) {
				List<SqsMessageDTO> batch = messages.subList(i, Math.min(i + batchSize, messages.size()));

				List<SendMessageBatchRequestEntry> entries = new ArrayList<>();
				for (SqsMessageDTO msg : batch) {
					String jsonMessage = new ObjectMapper().writeValueAsString(msg);
					entries.add(SendMessageBatchRequestEntry.builder()
							.id(UUID.randomUUID().toString()) // Unique ID for each message in the batch
							.messageBody(jsonMessage)
							.build());
				}

				SendMessageBatchRequest batchRequest = SendMessageBatchRequest.builder()
						.queueUrl(queueUrlValue)
						.entries(entries)
						.build();

				sqsClient.sendMessageBatch(batchRequest);
				System.out.println(
						"Lote de " + batch.size() + " mensajes enviado a SQS correctamente.");
			}
		} catch (Exception e) {
			System.out.println("Error al enviar lote de mensajes a SQS: " + e);
		}
	}
}
