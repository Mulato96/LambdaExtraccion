package co.org.ccb.lambda.handler.service.impl;

import static co.org.ccb.lambda.handler.util.AppPropertiesLoader.get;
import static co.org.ccb.lambda.handler.util.DocumentType.CERTIFICATE;
import static co.org.ccb.lambda.handler.util.DocumentType.CERTIFICATE_BOOK;

import co.org.ccb.lambda.handler.model.entity.sirep.CertificateInfoEntity;
import co.org.ccb.lambda.handler.repository.traslado.IProcessControlRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessDocumentRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import co.org.ccb.lambda.handler.model.dto.ExtractionRequestDTO;
import co.org.ccb.lambda.handler.model.dto.SqsMessageDTO;
import co.org.ccb.lambda.handler.model.entity.sirep.EnrollmentsEntity;
import co.org.ccb.lambda.handler.model.entity.sirep.OnbaseControlEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ParameterEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessControlEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessDocumentEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessEntity;
import co.org.ccb.lambda.handler.repository.sirep.IEnrollmentsRepository;
import co.org.ccb.lambda.handler.repository.sirep.IOnbaseControlRepository;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import co.org.ccb.lambda.handler.service.IDocumentExtractionService;
import co.org.ccb.lambda.handler.stream.aws.producer.ISqsService;
import co.org.ccb.lambda.handler.util.Constantes;
import co.org.ccb.lambda.handler.util.DocumentType;
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
	private final IOnbaseControlRepository onbaseControlRepository;
	private final IParameterRepository parameterRepository;
	private final ISqsService sqsService;
	private final EntityManager sirepEntityManager;
	private final EntityManager trasladoEntityManager;
	private final IProcessControlRepository processControlRepository;
	private final IProcessDocumentRepository processDocumentRepository;
	private final IProcessRepository processRepository;

	public DocumentExtractionServiceImpl(IEnrollmentsRepository enrollmentsRepository,
			IOnbaseControlRepository onbaseControlRepository, IParameterRepository parameterRepository,
			ISqsService sqsService, EntityManager sirepEntityManager, EntityManager trasladoEntityManager,
			IProcessControlRepository processControlRepository,
			IProcessDocumentRepository processDocumentRepository,
			IProcessRepository processRepository) {

		this.processControlRepository = processControlRepository;
		this.processDocumentRepository = processDocumentRepository;
		this.processRepository = processRepository;
		this.typeParameterState = get("traslado.parameter.type.state");
		this.typeParameterLimitProcess = get("traslado.parameter.type.limitmaxprocess");
		this.valueStatePendiente = get("traslado.parameter.value.statePendiente");
		this.valueStateTransladado = get("traslado.parameter.value.stateTransladado");
		this.valueStateCancelado = get("traslado.parameter.value.stateCancelado");
		this.valueStateError = get("traslado.parameter.value.stateError");
		this.valueStateIncompleto = get("traslado.parameter.value.stateIncompleto");

		this.enrollmentsRepository = enrollmentsRepository;
		this.onbaseControlRepository = onbaseControlRepository;
		this.parameterRepository = parameterRepository;
		this.sqsService = sqsService;
		this.sirepEntityManager = sirepEntityManager;
		this.trasladoEntityManager = trasladoEntityManager;
	}

	public String extractDocuments(ExtractionRequestDTO request) {
		System.out.println("Iniciando proceso de extracción de documentos");
		EntityTransaction transaction = trasladoEntityManager.getTransaction();
		List<String> messagesToSend = new ArrayList<>();
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

			long startTime = System.currentTimeMillis();
			System.out.println("Tiempo de inicio de procesamiento: " + startTime + " ms");

			transaction.begin();
			ProcessEntity processEntity = createProcessEntity(trasladoEntityManager, request);
			processEnrollments(trasladoEntityManager, enrollmentsEntities, lstStates, processEntity, request,
					messagesToSend);
			transaction.commit();

			long endTime = System.currentTimeMillis();
			System.out.println("Tiempo de finalización de procesamiento: " + endTime + " ms");
			System.out.println("Tiempo total de procesamiento: " + (endTime - startTime) / 1000);

			System.out.println(
					"Commit exitoso de transaccion global, numero de mensajes para la cola = " + messagesToSend.size());

			messagesToSend.forEach(this::sendMessageSQS);
			return "Proceso completado exitosamente con el ID: " + processEntity.getId();
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
		processEntity.setId(processRepository.getId());
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

	public void processEnrollments(EntityManager em, List<EnrollmentsEntity> enrollments,
			List<ParameterEntity> lstStates, ProcessEntity processEntity, ExtractionRequestDTO request,
			List<String> messagesToSend) {

		System.out.println("Funcion processEnrollments ejecutandose correctamente");
		Optional<Integer> idStatePendiente = lstStates.stream()
				.filter(param -> param.getValue().equals(valueStatePendiente)).map(ParameterEntity::getId)
				.findFirst();

		Integer estadoPendienteId = idStatePendiente.orElse(0);
		for (EnrollmentsEntity enrollment : enrollments) {
			ProcessControlEntity processControlEntity = buildProcessControlEntity(processEntity,
					enrollment, estadoPendienteId);
			em.persist(processControlEntity);
			processDocumentsForEnrollment(enrollment, processControlEntity, lstStates);
			messagesToSend.add(processControlEntity.getId().toString());

			System.out.println("Se agrego el id de proceso control a el array para enviar a la cola.");
		}
	}

	private ProcessControlEntity buildProcessControlEntity(ProcessEntity processEntity,
			EnrollmentsEntity enrollment, Integer estadoPendienteId) {
		ProcessControlEntity processControlEntity = new ProcessControlEntity();
		processControlEntity.setProcess(processEntity);
		processControlEntity.setEnrollmentNumber(enrollment.getNumMatricula());
		processControlEntity.setRecordType(Short.valueOf(enrollment.getIdOrganizacion()));
		processControlEntity.setProcessStatusId(estadoPendienteId);
		processControlEntity.setCreationDate(convertDateToLocalDateTime(new Date()));
		processControlEntity.setCreatedBy(Constantes.USER_CREATE);
		return processControlEntity;
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

	public void processDocumentsForEnrollment(EnrollmentsEntity finalEnrollment,
			ProcessControlEntity processControlEntity, List<ParameterEntity> lstStates) {

		System.out.println("Funcion processDocumentsForEnrollment ejecutandose correctamente");
		try {
			Integer estadoPendienteId = getStatusId(lstStates, valueStatePendiente).orElse(0);
			List<OnbaseControlEntity> documentsByEnrollmentNumber = onbaseControlRepository
					.findDocumentsByEnrollmentNumber(finalEnrollment.getNumMatricula());
			Map<String, ProcessDocumentEntity> processDocumentMap = processDocumentRepository.findByEnrollmentNumber(
					finalEnrollment.getNumMatricula()).stream().collect(
							Collectors.toMap(ProcessDocumentEntity::getUniqueDocumentNumber, Function.identity()));

			System.out.println("Consulta de documentos para matricula" + finalEnrollment.getNumMatricula()
					+ " exitosa = " + documentsByEnrollmentNumber.size() + " documentos");

			saveCertificateForEnrollment(processControlEntity, estadoPendienteId.toString(),
					finalEnrollment, processDocumentMap, lstStates);
			for (OnbaseControlEntity document : documentsByEnrollmentNumber) {
				buildAndCreateProcessDocumentEntity(document, estadoPendienteId.toString(),
						processControlEntity,
						processDocumentMap, lstStates);

				System.out.println("Insercion en la tabla proceso_documentos exitosa, pendiente commit.");
			}
		} catch (Exception e) {
			System.out.println("Error procesando documentos = " + e);
			throw new RuntimeException("Error procesando documentos", e);
		}
	}

	private Optional<Integer> getStatusId(List<ParameterEntity> lstStates, String paramStatus) {
		return lstStates.stream()
				.filter(param -> param.getValue().equals(paramStatus)).map(ParameterEntity::getId)
				.findFirst();
	}

	private void buildAndCreateProcessDocumentEntity(OnbaseControlEntity document,
			String idStatePendiente, ProcessControlEntity processControlEntity,
			Map<String, ProcessDocumentEntity> processDocumentMap, List<ParameterEntity> lstStates) {
		Integer idStateIncomplete = getStatusId(lstStates, valueStateIncompleto).orElse(31);
		Integer idStateError = getStatusId(lstStates, valueStateError).orElse(5);

		if (processDocumentMap.containsKey(document.getHandle().toString())) {
			ProcessDocumentEntity processDocumentExist = processDocumentMap.get(
					document.getHandle().toString());
			if (idStateError.toString().equals(processDocumentExist.getStatusId()) ||
					idStateIncomplete.toString().equals(processDocumentExist.getStatusId())) {
				processDocumentExist.setProcessControl(processControlEntity);
				processDocumentExist.setStatusId(idStatePendiente);
				trasladoEntityManager.merge(processDocumentExist);
			}
		} else {
			ProcessDocumentEntity processDocumentEntity = new ProcessDocumentEntity();
			processDocumentEntity.setProcessControl(processControlEntity);
			processDocumentEntity.setEnrollmentNumber(document.getNumMatricula());
			processDocumentEntity.setUniqueDocumentNumber(document.getHandle().toString());
			processDocumentEntity.setStatusId(idStatePendiente);
			processDocumentEntity.setDocumentType(determineDocumentType(document));
			processDocumentEntity.setManagementDate(
					document.getFecProcesado() != null ? convertDateToLocalDateTime(document.getFecProcesado())
							: LocalDateTime.now());
			processDocumentEntity.setFinalName(
					document.getNombreArchivo() != null ? document.getNombreArchivo()
							: "TEST-" + determineDocumentType(document) + document.getHandle());
			processDocumentEntity.setBookId(document.getIdLibro());
			processDocumentEntity.setRecordNumber(document.getNumRegistro());
			processDocumentEntity.setCreationDate(convertDateToLocalDateTime(new Date()));
			processDocumentEntity.setCreatedBy(Constantes.USER_CREATE);
			trasladoEntityManager.persist(processDocumentEntity);
		}
	}

	private void saveCertificateForEnrollment(ProcessControlEntity processControlEntity,
			String idStatePendiente, EnrollmentsEntity finalEnrollment,
			Map<String, ProcessDocumentEntity> processDocumentMap, List<ParameterEntity> lstStates) {
		Set<String> numberCertificateSet = getCertificateNumbers(finalEnrollment);

		System.out.println(
				"Esta matricula contiene [" + numberCertificateSet.size() + "] certificados.");

		if (!numberCertificateSet.isEmpty()) {
			List<CertificateInfoEntity> certificateInfo = enrollmentsRepository.findCertificateInfo(
					finalEnrollment.getNumMatricula(),
					numberCertificateSet);

			System.out.println(
					"Se encontro [" + certificateInfo.size() + "] certificados para la matricula "
							+ finalEnrollment.getNumMatricula());

			Integer idStateIncomplete = getStatusId(lstStates, valueStateIncompleto).orElse(31);
			Integer idStateError = getStatusId(lstStates, valueStateError).orElse(5);
			certificateInfo.forEach(certificateInfoEntity -> {
				System.out.println(
						"Certificado encontrado: [" + certificateInfoEntity.getNumRecibo() + "]");
				if (processDocumentMap.containsKey(certificateInfoEntity.getCodVerificacion())) {
					ProcessDocumentEntity processDocument = processDocumentMap.get(
							certificateInfoEntity.getCodVerificacion());
					if (idStateError.toString().equals(processDocument.getStatusId()) ||
							idStateIncomplete.toString().equals(processDocument.getStatusId())) {
						processDocument.setStatusId(idStatePendiente);
						processDocument.setProcessControl(processControlEntity);
						trasladoEntityManager.merge(processDocument);
					}
				} else {
					trasladoEntityManager.persist(
							buildProcessDocumentCertificate(processControlEntity, certificateInfoEntity,
									idStatePendiente, finalEnrollment));
				}
			});
		}
	}

	private ProcessDocumentEntity buildProcessDocumentCertificate(
			ProcessControlEntity processControlEntity, CertificateInfoEntity certificateInfoEntity,
			String idStatePendiente, EnrollmentsEntity finalEnrollment) {
		ProcessDocumentEntity certificateDocument = new ProcessDocumentEntity();
		boolean isCertificateBook = finalEnrollment.tieneCertificadoLibro(
				certificateInfoEntity.getNumRecibo());
		certificateDocument.setProcessControl(processControlEntity);
		certificateDocument.setEnrollmentNumber(finalEnrollment.getNumMatricula());
		certificateDocument.setUniqueDocumentNumber(
				certificateInfoEntity.getCodVerificacion() != null
						? certificateInfoEntity.getCodVerificacion()
						: "0");
		certificateDocument.setStatusId(idStatePendiente);
		certificateDocument.setDocumentType(
				isCertificateBook ? CERTIFICATE_BOOK.name() : CERTIFICATE.name());
		certificateDocument.setManagementDate(convertDateToLocalDateTime(
				isCertificateBook ? finalEnrollment.getFecCertLibro() : finalEnrollment.getFecCertActiva()));
		certificateDocument.setCreationDate(LocalDateTime.now());
		certificateDocument.setCreatedBy(Constantes.USER_CREATE);
		certificateDocument.setBookId("");
		certificateDocument.setFinalName("");
		certificateDocument.setRecordNumber("");
		return certificateDocument;
	}

	private Set<String> getCertificateNumbers(EnrollmentsEntity finalEnrollment) {
		Set<String> numberCertificateSet = new HashSet<>();
		if (finalEnrollment.getCtrCertActiva() != null && finalEnrollment.getCtrCertActiva() == 1
				&& finalEnrollment.getNumReciboActiva() != null) {
			numberCertificateSet.add(finalEnrollment.getNumReciboActiva());
		}
		if (finalEnrollment.getCtrCertLibro() != null && finalEnrollment.getCtrCertLibro() == 1 &&
				finalEnrollment.getCtrLibros() != null && finalEnrollment.getCtrLibros() == 1
				&& finalEnrollment.getNumReciboLibro() != null) {
			numberCertificateSet.add(finalEnrollment.getNumReciboLibro());
		}
		numberCertificateSet.forEach(
				value -> System.out.println(
						"Certificados de la matricula: MAT-" + finalEnrollment.getNumMatricula() + " | CERT- "
								+ value));

		return numberCertificateSet;
	}

	private static LocalDateTime convertDateToLocalDateTime(Date dateConvert) {
		if (dateConvert instanceof java.sql.Date date) {
			return date.toLocalDate().atStartOfDay();
		} else {
			return dateConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
		}
	}

	private void sendMessageSQS(String registrationId) {
		System.out.println("Funcion sendMessageSQS ejecutandose correctamente");
		SqsMessageDTO message = new SqsMessageDTO(registrationId);

		sqsService.sendMessage(message);
		System.out.println("Mensaje enviado a SQS: {}" + message);
	}

	private String determineDocumentType(OnbaseControlEntity document) {
		return switch (document.getCtrDocumento()) {
			case 1 -> DocumentType.KARDEX.name();
			case 2 -> DocumentType.FORM.name();
			case 3 -> DocumentType.OTHER.name();
			default -> DocumentType.OTHER.name();
		};
	}

	private Map<String, String> buildMapParameters(List<ParameterEntity> parameters) {
		Map<String, String> parametersMap = new HashMap<>();

		parameters.stream()
				.forEach(param -> parametersMap.put(param.getType(), param.getValue()));

		return parametersMap;
	}

}
