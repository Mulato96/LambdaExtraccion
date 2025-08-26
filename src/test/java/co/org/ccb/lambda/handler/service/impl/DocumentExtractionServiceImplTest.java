package co.org.ccb.lambda.handler.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.org.ccb.lambda.handler.model.dto.EnrollmentMessageDTO;
import co.org.ccb.lambda.handler.repository.traslado.IProcessControlRepository;
import co.org.ccb.lambda.handler.repository.traslado.IProcessRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.org.ccb.lambda.handler.model.dto.ExtractionRequestDTO;
import co.org.ccb.lambda.handler.model.entity.sirep.EnrollmentsEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ParameterEntity;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessEntity;
import co.org.ccb.lambda.handler.repository.sirep.IEnrollmentsRepository;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import co.org.ccb.lambda.handler.stream.aws.producer.ISqsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

class DocumentExtractionServiceImplTest {

    private IEnrollmentsRepository enrollmentsRepository;
    private IProcessControlRepository processControlRepository;
    private IProcessRepository processRepository;
    private IParameterRepository parameterRepository;
    private ISqsService sqsService;
    private EntityManager trasladoEntityManager;
    private EntityTransaction trasladoTransaction;
    private DocumentExtractionServiceImpl service;

    @BeforeEach
    void setUp() {
        enrollmentsRepository = mock(IEnrollmentsRepository.class);
        processControlRepository = mock(IProcessControlRepository.class);
        parameterRepository = mock(IParameterRepository.class);
        processRepository = mock(IProcessRepository.class);
        sqsService = mock(ISqsService.class);
        EntityManager sirepEntityManager = mock(EntityManager.class);
        trasladoEntityManager = mock(EntityManager.class);
        trasladoTransaction = mock(EntityTransaction.class);

        when(trasladoEntityManager.getTransaction()).thenReturn(trasladoTransaction);

        // Use the updated constructor
        service = new DocumentExtractionServiceImpl(
            enrollmentsRepository,
            parameterRepository,
            sqsService,
            sirepEntityManager,
            trasladoEntityManager,
            processControlRepository,
            processRepository
        );
    }

    @Test
    void shouldReturnNoEnrollmentsMessage_whenNoEnrollmentsFound() {
        // Arrange
        ExtractionRequestDTO request = new ExtractionRequestDTO();
        request.setQuantityRecords(5);

        ParameterEntity limitParam = new ParameterEntity();
        limitParam.setType("LIMIT_MAX_PROCESS");
        limitParam.setValue("1000");

        List<ParameterEntity> params = new ArrayList<>();
        params.add(limitParam);

        when(parameterRepository.findParameters()).thenReturn(params);
        when(enrollmentsRepository.findByFilters(any(), any(), anyInt())).thenReturn(Collections.emptyList());

        // Act
        String result = service.extractDocuments(request);

        // Assert
        assertEquals("No se encontraron matrículas para procesar", result);
        verifyNoInteractions(sqsService);
    }

    @Test
    void testExtractDocuments_sendsMessagesToSqs() {
        // Arrange
        ExtractionRequestDTO request = new ExtractionRequestDTO();
        request.setQuantityRecords(1);

        ParameterEntity limitParam = new ParameterEntity();
        limitParam.setType("LIMIT_MAX_PROCESS");
        limitParam.setValue("1000");

        ParameterEntity stateParam = new ParameterEntity();
        stateParam.setType("STATE");
        stateParam.setValue("PENDIENTE");

        List<ParameterEntity> params = new ArrayList<>();
        params.add(limitParam);
        params.add(stateParam);

        when(parameterRepository.findParameters()).thenReturn(params);

        EnrollmentsEntity enrollment = new EnrollmentsEntity();
        enrollment.setNumMatricula("12345");
        when(enrollmentsRepository.findByFilters(any(), any(), anyInt())).thenReturn(List.of(enrollment));
        when(processControlRepository.findByEnrollNumber(any())).thenReturn(Collections.emptyList());

        doAnswer(invocation -> {
            ProcessEntity p = invocation.getArgument(0);
            p.setId(100);
            return null;
        }).when(trasladoEntityManager).persist(any(ProcessEntity.class));

        // Act
        String result = service.extractDocuments(request);

        // Assert
        assertTrue(result.contains("Proceso de despacho completado exitosamente con el ID: 100"));
        verify(sqsService, times(1)).sendEnrollmentMessages(any(List.class));
        verify(trasladoTransaction).begin();
        verify(trasladoTransaction).commit();
    }
}
