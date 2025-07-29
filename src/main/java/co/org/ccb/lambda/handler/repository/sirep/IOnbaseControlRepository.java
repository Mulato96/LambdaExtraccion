package co.org.ccb.lambda.handler.repository.sirep;

import java.util.List;

import co.org.ccb.lambda.handler.model.entity.sirep.OnbaseControlEntity;

public interface IOnbaseControlRepository {

	List<OnbaseControlEntity> findDocumentsByEnrollmentNumber(List<String> enrollmentNumber);
}
