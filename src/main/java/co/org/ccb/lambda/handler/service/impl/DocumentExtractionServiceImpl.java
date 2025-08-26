package co.org.ccb.lambda.handler.service.impl;

import static co.org.ccb.lambda.handler.util.AppPropertiesLoader.get;
import static co.org.ccb.lambda.handler.util.DocumentType.CERTIFICATE;
import static co.org.ccb.lambda.handler.util.DocumentType.CERTIFICATE_BOOK;

import static co.org.ccb.lambda.handler.util.AppPropertiesLoader.get;
import co.org.ccb.lambda.handler.repository.traslado.IProcessControlRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import co.org.ccb.lambda.handler.model.dto.EnrollmentMessageDTO;
import co.org.ccb.lambda.handler.model.dto.ExtractionRequestDTO;
import co.org.ccb.lambda.handler.model.entity.sirep.EnrollmentsEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ParameterEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessControlEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessEntity;
import co.org.ccb.lambda.handler.repository.sirep.IEnrollmentsRepository;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import co.org.ccb.lambda.handler.service.IDocumentExtractionService;
import co.org.ccb.lambda.handler.stream.aws.producer.ISqsService;
import co.org.ccb.lambda.handler.util.Constantes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

public class DocumentExtractionServiceImpl implements IDocumentExtractionService {

	private String typeParameterState;
	private String typeParameterLimitProcess;
	private String valueStatePendiente;
	private String valueStateTransladado;
	private String valueStateCancelado;
	private String valueStateError;
	private String valueStateIncompleto;

	private final IEnrollmentsRepository enrollmentsRepository;
	private final IParameterRepository parameterRepository;
	private final ISqsService sqsService;
	private final EntityManager trasladoEntityManager;
	private final IProcessControlRepository processControlRepository;

	public DocumentExtractionServiceImpl(IEnrollmentsRepository enrollmentsRepository,
			IParameterRepository parameterRepository,
			ISqsService sqsService, EntityManager sirepEntityManager, EntityManager trasladoEntityManager,
			IProcessControlRepository processControlRepository,
			IProcessRepository processRepository) {

		this.processControlRepository = processControlRepository;
		this.typeParameterState = get("traslado.parameter.type.state");
		this.typeParameterLimitProcess = get("traslado.parameter.type.limitmaxprocess");
		this.valueStatePendiente = get("traslado.parameter.value.statePendiente");
		this.valueStateTransladado = get("traslado.parameter.value.stateTransladado");
		this.valueStateCancelado = get("traslado.parameter.value.stateCancelado");
		this.valueStateError = get("traslado.parameter.value.stateError");
		this.valueStateIncompleto = get("traslado.parameter.value.stateIncompleto");

		this.enrollmentsRepository = enrollmentsRepository;
		this.parameterRepository = parameterRepository;
		this.sqsService = sqsService;
		this.trasladoEntityManager = trasladoEntityManager;
	}

	public String extractDocuments(ExtractionRequestDTO request) {
		System.out.println("Iniciando proceso de despacho de documentos");
		EntityTransaction transaction = trasladoEntityManager.getTransaction();
		try {
			System.out.println("Se consultan parametros de configuración");
			List<ParameterEntity> parameters = parameterRepository.findParameters();
			Map<String, String> parametersMap = buildMapParameters(parameters);

			if (parameters.isEmpty()) {
				throw new RuntimeException("No se encontraron parámetros de configuración");
			}

			String limitProcess = parametersMap.get(this.typeParameterLimitProcess);
			System.out.println("Limite de matriculas permitidas para procesar " + limitProcess);

			if (limitProcess == null) {
				throw new RuntimeException("Parámetro LIMIT no encontrado");
			}
			if (request.getQuantityRecords() > Integer.parseInt(limitProcess)) {
				return "El parámetro quantityRecords excede el límite permitido: " + limitProcess;
			}

			List<ParameterEntity> lstStates = parameters.stream().filter(param -> List.of(
					this.valueStatePendiente, this.valueStateTransladado, this.valueStateCancelado,
					this.valueStateError,
					this.valueStateIncompleto).contains(param.getValue()))
					.toList();

			List<EnrollmentsEntity> enrollments = getEnrollmentsFromSIREP(request);
			System.out.println("Consulta de matriculas exitosa = " + enrollments.size() + " matriculas");

			if (enrollments.isEmpty()) {
				return "No se encontraron matrículas para procesar";
			}

			List<EnrollmentsEntity> enrollmentsEntities = filterEnrollmentProcessed(enrollments,
					lstStates);

			if (enrollmentsEntities.isEmpty()) {
				System.out.println("No se encontraron matrículas para procesar");
				return "No se encontraron matrículas para procesar";
			}

			transaction.begin();
			ProcessEntity processEntity = createProcessEntity(trasladoEntityManager, request);
			transaction.commit();

			List<EnrollmentMessageDTO> enrollmentMessages = enrollmentsEntities.stream()
					.map(enrollment -> new EnrollmentMessageDTO(enrollment.getNumMatricula(), processEntity.getId()))
					.collect(Collectors.toList());

			sqsService.sendEnrollmentMessages(enrollmentMessages);

			System.out.println(
					"Se han enviado " + enrollmentMessages.size() + " matrículas a la cola para su procesamiento.");

			return "Proceso de despacho completado exitosamente con el ID: " + processEntity.getId()
					+ ". Se han enviado " + enrollmentMessages.size() + " matrículas para ser procesadas.";
		} catch (Exception e) {
			if (transaction.isActive())
				transaction.rollback();
			System.out.println("Error durante la extracción de documentos " + e);
			throw new RuntimeException("Error durante la extracción de documentos", e);
		}
	}

