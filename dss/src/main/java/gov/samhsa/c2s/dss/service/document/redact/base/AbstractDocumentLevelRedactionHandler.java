package gov.samhsa.c2s.dss.service.document.redact.base;

import gov.samhsa.c2s.common.document.accessor.DocumentAccessor;
import gov.samhsa.c2s.dss.service.document.dto.RedactionHandlerResult;
import org.w3c.dom.Document;

public abstract class AbstractDocumentLevelRedactionHandler extends
        AbstractRedactionHandler {

    public AbstractDocumentLevelRedactionHandler(
            DocumentAccessor documentAccessor) {
        super(documentAccessor);
    }

    protected AbstractDocumentLevelRedactionHandler() {
    }

    public abstract RedactionHandlerResult execute(Document xmlDocument, String documentType);
}
