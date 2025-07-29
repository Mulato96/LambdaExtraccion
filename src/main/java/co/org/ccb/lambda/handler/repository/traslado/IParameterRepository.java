package co.org.ccb.lambda.handler.repository.traslado;

import java.util.List;


import co.org.ccb.lambda.handler.model.entity.traslado.ParameterEntity;

public interface IParameterRepository {

	List<ParameterEntity> findIdsByTypeState(String estado);

	String findValueByTypeState(String estado);

	List<ParameterEntity> findParameters();
}
