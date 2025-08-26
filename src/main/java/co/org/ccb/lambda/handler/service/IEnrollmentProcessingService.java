package co.org.ccb.lambda.handler.service;

import co.org.ccb.lambda.handler.model.dto.EnrollmentMessageDTO;

public interface IEnrollmentProcessingService {
    void processEnrollment(EnrollmentMessageDTO enrollmentMessage);
}
