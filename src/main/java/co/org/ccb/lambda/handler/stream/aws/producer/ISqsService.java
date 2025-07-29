package co.org.ccb.lambda.handler.stream.aws.producer;

import co.org.ccb.lambda.handler.model.dto.SqsMessageDTO;

public interface ISqsService {

	void sendMessage(SqsMessageDTO sqsMessageDTO);
}
