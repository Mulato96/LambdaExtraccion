package co.org.ccb.lambda.handler.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExtractionRequestDTO {

	private List<Integer> years;
	private List<Long> typesCodes;	
	private Integer quantityRecords;
}
