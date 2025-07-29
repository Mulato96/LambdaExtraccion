package co.org.ccb.lambda.handler.repository.traslado;

import co.org.ccb.lambda.handler.model.entity.traslado.ProcessDocumentEntity;
import java.util.List;

public interface IProcessDocumentRepository {

  List<ProcessDocumentEntity> findByEnrollmentNumber(List<String> enrolmentNumber);
}
