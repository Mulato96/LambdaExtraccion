package co.org.ccb.lambda.handler.repository.sirep;

import co.org.ccb.lambda.handler.model.entity.sirep.CertificateInfoEntity;
import java.util.List;
import java.util.Optional;

import co.org.ccb.lambda.handler.model.entity.sirep.EnrollmentsEntity;
import java.util.Set;

public interface IEnrollmentsRepository {

	List<EnrollmentsEntity> findByFilters(List<Integer> years, List<Long> typesCodes, Integer quantityRecords);

	Optional<EnrollmentsEntity> findSingleByFiltersAndStateProcess(String enrollmentNumber);

	Optional<Short> findRecordTypeByEnrollmentNumber(Long enrollmentNumber);

	List<CertificateInfoEntity> findCertificateInfo(String numMatricula, Set<String> numRecibo);

	List<CertificateInfoEntity> findAllCertificateInfoByEnrollmentNumbers(List<String> enrollmentNumbers);

}
