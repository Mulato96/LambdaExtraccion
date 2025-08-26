package co.org.ccb.lambda.handler.service.impl;

import static co.org.ccb.lambda.handler.util.AppPropertiesLoader.get;
import static co.org.ccb.lambda.handler.util.DocumentType.CERTIFICATE;
import static co.org.ccb.lambda.handler.util.DocumentType.CERTIFICATE_BOOK;

import co.org.ccb.lambda.handler.model.dto.EnrollmentMessageDTO;
import co.org.ccb.lambda.handler.model.dto.SqsMessageDTO;
import co.org.ccb.lambda.handler.model.entity.sirep.CertificateInfoEntity;
import co.org.ccb.lambda.handler.model.entity.sirep.EnrollmentsEntity;
import co.org.ccb.lambda.handler.model.entity.sirep.OnbaseControlEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ParameterEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessControlEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessDocumentEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessEntity;
import co.org.ccb.lambda.handler.repository.sirep.IEnrollmentsRepository;
import co.org.ccb.lambda.handler.repository.sirep.IOnbaseControlRepository;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessControlRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessDocumentRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessRepository;
import co.org.ccb.lambda.handler.service.IEnrollmentProcessingService;
import co.org.ccb.lambda.handler.stream.aws.producer.ISqsService;
import co.org.ccb.lambda.handler.util.Constantes;
import co.org.ccb.lambda.handler.util.DocumentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EnrollmentProcessingServiceImpl implements IEnrollmentProcessingService {

    private final String valueStatePendiente;
    private final String valueStateIncompleto;
    private final String valueStateError;

    private final IEnrollmentsRepository enrollmentsRepository;
    private final IOnbaseControlRepository onbaseControlRepository;
    private final IParameterRepository parameterRepository;
    private final ISqsService sqsService;
    private final EntityManager trasladoEntityManager;
    private final IProcessControlRepository processControlRepository;
    private final IProcessDocumentRepository processDocumentRepository;
    private final IProcessRepository processRepository;

    public EnrollmentProcessingServiceImpl(
            IEnrollmentsRepository enrollmentsRepository,
            IOnbaseControlRepository onbaseControlRepository,
            IParameterRepository parameterRepository,
            ISqsService sqsService,
            EntityManager sirepEntityManager,
            EntityManager trasladoEntityManager,
            IProcessControlRepository processControlRepository,
            IProcessDocumentRepository processDocumentRepository,
            IProcessRepository processRepository) {
        this.enrollmentsRepository = enrollmentsRepository;
        this.onbaseControlRepository = onbaseControlRepository;
        this.parameterRepository = parameterRepository;
        this.sqsService = sqsService;
        this.trasladoEntityManager = trasladoEntityManager;
        this.processControlRepository = processControlRepository;
        this.processDocumentRepository = processDocumentRepository;
        this.processRepository = processRepository;

        this.valueStatePendiente = get("traslado.parameter.value.statePendiente");
        this.valueStateIncompleto = get("traslado.parameter.value.stateIncompleto");
        this.valueStateError = get("traslado.parameter.value.stateError");
    }

    @Override
    public void processEnrollment(EnrollmentMessageDTO enrollmentMessage) {
        EntityTransaction transaction = trasladoEntityManager.getTransaction();
        try {
            transaction.begin();

            EnrollmentsEntity enrollment = enrollmentsRepository.findByEnrollmentNumber(enrollmentMessage.getEnrollmentNumber())
                    .orElseThrow(() -> new RuntimeException("Matrícula no encontrada: " + enrollmentMessage.getEnrollmentNumber()));

            ProcessEntity processEntity = processRepository.findById(enrollmentMessage.getProcessId())
                    .orElseThrow(() -> new RuntimeException("Proceso no encontrado: " + enrollmentMessage.getProcessId()));

            List<ParameterEntity> parameters = parameterRepository.findParameters();

            Optional<Integer> idStatePendienteOpt = getStatusId(parameters, valueStatePendiente);
            Integer estadoPendienteId = idStatePendienteOpt.orElseThrow(() -> new RuntimeException("ID de estado pendiente no encontrado"));

            ProcessControlEntity processControlEntity = buildProcessControlEntity(processEntity,
                    enrollment, estadoPendienteId);
            trasladoEntityManager.persist(processControlEntity);

            processDocumentsForEnrollment(enrollment, processControlEntity, parameters);

            transaction.commit();

            sendMessageSQS(processControlEntity.getId().toString());

        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            System.out.println("Error procesando matrícula " + enrollmentMessage.getEnrollmentNumber() + ": " + e.getMessage());
            throw new RuntimeException("Error procesando matrícula", e);
        }
    }

    private void processDocumentsForEnrollment(EnrollmentsEntity enrollment,
                                               ProcessControlEntity processControlEntity, List<ParameterEntity> lstStates) {
        System.out.println("Funcion processDocumentsForEnrollment ejecutandose correctamente para la matrícula: " + enrollment.getNumMatricula());
        try {
            Integer estadoPendienteId = getStatusId(lstStates, valueStatePendiente).orElse(0);

            List<OnbaseControlEntity> documents = onbaseControlRepository.findDocumentsByEnrollmentNumber(List.of(enrollment.getNumMatricula()));
            List<ProcessDocumentEntity> processDocuments = processDocumentRepository.findByEnrollmentNumber(List.of(enrollment.getNumMatricula()));
            Map<String, ProcessDocumentEntity> processDocumentMap = processDocuments.stream().collect(
                    Collectors.toMap(ProcessDocumentEntity::getUniqueDocumentNumber, Function.identity(), (p1, p2) -> p1));

            System.out.println("Consulta de documentos para matricula " + enrollment.getNumMatricula()
                    + " exitosa = " + documents.size() + " documentos");

            saveCertificateForEnrollment(processControlEntity, estadoPendienteId.toString(),
                    enrollment, processDocumentMap, lstStates);

            for (OnbaseControlEntity document : documents) {
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
        System.out.println("Mensaje enviado a SQS: " + message);
    }

    private String determineDocumentType(OnbaseControlEntity document) {
        return switch (document.getCtrDocumento()) {
            case 1 -> DocumentType.KARDEX.name();
            case 2 -> DocumentType.FORM.name();
            case 3 -> DocumentType.OTHER.name();
            default -> DocumentType.OTHER.name();
        };
    }
}
