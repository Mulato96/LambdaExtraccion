package co.org.ccb.lambda.handler.repository.sirep.impl;

import static co.org.ccb.lambda.handler.util.AppPropertiesLoader.get;

import co.org.ccb.lambda.handler.model.entity.sirep.CertificateInfoEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import co.org.ccb.lambda.handler.model.entity.sirep.EnrollmentsEntity;
import co.org.ccb.lambda.handler.model.entity.sirep.OnbaseControlEntity;
import co.org.ccb.lambda.handler.repository.sirep.IEnrollmentsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Set;


public class EnrollmentsRepositoryImpl implements IEnrollmentsRepository {

	private String allowedStatesConfig;

	private EntityManager entityManager;

	public EnrollmentsRepositoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
		this.allowedStatesConfig = get("enrollments.sirep.allowedStates");
	}

	@Override
	public List<EnrollmentsEntity> findByFilters(List<Integer> years, List<Long> typesCodes, Integer quantityRecords) {
		System.out.println("Ejecutando consulta para la extracción de matrículas");

		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<EnrollmentsEntity> query = cb.createQuery(EnrollmentsEntity.class);
		Root<EnrollmentsEntity> enrollmentsRoot = query.from(EnrollmentsEntity.class);

		List<Predicate> predicates = new ArrayList<>();

		List<String> allowedStates = Arrays.asList(allowedStatesConfig.split(","));
		predicates.add(enrollmentsRoot.get("idEstadoMat").in(allowedStates));

		if (typesCodes != null && !typesCodes.isEmpty()) {
			predicates.add(enrollmentsRoot.get("idOrganizacion").in(typesCodes));
		}

		boolean hasYears = years != null && !years.isEmpty();
		if (hasYears) {
			predicates.add(enrollmentsRoot.get("ultAnoRenov").in(years));
		}

		query.select(enrollmentsRoot).where(cb.and(predicates.toArray(new Predicate[0])));

		if (hasYears) {
			CriteriaBuilder.Case<Integer> orderCase = cb.selectCase();
			for (int i = 0; i < years.size(); i++) {
				orderCase.when(cb.equal(enrollmentsRoot.get("ultAnoRenov"), years.get(i)), i);
			}
			query.orderBy(cb.asc(orderCase), cb.asc(enrollmentsRoot.get("ultAnoRenov")));
		} else {
			int currentYear = java.time.Year.now().getValue();
			int lastYear = currentYear - 1;

			predicates.add(cb.lessThanOrEqualTo(enrollmentsRoot.get("ultAnoRenov"), currentYear));

			Order orderByLastYearFirst = cb
					.desc(cb.selectCase().when(cb.equal(enrollmentsRoot.get("ultAnoRenov"), lastYear), 0)
							.when(cb.lessThan(enrollmentsRoot.get("ultAnoRenov"), lastYear), 1)
							.when(cb.equal(enrollmentsRoot.get("ultAnoRenov"), currentYear), 2).otherwise(3));

			query.orderBy(orderByLastYearFirst, cb.desc(enrollmentsRoot.get("ultAnoRenov")),
					cb.asc(enrollmentsRoot.get("numMatricula")));
		}

		System.out.println("Ejecutando consulta para la extracción de matrículas");

		TypedQuery<EnrollmentsEntity> typedQuery = entityManager.createQuery(query);

		if (quantityRecords != null) {
			typedQuery.setMaxResults(quantityRecords);
		}

		return typedQuery.getResultList();
	}

	@Override
	public Optional<EnrollmentsEntity> findSingleByFiltersAndStateProcess(String enrollmentNumber) {

		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<EnrollmentsEntity> query = cb.createQuery(EnrollmentsEntity.class);
		Root<EnrollmentsEntity> enrollmentsRoot = query.from(EnrollmentsEntity.class);

		Root<OnbaseControlEntity> onbaseRoot = query.from(OnbaseControlEntity.class);

		List<Predicate> predicates = new ArrayList<>();

		predicates.add(cb.equal(enrollmentsRoot.get("numMatricula"), onbaseRoot.get("numMatricula")));

		if (enrollmentNumber != null && !enrollmentNumber.toString().isEmpty()) {
			predicates.add(cb.notEqual(enrollmentsRoot.get("numMatricula"), enrollmentNumber));
		}

		List<String> allowedStates = Arrays.asList(allowedStatesConfig.split(","));
		predicates.add(enrollmentsRoot.get("idEstadoMat").in(allowedStates));

		query.select(enrollmentsRoot).where(cb.and(predicates.toArray(new Predicate[0])));

		TypedQuery<EnrollmentsEntity> typedQuery = entityManager.createQuery(query);
		typedQuery.setMaxResults(1);

		List<EnrollmentsEntity> results = typedQuery.getResultList();

		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	@Override
	public Optional<Short> findRecordTypeByEnrollmentNumber(Long enrollmentNumber) {
		String sql = """
				SELECT CASE
				           WHEN LEFT(RM.NUM_MATRICULA, 1) = 'S' THEN 1
				           ELSE 0
				       END AS REGISTRO
				FROM SIREP.RM_MATRICULAS_SOACHA cs
				INNER JOIN SIREP.RM_MATRICULADOS rm
				    ON rm.NUM_MATRICULA = cs.NUM_MATRICULA
				WHERE rm.NUM_MATRICULA = :enrollmentNumber
				""";

		Query query = entityManager.createNativeQuery(sql);
		query.setParameter("enrollmentNumber", enrollmentNumber);

		Object result = query.getSingleResult();
		return Optional.ofNullable(result != null ? ((Number) result).shortValue() : null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<CertificateInfoEntity> findCertificateInfo(String numMatricula, Set<String> numRecibo) {
		String sql =  """
        SELECT CCC.COD_VERIFICACION, CCC.NUM_RECIBO, CCS.NUM_MATRICULA
        FROM SIREP.CC_CERTIFICADOS_CONTROL CCC
        INNER JOIN SIREP.CC_CERTIFICADOS_SOLICITADOS CCS
        ON CCS.NUM_RECIBO = CCC.NUM_RECIBO
        AND CCS.ID_CERTIFICADO = CCC.ID_CERTIFICADO
        AND CCS.NUM_CLIENTE = CCC.NUM_CLIENTE
        WHERE CCS.NUM_MATRICULA = :numMatricula AND CCS.NUM_RECIBO IN (:numRecibo)
    """;
		Query query = entityManager.createNativeQuery(sql, CertificateInfoEntity.class);
		query.setParameter("numMatricula", numMatricula);
		query.setParameter("numRecibo", numRecibo.stream().toList());

		return query.getResultList();
	}

}
