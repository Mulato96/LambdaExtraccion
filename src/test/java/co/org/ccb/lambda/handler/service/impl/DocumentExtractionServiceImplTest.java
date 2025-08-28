package co.org.ccb.lambda.handler.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import co.org.ccb.lambda.handler.model.dto.ExtractionRequestDTO;
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
import co.org.ccb.lambda.handler.stream.aws.producer.ISqsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
		trasladoTransaction = mock(EntityTransaction.class);

		when(trasladoEntityManager.getTransaction()).thenReturn(trasladoTransaction);

		service = new DocumentExtractionServiceImpl(enrollmentsRepository, onbaseControlRepository, parameterRepository,
				sqsService, sirepEntityManager, trasladoEntityManager, processControlRepository, processDocumentRepository,
				processRepository);
	}

	private List<ParameterEntity> getMockParameters() {
		List<ParameterEntity> parameters = new ArrayList<>();

		ParameterEntity p1 = mock(ParameterEntity.class);
		when(p1.getType()).thenReturn("traslado.parameter.type.limitmaxprocess");
		when(p1.getValue()).thenReturn("100");

		ParameterEntity p2 = mock(ParameterEntity.class);
		when(p2.getId()).thenReturn(1);
		when(p2.getType()).thenReturn("traslado.parameter.type.state");
		when(p2.getValue()).thenReturn("PENDIENTE");

		parameters.add(p1);
		parameters.add(p2);

		return parameters;
	}

	@Test
	public void shouldReturnNoEnrollmentsMessage_whenNoEnrollmentsFound() {
		ExtractionRequestDTO request = new ExtractionRequestDTO();
		request.setQuantityRecords(5);

		when(parameterRepository.findParameters()).thenReturn(getMockParameters());
		when(enrollmentsRepository.findByFilters(any(), any(), anyInt())).thenReturn(Collections.emptyList());

		String result = service.extractDocuments(request);

		assertEquals("No se encontraron matrículas para procesar", result);
		verifyNoInteractions(sqsService);
	}

	@Test
	void testExtractDocuments_successfulFlow() {
		ExtractionRequestDTO request = new ExtractionRequestDTO();
		request.setQuantityRecords(1);

		when(parameterRepository.findParameters()).thenReturn(getMockParameters());

		EnrollmentsEntity enrollment = new EnrollmentsEntity();
		enrollment.setNumMatricula("12345");
		enrollment.setIdOrganizacion("1");
		enrollment.setCtrCertActiva(1);
		enrollment.setNumReciboActiva("9876");
		enrollment.setCtrLibros(0);
		enrollment.setFecCertActiva(new Date());

		when(enrollmentsRepository.findByFilters(any(), any(), anyInt())).thenReturn(List.of(enrollment));

		when(processControlRepository.findByEnrollNumber(anySet())).thenReturn(Collections.emptyList());

		doAnswer(invocation -> {
			ProcessEntity p = invocation.getArgument(0);
			setPrivateField(p, "id", 100);
			return null;
		}).when(trasladoEntityManager).persist(any(ProcessEntity.class));

		doAnswer(invocation -> {
			ProcessControlEntity pc = invocation.getArgument(0);
			setPrivateField(pc, "id", 200);
			return null;
		}).when(trasladoEntityManager).persist(any(ProcessControlEntity.class));

		OnbaseControlEntity doc = new OnbaseControlEntity();
		doc.setNumMatricula("12345");
		doc.setCtrDocumento(1);
		doc.setHandle(12345);
		doc.setNombreArchivo("archivo.pdf");
		when(onbaseControlRepository.findDocumentsByEnrollmentNumber(anyList())).thenReturn(List.of(doc));

		CertificateInfoEntity certInfo = new CertificateInfoEntity();
		certInfo.setNumMatricula("12345");
		certInfo.setNumRecibo("9876");
		certInfo.setCodVerificacion("COD123");
		when(enrollmentsRepository.findAllCertificateInfoByEnrollmentNumbers(anyList())).thenReturn(List.of(certInfo));

		String result = service.extractDocuments(request);

		assertNotNull(result);
		assertTrue(result.contains("Proceso completado exitosamente con el ID: 100"));
		verify(trasladoTransaction).begin();
		verify(sqsService).sendMessageBatch(anyList());
	}

	@Test
	void testCreateProcessEntity_success() {
		ExtractionRequestDTO request = new ExtractionRequestDTO();
		request.setQuantityRecords(5);

		doAnswer(invocation -> {
			ProcessEntity p = invocation.getArgument(0);
			setPrivateField(p, "id", 999);
			return null;
		}).when(trasladoEntityManager).persist(any(ProcessEntity.class));

		ProcessEntity result = service.createProcessEntity(trasladoEntityManager, request);

		assertNotNull(result);
		assertEquals(999, result.getId());
		verify(trasladoEntityManager, times(1)).persist(any(ProcessEntity.class));
	}

	@Test
	void testGetEnrollmentsFromSIREP_success() {
		ExtractionRequestDTO request = new ExtractionRequestDTO();
		request.setQuantityRecords(10);
		request.setYears(List.of(2023));
		request.setTypesCodes(List.of(2901L));

		EnrollmentsEntity mockEnrollment = new EnrollmentsEntity();
		mockEnrollment.setNumMatricula("12345");
		when(enrollmentsRepository.findByFilters(anyList(), anyList(), anyInt())).thenReturn(List.of(mockEnrollment));

		List<EnrollmentsEntity> result = service.getEnrollmentsFromSIREP(request);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("12345", result.get(0).getNumMatricula());
		verify(enrollmentsRepository).findByFilters(List.of(2023), List.of(2901L), 10);
	}

	@Test
	void testProcessEnrollments_success() {
		EnrollmentsEntity enrollment = new EnrollmentsEntity();
		enrollment.setNumMatricula("12345");
		enrollment.setIdOrganizacion("1");
		enrollment.setCtrCertActiva(1);
		enrollment.setNumReciboActiva("9876");
		enrollment.setCtrLibros(0);
		enrollment.setFecCertActiva(new Date());

		List<EnrollmentsEntity> enrollments = List.of(enrollment);

		ProcessEntity mockProcess = new ProcessEntity();
		setPrivateField(mockProcess, "id", 100);

		List<ParameterEntity> parameters = getMockParameters();

		Map<String, List<CertificateInfoEntity>> certificatesByEnrollment = Map.of("12345", List.of(new CertificateInfoEntity()));
		List<String> messagesToSend = new ArrayList<>();

		when(onbaseControlRepository.findDocumentsByEnrollmentNumber(anyList())).thenReturn(Collections.emptyList());
		when(processDocumentRepository.findByEnrollmentNumber(anyList())).thenReturn(Collections.emptyList());

		doAnswer(invocation -> {
			ProcessControlEntity pc = invocation.getArgument(0);
			setPrivateField(pc, "id", 1);
			return null;
		}).when(trasladoEntityManager).persist(any(ProcessControlEntity.class));

		service.processEnrollments(trasladoEntityManager, enrollments, parameters, mockProcess, certificatesByEnrollment, messagesToSend);

		verify(trasladoEntityManager, times(1)).persist(any(ProcessControlEntity.class));
		assertEquals(1, messagesToSend.size());
		assertEquals("1", messagesToSend.get(0));
	}

	@Test
	void testProcessDocumentsForEnrollment_withCertificateAndDocs() {
		EnrollmentsEntity enrollment = new EnrollmentsEntity();
		enrollment.setNumMatricula("12345");
		enrollment.setCtrLibros(0);
		enrollment.setCtrCertActiva(1);
		enrollment.setFecCertActiva(new Date());

		ProcessControlEntity processControl = new ProcessControlEntity();
		setPrivateField(processControl, "id", 999);

		OnbaseControlEntity doc = new OnbaseControlEntity();
		doc.setNumMatricula("12345");
		doc.setCtrDocumento(1);
		doc.setHandle(12345);
		doc.setNombreArchivo("archivo.pdf");
		List<OnbaseControlEntity> documents = List.of(doc);

		CertificateInfoEntity certInfo = new CertificateInfoEntity();
		certInfo.setNumMatricula("12345");
		List<CertificateInfoEntity> certificateInfo = List.of(certInfo);

		service.processDocumentsForEnrollment(enrollment, processControl, getMockParameters(), documents, Collections.emptyList(), certificateInfo);

		verify(trasladoEntityManager, atLeastOnce()).persist(any(ProcessDocumentEntity.class));
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
