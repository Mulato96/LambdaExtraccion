package co.org.ccb.lambda.handler.repository.traslado;

import co.org.ccb.lambda.handler.model.entity.traslado.ProcessControlEntity;
import java.util.List;
import java.util.Set;

public interface IProcessControlRepository {

  boolean existsEnrollmentWithStates(String enrollmentNumber, List<Integer> idsStatesExcluded);

  List<ProcessControlEntity> findByEnrollNumber(Set<String> setEnrollNumbers);
}
