package co.org.ccb.lambda.handler.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.org.ccb.lambda.handler.model.dto.ExtractionRequestDTO;
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

        // Mock parameter repository to return some default values
        when(parameterRepository.findParameters()).thenReturn(List.of(
                createParameter("traslado.parameter.type.limitmaxprocess", "100"),
                createParameter("traslado.parameter.type.state", "PENDIENTE")));

        service = new DocumentExtractionServiceImpl(enrollmentsRepository, onbaseControlRepository, parameterRepository,
                sqsService, sirepEntityManager, trasladoEntityManager, processControlRepository,
                processDocumentRepository, processRepository);
    }

    private ParameterEntity createParameter(String type, String value) {
        ParameterEntity p = new ParameterEntity();
        p.setType(type);
        p.setValue(value);
        p.setId(1); // Default ID
        return p;
    }

    @Test
    void testExtractDocuments_SuccessfulFlow_OneBatch() {
        // Arrange
        ExtractionRequestDTO request = new ExtractionRequestDTO();
        request.setQuantityRecords(1);

        EnrollmentsEntity enrollment = new EnrollmentsEntity();
        enrollment.setNumMatricula("12345");
        enrollment.setIdOrganizacion("1");

        // Mock fetching one enrollment, then an empty list to stop the batch loop
        when(enrollmentsRepository.findByFilters(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(enrollment)) // First call returns one record
                .thenReturn(Collections.emptyList()); // Second call returns empty

        // Mock process control repository to return no existing processed enrollments
        when(processControlRepository.findByEnrollNumber(anySet())).thenReturn(Collections.emptyList());

        // Mock bulk fetches to return empty lists
        when(onbaseControlRepository.findDocumentsByEnrollmentNumbers(anySet())).thenReturn(Collections.emptyList());
        when(processDocumentRepository.findByEnrollmentNumbers(anySet())).thenReturn(Collections.emptyList());
        when(enrollmentsRepository.findCertificateInfo(anySet(), anySet())).thenReturn(Collections.emptyList());

        // Mock process entity creation
        doAnswer(inv -> {
            ProcessEntity p = inv.getArgument(0);
            setPrivateField(p, "id", 100);
            return null;
        }).when(trasladoEntityManager).persist(any(ProcessEntity.class));

        // Act
        String result = service.extractDocuments(request);

        // Assert
        assertTrue(result.contains("Proceso completado exitosamente con el ID: 100"));
        verify(trasladoTransaction, times(2)).begin(); // Once for ProcessEntity, once for the batch
        verify(trasladoTransaction, times(2)).commit();
        verify(sqsService, times(1)).sendMessageBatch(anyList());
    }

    @Test
    void testExtractDocuments_NoEnrollmentsFound() {
        // Arrange
        ExtractionRequestDTO request = new ExtractionRequestDTO();
        request.setQuantityRecords(5);

        // Mock fetching to return an empty list on the first call
        when(enrollmentsRepository.findByFilters(any(), any(), anyInt(), anyInt())).thenReturn(Collections.emptyList());

        // Mock process entity creation
        doAnswer(inv -> {
            ProcessEntity p = inv.getArgument(0);
            setPrivateField(p, "id", 123);
            return null;
        }).when(trasladoEntityManager).persist(any(ProcessEntity.class));

        // Act
        String result = service.extractDocuments(request);

        // Assert
        assertTrue(result.contains("Proceso completado exitosamente con el ID: 123"));
        verifyNoInteractions(sqsService); // No messages should be sent
        verify(trasladoTransaction, times(1)).commit(); // Only the process entity transaction
    }

    @Test
    void testGetEnrollmentsFromSIREP_success() {
        // Arrange
        ExtractionRequestDTO request = new ExtractionRequestDTO();
        request.setQuantityRecords(10);
        request.setYears(List.of(2023));
        request.setTypesCodes(List.of(2901L));

        EnrollmentsEntity mockEnrollment = new EnrollmentsEntity();
        mockEnrollment.setNumMatricula("12345");

        when(enrollmentsRepository.findByFilters(anyList(), anyList(), anyInt(), anyInt()))
                .thenReturn(List.of(mockEnrollment));

        // Act
        List<EnrollmentsEntity> result = service.getEnrollmentsFromSIREP(request, 10, 0);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("12345", result.get(0).getNumMatricula());
        verify(enrollmentsRepository).findByFilters(List.of(2023), List.of(2901L), 10, 0);
    }

    @Test
    void testProcessDocumentsForEnrollment_withData() {
        // Arrange
        setPrivateField(service, "valueStatePendiente", "PENDIENTE");
        setPrivateField(service, "valueStateIncompleto", "INCOMPLETO");
        setPrivateField(service, "valueStateError", "ERROR");

        EnrollmentsEntity enrollment = new EnrollmentsEntity();
        enrollment.setNumMatricula("12345");
        enrollment.setCtrCertActiva(1);
        enrollment.setNumReciboActiva("9876");

        ProcessControlEntity processControl = new ProcessControlEntity();
        setPrivateField(processControl, "id", 999);

        OnbaseControlEntity doc = new OnbaseControlEntity();
        doc.setNumMatricula("12345");
        doc.setCtrDocumento(1); // KARDEX
        doc.setHandle(456L);
        doc.setNombreArchivo("archivo.pdf");
        List<OnbaseControlEntity> documents = List.of(doc);

        CertificateInfoEntity cert = new CertificateInfoEntity();
        cert.setCodVerificacion("CERT-001");
        List<CertificateInfoEntity> certificates = List.of(cert);

        // Act - Call the method with pre-fetched data
        service.processDocumentsForEnrollment(enrollment, processControl, List.of(), documents, new HashMap<>(),
                certificates);

        // Assert
        ArgumentCaptor<ProcessDocumentEntity> captor = ArgumentCaptor.forClass(ProcessDocumentEntity.class);
        verify(trasladoEntityManager, times(2)).persist(captor.capture());

        List<ProcessDocumentEntity> capturedDocs = captor.getAllValues();
        assertEquals(2, capturedDocs.size()); // One for the OnBase doc, one for the certificate
        assertTrue(capturedDocs.stream()
                .anyMatch(p -> p.getUniqueDocumentNumber().equals("456")));
        assertTrue(capturedDocs.stream()
                .anyMatch(p -> p.getUniqueDocumentNumber().equals("CERT-001")));
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
