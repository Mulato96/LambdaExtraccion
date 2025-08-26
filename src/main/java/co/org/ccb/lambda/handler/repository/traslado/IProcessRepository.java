package co.org.ccb.lambda.handler.repository.traslado;

import java.util.Optional;
import co.org.ccb.lambda.handler.model.entity.traslado.ProcessEntity;

public interface IProcessRepository {
    Optional<ProcessEntity> findById(Integer id);
}
