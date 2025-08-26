package co.org.ccb.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.org.ccb.lambda.handler.model.dto.EnrollmentMessageDTO;
import co.org.ccb.lambda.handler.service.IEnrollmentProcessingService;
import co.org.ccb.lambda.handler.service.impl.EnrollmentProcessingServiceImpl;
import co.org.ccb.lambda.handler.config.AwsParameterStoreService;
import co.org.ccb.lambda.handler.config.PersistenceManager;
import co.org.ccb.lambda.handler.repository.sirep.IEnrollmentsRepository;
import co.org.ccb.lambda.handler.repository.sirep.IOnbaseControlRepository;
import co.org.ccb.lambda.handler.repository.sirep.impl.EnrollmentsRepositoryImpl;
import co.org.ccb.lambda.handler.repository.sirep.impl.OnbaseControlRepositoryImpl;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessControlRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessDocumentRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessRepository;
import co.org.ccb.lambda.handler.repository.traslado.impl.IProcessDocumentRepositoryImpl;
import co.org.ccb.lambda.handler.repository.traslado.impl.ParameterRepositoryImpl;
import co.org.ccb.lambda.handler.repository.traslado.impl.ProcessControlRepositoryImpl;
import co.org.ccb.lambda.handler.repository.traslado.impl.ProcessRepositoryImpl;
import co.org.ccb.lambda.handler.stream.aws.producer.ISqsService;
import co.org.ccb.lambda.handler.stream.aws.producer.impl.SqsServiceImpl;
import jakarta.persistence.EntityManager;

public class EnrollmentWorkerHandler implements RequestHandler<SQSEvent, Void> {

    private final IEnrollmentProcessingService enrollmentProcessingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EnrollmentWorkerHandler() {
        // Initialize dependencies, similar to the original LambdaHandler
        AwsParameterStoreService awsService = new AwsParameterStoreService();
		PersistenceManager.init(awsService);

		EntityManager sirepEntityManager = PersistenceManager.getSirepEntityManagerFactory()
				.createEntityManager();
		EntityManager trasladoEntityManager = PersistenceManager.getTrasladoEntityManagerFactory()
				.createEntityManager();

		IEnrollmentsRepository enrollmentsRepository = new EnrollmentsRepositoryImpl(sirepEntityManager);
		IOnbaseControlRepository onbaseControlRepository = new OnbaseControlRepositoryImpl(sirepEntityManager);
		IParameterRepository parameterRepository = new ParameterRepositoryImpl(trasladoEntityManager);
		IProcessControlRepository iProcessControlRepository = new ProcessControlRepositoryImpl(trasladoEntityManager);
		IProcessDocumentRepository processDocumentRepository = new IProcessDocumentRepositoryImpl(trasladoEntityManager);
		IProcessRepository processRepository = new ProcessRepositoryImpl(trasladoEntityManager);

		ISqsService sqsService = new SqsServiceImpl(parameterRepository);

        this.enrollmentProcessingService = new EnrollmentProcessingServiceImpl(
            enrollmentsRepository,
            onbaseControlRepository,
            parameterRepository,
            sqsService,
            sirepEntityManager,
            trasladoEntityManager,
            iProcessControlRepository,
            processDocumentRepository,
            processRepository
        );
    }

    @Override
    public Void handleRequest(SQSEvent sqsEvent, Context context) {
        context.getLogger().log("Iniciando procesamiento de matrículas desde SQS. Número de mensajes: " + sqsEvent.getRecords().size());

        for (SQSEvent.SQSMessage msg : sqsEvent.getRecords()) {
            try {
                String messageBody = msg.getBody();
                context.getLogger().log("Procesando mensaje: " + messageBody);

                EnrollmentMessageDTO enrollmentMessage = objectMapper.readValue(messageBody, EnrollmentMessageDTO.class);

                enrollmentProcessingService.processEnrollment(enrollmentMessage);

                context.getLogger().log("Mensaje procesado exitosamente para la matrícula: " + enrollmentMessage.getEnrollmentNumber());

            } catch (Exception e) {
                context.getLogger().log("Error al procesar mensaje de SQS: " + e.getMessage());
                // Depending on the SQS configuration, the message might be retried or sent to a DLQ.
                // Throwing an exception will cause the batch to fail.
                throw new RuntimeException("Error al procesar mensaje de SQS", e);
            }
        }
        context.getLogger().log("Procesamiento de lote de SQS completado.");
        return null;
    }
}