	private List<EnrollmentsEntity> filterEnrollmentProcessed(List<EnrollmentsEntity> enrollments,
			List<ParameterEntity> lstStates) {
		Optional<Integer> idStateError = lstStates.stream()
				.filter(param -> param.getValue().equals(valueStateError)).map(ParameterEntity::getId)
				.findFirst();

		Integer estadoErrorId = idStateError.orElse(5);
		Map<String, ProcessControlEntity> enrollmentFromRsd = getEnrollmentFromInErrorRsd(enrollments);
		List<EnrollmentsEntity> enrollmentsEntitiesFilter = new ArrayList<>();
		enrollments.forEach(enrollment -> {
			if (enrollmentFromRsd.containsKey(enrollment.getNumMatricula()) && !Objects.equals(
					enrollmentFromRsd.get(enrollment.getNumMatricula()).getProcessStatusId(),
					estadoErrorId)) {

				System.out.println(
						"Matricula Ya registrada en RDS: [" + enrollment.getNumMatricula() + "] | Estado: ["
						+ enrollmentFromRsd.get(enrollment.getNumMatricula()).getProcessStatusId() + "]");
			} else {
				enrollmentsEntitiesFilter.add(enrollment);
			}
		});
		return enrollmentsEntitiesFilter;
	}

	public ProcessEntity createProcessEntity(EntityManager em, ExtractionRequestDTO request) {

		System.out.println("Funcion createProcessEntity ejecutandose correctamente");
		LocalDateTime localDateTime = convertDateToLocalDateTime(new Date());
		ProcessEntity processEntity = new ProcessEntity();
		processEntity.setEnrollmentCount(request.getQuantityRecords());
		processEntity.setCreatedBy(Constantes.USER_CREATE);
		processEntity.setCreationDate(localDateTime);
		em.persist(processEntity);
		System.out.println("Insercion en la tabla procesos exitosa, pendiente commit." + " ID: " + processEntity.getId());
		return processEntity;

	}

	public List<EnrollmentsEntity> getEnrollmentsFromSIREP(ExtractionRequestDTO request) {
		System.out.println("Funcion getEnrollmentsFromSIREP ejecutandose correctamente");
		try {
			return enrollmentsRepository.findByFilters(request.getYears(),
					request.getTypesCodes(), request.getQuantityRecords());
		} catch (Exception e) {
			System.out.println("Error obteniendo matrículas desde SIREP: " + e);
			throw new RuntimeException("Error obteniendo matrículas desde SIREP", e);
		}

	}

	private Map<String, ProcessControlEntity> getEnrollmentFromInErrorRsd(
			List<EnrollmentsEntity> enrollments) {
		Set<String> collected = enrollments.stream()
				.map(EnrollmentsEntity::getNumMatricula)
				.collect(Collectors.toSet());

		return processControlRepository.findByEnrollNumber(collected)
				.stream()
				.collect(Collectors.toMap(ProcessControlEntity::getEnrollmentNumber, Function.identity(),
						(p1, p2) -> p1.getCreationDate().isAfter(p2.getCreationDate()) ? p1 : p2));
	}

	private static LocalDateTime convertDateToLocalDateTime(Date dateConvert) {
		if (dateConvert instanceof java.sql.Date date) {
			return date.toLocalDate().atStartOfDay();
		} else {
			return dateConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
		}
	}

	private Map<String, String> buildMapParameters(List<ParameterEntity> parameters) {
		Map<String, String> parametersMap = new HashMap<>();

		parameters.stream()
				.forEach(param -> parametersMap.put(param.getType(), param.getValue()));

		return parametersMap;
	}

}
