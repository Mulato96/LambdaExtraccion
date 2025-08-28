package co.org.ccb.lambda.handler.repository.traslado.impl;

import co.org.ccb.lambda.handler.repository.traslado.IProcessRepository;
import jakarta.persistence.EntityManager;
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
        String jpql = "SELECT MAX(p.id) FROM ProcessEntity p";
        
        TypedQuery<Integer> query = entityManager.createQuery(jpql,
				Integer.class);

        Integer maxId = query.getSingleResult();
        return maxId != null ? maxId + 1 : 1;
    }

}
