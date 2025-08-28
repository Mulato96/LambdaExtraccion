package co.org.ccb.lambda.handler.repository.sirep;

import java.util.List;
import java.util.Set;

import co.org.ccb.lambda.handler.model.entity.sirep.OnbaseControlEntity;

public interface IOnbaseControlRepository {

	List<OnbaseControlEntity> findDocumentsByEnrollmentNumber(String enrollmentNumber);

	List<OnbaseControlEntity> findDocumentsByEnrollmentNumbers(Set<String> enrollmentNumbers);
}
