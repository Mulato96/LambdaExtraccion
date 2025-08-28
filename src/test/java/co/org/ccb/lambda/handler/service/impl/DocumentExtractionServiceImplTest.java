package co.org.ccb.lambda.handler.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.org.ccb.lambda.handler.model.entity.sirep.CertificateInfoEntity;
import co.org.ccb.lambda.handler.repository.traslado.IProcessControlRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessDocumentRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.org.ccb.lambda.handler.model.dto.ExtractionRequestDTO;
import co.org.ccb.lambda.handler.model.entity.sirep.EnrollmentsEntity;
import co.org.ccb.lambda.handler.model.entity.sirep.OnbaseControlEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ParameterEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessControlEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessDocumentEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessEntity;
import co.org.ccb.lambda.handler.repository.sirep.IEnrollmentsRepository;
import co.org.ccb.lambda.handler.repository.sirep.IOnbaseControlRepository;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import co.org.ccb.lambda.handler.stream.aws.producer.ISqsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

class DocumentExtractionServiceImplTest {

	private IEnrollmentsRepository enrollmentsRepository;

	private IOnbaseControlRepository onbaseControlRepository;
	private IProcessControlRepository processControlRepository;
	private IProcessDocumentRepository processDocumentRepository;
	private IProcessRepository processRepository;

	private IParameterRepository parameterRepository;

	private ISqsService sqsService;

	private EntityManager sirepEntityManager;

	private EntityManager trasladoEntityManager;

	private EntityTransaction sirepTransaction;

	private EntityTransaction trasladoTransaction;

	private DocumentExtractionServiceImpl service;

	@BeforeEach
	void setUp() {
		enrollmentsRepository = mock(IEnrollmentsRepository.class);
		onbaseControlRepository = mock(IOnbaseControlRepository.class);
		processControlRepository = mock(IProcessControlRepository.class);
		processDocumentRepository = mock(IProcessDocumentRepository.class);
		parameterRepository = mock(IParameterRepository.class);
		processRepository = mock(IProcessRepository.class);

		sqsService = mock(ISqsService.class);
		sirepEntityManager = mock(EntityManager.class);
		trasladoEntityManager = mock(EntityManager.class);
		sirepTransaction = mock(EntityTransaction.class);
		trasladoTransaction = mock(EntityTransaction.class);

		when(sirepEntityManager.getTransaction()).thenReturn(sirepTransaction);
		when(trasladoEntityManager.getTransaction()).thenReturn(trasladoTransaction);

		service = new DocumentExtractionServiceImpl(enrollmentsRepository, onbaseControlRepository, parameterRepository,
				sqsService, sirepEntityManager, trasladoEntityManager, processControlRepository, processDocumentRepository,
				processRepository);
	}

	@Test
	public void shouldReturnNoEnrollmentsMessage_whenNoEnrollmentsFound() {
		// Arrange
		ExtractionRequestDTO request = new ExtractionRequestDTO();
		request.setQuantityRecords(5); // cantidad válida
		request.setYears(Collections.singletonList(2024));
		request.setTypesCodes(Collections.singletonList(2901L));

		when(parameterRepository.findValueByTypeState(anyString())).thenReturn("10"); // límite permitido

		// Simular lista vacía de matrículas
		when(enrollmentsRepository.findByFilters(anyList(), anyList(), anyInt())).thenReturn(Collections.emptyList());

		// Simular persistencia del proceso sin error
		doAnswer(invocation -> {
			ProcessEntity processEntity = invocation.getArgument(0);
			processEntity.setId(123);
			return null;
		}).when(trasladoEntityManager).persist(any(ProcessEntity.class));

		// Act
		String result = service.extractDocuments(request);

		// Assert
		assertEquals("No se encontraron matrículas para procesar", result);

		verifyNoInteractions(sqsService); // no se debe enviar nada a SQS
	}

