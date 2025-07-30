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


    @Override
    @Transactional
    public int getId() {
        entityManager.getTransaction().begin();
        String jpql = "SELECT p FROM ProcessEntity p ORDER BY p.id DESC";
        TypedQuery<ProcessEntity> query = entityManager.createQuery(jpql, ProcessEntity.class);
        query.setMaxResults(1);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        ProcessEntity lastProcess = query.getSingleResult();
        int maxId = (lastProcess != null) ? lastProcess.getId() + 1 : 1;
        entityManager.getTransaction().commit();
        return maxId;
    }

}
