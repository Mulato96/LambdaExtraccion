package co.org.ccb.lambda.handler.repository.traslado.impl;

import co.org.ccb.lambda.handler.repository.traslado.IProcessRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.Optional;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessEntity;

public class ProcessRepositoryImpl implements IProcessRepository {

    private EntityManager entityManager;

	public ProcessRepositoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

    @Override
    public Optional<ProcessEntity> findById(Integer id) {
        return Optional.ofNullable(entityManager.find(ProcessEntity.class, id));
    }
}