	@Test
	void testExtractDocuments_successfulFlow() {
		ExtractionRequestDTO request = new ExtractionRequestDTO();
		request.setQuantityRecords(5);

		when(parameterRepository.findValueByTypeState(anyString())).thenReturn("10");

		// Matrícula simulada
		EnrollmentsEntity enrollment = new EnrollmentsEntity();
		enrollment.setNumMatricula("12345");
		enrollment.setIdOrganizacion("1");
		enrollment.setCtrCertActiva(1);
		enrollment.setNumReciboActiva("9876");

		when(enrollmentsRepository.findByFilters(any(), any(), anyInt())).thenReturn(List.of(enrollment));

		// Simular parámetros válidos
		ParameterEntity parameter = mock(ParameterEntity.class);
		when(parameter.getValue()).thenReturn("PENDIENTE");
		when(parameter.getId()).thenReturn(1);

		when(parameterRepository.findIdsByTypeState("STATE")).thenReturn(List.of(parameter));
		System.out.println("Mock de parámetro: " + parameter);

		// Proceso simulado con persistencia manual
		doAnswer(invocation -> {
			ProcessEntity p = invocation.getArgument(0);
			setPrivateField(p, "id", 100);
			return null;
		}).when(trasladoEntityManager).persist(any(ProcessEntity.class));

		// Control simulado
		doAnswer(invocation -> {
			ProcessControlEntity pc = invocation.getArgument(0);
			setPrivateField(pc, "id", 200);
			return null;
		}).when(trasladoEntityManager).persist(any(ProcessControlEntity.class));

		// Documentos asociados
		OnbaseControlEntity doc = new OnbaseControlEntity();
		doc.setNumMatricula("12345");
		doc.setCtrDocumento(1);
		doc.setHandle(12345);
		doc.setNombreArchivo("archivo.pdf");

		when(onbaseControlRepository.findDocumentsByEnrollmentNumber("12345")).thenReturn(List.of(doc));
		when(enrollmentsRepository.findCertificateInfo("12345", Set.of("9876", "9877")))
				.thenReturn(List.of(new CertificateInfoEntity(), new CertificateInfoEntity()));

		// Act
		String result = service.extractDocuments(request);

		// Assert
		assertNotNull(result);
		assertTrue(result.contains("Proceso completado exitosamente con el ID: 100"));
		verify(trasladoTransaction).begin();
	}

	@Test
	void testCreateProcessEntity_success() {
		// Arrange
		ExtractionRequestDTO request = new ExtractionRequestDTO();
		request.setQuantityRecords(5);
		request.setYears(List.of(2024));
		request.setTypesCodes(List.of(2901L));

		// Simulamos persistencia y asignación de ID
		doAnswer(invocation -> {
			ProcessEntity p = invocation.getArgument(0);
			setPrivateField(p, "id", 999);
			return null;
		}).when(trasladoEntityManager).persist(any(ProcessEntity.class));

		// Act
		ProcessEntity result = service.createProcessEntity(trasladoEntityManager, request);

		// Assert
		assertNotNull(result);
		assertEquals(999, result.getId());
		verify(trasladoEntityManager, times(1)).persist(any(ProcessEntity.class));
	}

	@Test
	void testGetEnrollmentsFromSIREP_success() {
		// Arrange
		ExtractionRequestDTO request = new ExtractionRequestDTO();
		request.setQuantityRecords(10);
		request.setYears(List.of(2023));
		request.setTypesCodes(List.of(2901L));

		// Mock de matrícula
		EnrollmentsEntity mockEnrollment = new EnrollmentsEntity();
		mockEnrollment.setNumMatricula("12345");

		when(enrollmentsRepository.findByFilters(anyList(), anyList(), anyInt())).thenReturn(List.of(mockEnrollment));

		// Act
		List<EnrollmentsEntity> result = service.getEnrollmentsFromSIREP(request);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("12345", result.get(0).getNumMatricula());
		verify(enrollmentsRepository).findByFilters(List.of(2023), List.of(2901L), 10);
	}

