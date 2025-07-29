package co.org.ccb.lambda.handler.service;

import co.org.ccb.lambda.handler.model.dto.ExtractionRequestDTO;

public interface IDocumentExtractionService {

	String extractDocuments(ExtractionRequestDTO request);
}
