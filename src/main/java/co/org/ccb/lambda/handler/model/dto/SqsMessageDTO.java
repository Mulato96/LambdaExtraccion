package co.org.ccb.lambda.handler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SqsMessageDTO {

	private String registrationId;
}
