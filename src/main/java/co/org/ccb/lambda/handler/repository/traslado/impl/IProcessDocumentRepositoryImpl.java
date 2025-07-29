package co.org.ccb.lambda.handler.repository.traslado.impl;

import co.org.ccb.lambda.handler.model.entity.traslado.ProcessDocumentEntity;
import co.org.ccb.lambda.handler.repository.traslado.IProcessDocumentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class IProcessDocumentRepositoryImpl implements IProcessDocumentRepository {

  private final EntityManager entityManager;

  public IProcessDocumentRepositoryImpl(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<ProcessDocumentEntity> findByEnrollmentNumber(List<String> enrolmentNumber) {
    TypedQuery<ProcessDocumentEntity> query = entityManager.createQuery(
        "SELECT o FROM ProcessDocumentEntity o WHERE o.enrollmentNumber IN :enrollmentNumber",
        ProcessDocumentEntity.class);
    log.info("Ejecutando query para la extracción de documentos en RDS: {}", query);
    query.setParameter("enrollmentNumber", enrolmentNumber);
    return query.getResultList();
  }
}