	@Test
	void testProcessEnrollments_success() {
		// Arrange
		setPrivateField(service, "valueStatePendiente", "PENDIENTE");

		EnrollmentsEntity enrollment = new EnrollmentsEntity();
		enrollment.setNumMatricula("12345");
		enrollment.setIdOrganizacion("1");
		enrollment.setCtrCertActiva(1);
		enrollment.setNumReciboActiva("9876");

		List<EnrollmentsEntity> enrollments = List.of(enrollment);

		ProcessEntity mockProcess = new ProcessEntity();
		setPrivateField(mockProcess, "id", 100);

		ParameterEntity stateParam = new ParameterEntity();
		setPrivateField(stateParam, "id", 1);
		setPrivateField(stateParam, "value", "PENDIENTE");
		List<ParameterEntity> parameters = List.of(stateParam);

		ExtractionRequestDTO request = new ExtractionRequestDTO();
		request.setYears(List.of(2023));
		request.setTypesCodes(List.of(2901L));
		request.setQuantityRecords(5);

		List<String> messagesToSend = new ArrayList<>();

		OnbaseControlEntity doc = new OnbaseControlEntity();
		doc.setNumMatricula("12345");
		doc.setCtrDocumento(1);
		doc.setHandle(12345);
		doc.setNombreArchivo("archivo.pdf");

		when(onbaseControlRepository.findDocumentsByEnrollmentNumber("12345")).thenReturn(List.of(doc));

		when(enrollmentsRepository.findCertificateInfo("12345", Set.of("9876", "9877")))
				.thenReturn(List.of(new CertificateInfoEntity(), new CertificateInfoEntity()));

		// Muy importante: simulamos que el persist asigna un ID
		doAnswer(invocation -> {
			ProcessControlEntity pc = invocation.getArgument(0);
			setPrivateField(pc, "id", 1); // ID mockeado
			return null;
		}).when(trasladoEntityManager).persist(any(ProcessControlEntity.class));

		// Act
		service.processEnrollments(trasladoEntityManager, enrollments, parameters, mockProcess, request,
				messagesToSend);

		// Assert
		verify(trasladoEntityManager, times(1)).persist(any(ProcessControlEntity.class));
		verify(onbaseControlRepository, times(1)).findDocumentsByEnrollmentNumber("12345");

		// Validación del ID agregado a mensajes
		assertEquals(1, messagesToSend.size());
		assertEquals("1", messagesToSend.get(0));
	}

	@Test
	void testProcessDocumentsForEnrollment_withCertificateAndDocs() {
		// Arrange
		EnrollmentsEntity enrollment = new EnrollmentsEntity();
		enrollment.setNumMatricula("12345");
		enrollment.setCtrCertActiva(1);
		enrollment.setNumReciboActiva("9876");

		ProcessControlEntity processControl = new ProcessControlEntity();
		setPrivateField(processControl, "id", 999); // mock ID

		OnbaseControlEntity doc = new OnbaseControlEntity();
		doc.setNumMatricula("12345");
		doc.setCtrDocumento(1);
		doc.setHandle(12345);
		doc.setNombreArchivo("archivo.pdf");

		// Mock certificado
		Object[] certificateInfo = new Object[] { "COD-VERIF-001" };

		// Mock transacción
		EntityTransaction mockTx = mock(EntityTransaction.class);
		when(sirepEntityManager.getTransaction()).thenReturn(mockTx);

		// Mock repositorios
		when(onbaseControlRepository.findDocumentsByEnrollmentNumber("12345")).thenReturn(List.of(doc));
		when(enrollmentsRepository.findCertificateInfo("12345", Set.of("9876", "9877")))
				.thenReturn(List.of(new CertificateInfoEntity(), new CertificateInfoEntity()));

		// Act
		service.processDocumentsForEnrollment(enrollment, processControl, List.of());

		// Assert
		verify(trasladoEntityManager, atLeastOnce()).persist(any(ProcessDocumentEntity.class));
		verify(onbaseControlRepository, times(1)).findDocumentsByEnrollmentNumber("12345");
	}

	private void setPrivateField(Object target, String fieldName, Object value) {
		try {
			Field field;
			Class<?> current = target.getClass();
			while (current != null) {
				try {
					field = current.getDeclaredField(fieldName);
					field.setAccessible(true);
					field.set(target, value);
					return;
				} catch (NoSuchFieldException e) {
					current = current.getSuperclass();
				}
			}
			throw new RuntimeException("No se pudo setear el campo: " + fieldName);
		} catch (Exception e) {
			throw new RuntimeException("No se pudo setear el campo: " + fieldName, e);
		}
	}

}
