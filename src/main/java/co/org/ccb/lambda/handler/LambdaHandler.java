package co.org.ccb.lambda.handler;

import co.org.ccb.lambda.handler.repository.traslado.IProcessControlRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessRepository;
import co.org.ccb.lambda.handler.repository.traslado.impl.IProcessDocumentRepositoryImpl;
import co.org.ccb.lambda.handler.repository.traslado.impl.ProcessControlRepositoryImpl;
import co.org.ccb.lambda.handler.repository.traslado.impl.ProcessRepositoryImpl;

import java.io.IOException;
import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.org.ccb.lambda.handler.config.AwsParameterStoreService;
import co.org.ccb.lambda.handler.config.PersistenceManager;
import co.org.ccb.lambda.handler.model.dto.ExtractionRequestDTO;
import co.org.ccb.lambda.handler.repository.sirep.IEnrollmentsRepository;
import co.org.ccb.lambda.handler.repository.sirep.IOnbaseControlRepository;
import co.org.ccb.lambda.handler.repository.sirep.impl.EnrollmentsRepositoryImpl;
import co.org.ccb.lambda.handler.repository.sirep.impl.OnbaseControlRepositoryImpl;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import co.org.ccb.lambda.handler.repository.traslado.impl.ParameterRepositoryImpl;
import co.org.ccb.lambda.handler.service.IDocumentExtractionService;
import co.org.ccb.lambda.handler.service.impl.DocumentExtractionServiceImpl;
import co.org.ccb.lambda.handler.stream.aws.producer.ISqsService;
import co.org.ccb.lambda.handler.stream.aws.producer.impl.SqsServiceImpl;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private final IDocumentExtractionService documentExtractionService;

	public LambdaHandler() {

		AwsParameterStoreService awsService = new AwsParameterStoreService();
		PersistenceManager.init(awsService);

		EntityManager sirepEntityManager = PersistenceManager.getSirepEntityManagerFactory()
				.createEntityManager();
		EntityManager trasladoEntityManager = PersistenceManager.getTrasladoEntityManagerFactory()
				.createEntityManager();

		IEnrollmentsRepository enrollmentsRepository = new EnrollmentsRepositoryImpl(
				sirepEntityManager);
		IOnbaseControlRepository onbaseControlRepository = new OnbaseControlRepositoryImpl(
				sirepEntityManager);
		IParameterRepository parameterRepository = new ParameterRepositoryImpl(trasladoEntityManager);
		IProcessControlRepository iProcessControlRepository = new ProcessControlRepositoryImpl(
				trasladoEntityManager);
		IProcessDocumentRepositoryImpl processDocumentRepository = new IProcessDocumentRepositoryImpl(
				trasladoEntityManager);
		IProcessRepository processRepository = new ProcessRepositoryImpl(trasladoEntityManager);
		
		ISqsService sqsService = new SqsServiceImpl(parameterRepository);

		this.documentExtractionService = new DocumentExtractionServiceImpl(enrollmentsRepository,
				parameterRepository, sqsService, sirepEntityManager,
				trasladoEntityManager, iProcessControlRepository, processRepository);
	}

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
		context.getLogger().log("Iniciando extracción Lambda");

		try {
			String body = input.getBody();
			if (body == null || body.isEmpty()) {
				return buildErrorResponse("El body JSON es obligatorio.");
			}

			ExtractionRequestDTO requestDTO = new ObjectMapper().readValue(body, ExtractionRequestDTO.class);

			Integer quantityRecords = requestDTO.getQuantityRecords();
			if (quantityRecords == null) {
				return buildErrorResponse("El parámetro 'quantityRecords' es obligatorio.");
			}
			if (quantityRecords <= 0) {
				return buildErrorResponse("El parámetro 'quantityRecords' debe ser un número mayor a 0.");
			}

			List<Integer> years = requestDTO.getYears();
			if (years != null && !years.stream().allMatch(year -> year != null)) {
				return buildErrorResponse("El arreglo 'years' contiene valores no numéricos o nulos.");
			}

			List<Long> typesCodes = requestDTO.getTypesCodes();
			if (typesCodes != null && !typesCodes.stream().allMatch(code -> code != null)) {
				return buildErrorResponse("El arreglo 'typesCodes' contiene valores no numéricos o nulos.");
			}		
					
			System.out.println("Extracción con años: {}, tipos: {} y cantidad: {}" + requestDTO.getYears()
					+ requestDTO.getTypesCodes() + requestDTO.getQuantityRecords());

			String response = documentExtractionService.extractDocuments(requestDTO);
			return buildSuccessResponse(response);

		} catch (IOException e) {
			context.getLogger().log("Error al parsear JSON: " + e.getMessage());
			return buildErrorResponse("JSON inválido: " + e.getMessage());
		} catch (Exception e) {
			context.getLogger().log("Error general: " + e.getMessage());
			return buildErrorResponse("Error al procesar la extracción.");
		}

	}

	private APIGatewayProxyResponseEvent buildSuccessResponse(String message) {
		return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("{\"message\": \"" + message + "\"}");
	}

	private APIGatewayProxyResponseEvent buildErrorResponse(String message) {
		return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("{\"error\": \"" + message + "\"}");
	}
}
