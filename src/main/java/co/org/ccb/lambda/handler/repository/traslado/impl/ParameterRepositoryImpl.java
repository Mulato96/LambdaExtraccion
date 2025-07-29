package co.org.ccb.lambda.handler.repository.traslado.impl;

import java.util.List;

import co.org.ccb.lambda.handler.model.entity.traslado.ParameterEntity;
import co.org.ccb.lambda.handler.repository.traslado.IParameterRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

public class ParameterRepositoryImpl implements IParameterRepository {

	private EntityManager entityManager;

	public ParameterRepositoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<ParameterEntity> findIdsByTypeState(String estado) {
		String jpql = "SELECT p FROM ParameterEntity p WHERE p.type = :estado";
		TypedQuery<ParameterEntity> query = entityManager.createQuery(jpql, ParameterEntity.class);
		query.setParameter("estado", estado);
		return query.getResultList();
	}

	@Override
	public String findValueByTypeState(String estado) {
		String jpql = "SELECT p.value FROM ParameterEntity p WHERE p.type = :estado";
		TypedQuery<String> query = entityManager.createQuery(jpql, String.class);
		query.setParameter("estado", estado);

		List<String> results = query.getResultList();
		return results.isEmpty() ? null : results.get(0);
	}


	@Override
	public List<ParameterEntity> findParameters() {
		String jpql = "SELECT p FROM ParameterEntity p";
		TypedQuery<ParameterEntity> query = entityManager.createQuery(jpql, ParameterEntity.class);

		return query.getResultList();
	}


}
