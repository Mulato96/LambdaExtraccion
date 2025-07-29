package co.org.ccb.lambda.handler.repository.traslado.impl;

import co.org.ccb.lambda.handler.model.entity.traslado.ProcessControlEntity;
import java.util.List;

import co.org.ccb.lambda.handler.repository.traslado.IProcessControlRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.Set;

public class ProcessControlRepositoryImpl implements IProcessControlRepository {

	private EntityManager entityManager;

	public ProcessControlRepositoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public boolean existsEnrollmentWithStates(String enrollmentNumber, List<Integer> idsStatesExcluded) {
		String jpql = """
				    SELECT COUNT(p) FROM ProcessControlEntity p
				    WHERE p.enrollmentNumber = :enrollmentNumber
				    AND p.processStatusId IN :statusIds
				""";

		TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
		query.setParameter("enrollmentNumber", enrollmentNumber);
		query.setParameter("statusIds", idsStatesExcluded);

		Long count = query.getSingleResult();
		return count != null && count > 0;
	}

	@Override
	public List<ProcessControlEntity> findByEnrollNumber(Set<String> setEnrollNumbers) {
		if (setEnrollNumbers == null || setEnrollNumbers.isEmpty()) {
			return List.of();
		}
		String jpql = """
				    SELECT p FROM ProcessControlEntity p
				    WHERE p.enrollmentNumber IN :enrollmentNumbers
				""";
		TypedQuery<ProcessControlEntity> query = entityManager.createQuery(jpql,
				ProcessControlEntity.class);
		query.setParameter("enrollmentNumbers", setEnrollNumbers);
		return query.getResultList();
	}
}
