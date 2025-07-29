package co.org.ccb.lambda.handler.repository.sirep.impl;

import java.util.List;

import co.org.ccb.lambda.handler.model.entity.sirep.OnbaseControlEntity;
import co.org.ccb.lambda.handler.repository.sirep.IOnbaseControlRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnbaseControlRepositoryImpl implements IOnbaseControlRepository {

	private final EntityManager entityManager;

	public OnbaseControlRepositoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<OnbaseControlEntity> findDocumentsByEnrollmentNumber(String enrollmentNumber) {
		TypedQuery<OnbaseControlEntity> query = entityManager.createQuery(
				"SELECT o FROM OnbaseControlEntity o WHERE o.numMatricula = :enrollmentNumber",
				OnbaseControlEntity.class);

		log.info("Ejecutando query para la extracción de documentos: {}", query);
		query.setParameter("enrollmentNumber", enrollmentNumber);
		return query.getResultList();
	}
}
