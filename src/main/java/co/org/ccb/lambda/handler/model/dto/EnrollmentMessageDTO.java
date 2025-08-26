package co.org.ccb.lambda.handler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentMessageDTO {
    private String enrollmentNumber;
    private Integer processId;
}
