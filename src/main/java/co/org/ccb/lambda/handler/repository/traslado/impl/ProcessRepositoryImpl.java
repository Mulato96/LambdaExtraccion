package co.org.ccb.lambda.handler.repository.traslado.impl;

import co.org.ccb.lambda.handler.repository.traslado.IProcessRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

public class ProcessRepositoryImpl implements IProcessRepository {

    private EntityManager entityManager;

	public ProcessRepositoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}


}
