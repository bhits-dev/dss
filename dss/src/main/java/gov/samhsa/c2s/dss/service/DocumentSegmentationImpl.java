package gov.samhsa.c2s.dss.service;

import ch.qos.logback.audit.AuditException;
import gov.samhsa.c2s.brms.domain.FactModel;
import gov.samhsa.c2s.brms.domain.RuleExecutionContainer;
import gov.samhsa.c2s.brms.domain.XacmlResult;
import gov.samhsa.c2s.brms.service.RuleExecutionService;
import gov.samhsa.c2s.brms.service.dto.AssertAndExecuteClinicalFactsResponse;
import gov.samhsa.c2s.common.log.Logger;
import gov.samhsa.c2s.common.log.LoggerFactory;
import gov.samhsa.c2s.common.marshaller.SimpleMarshaller;
import gov.samhsa.c2s.common.marshaller.SimpleMarshallerException;
import gov.samhsa.c2s.common.validation.exception.XmlDocumentReadFailureException;
import gov.samhsa.c2s.dss.infrastructure.DocumentValidatorClient;
import gov.samhsa.c2s.dss.infrastructure.dto.ValidationDiagnosticType;
import gov.samhsa.c2s.dss.infrastructure.dto.ValidationRequestDto;
import gov.samhsa.c2s.dss.infrastructure.dto.ValidationResponseDto;
import gov.samhsa.c2s.dss.infrastructure.valueset.ValueSetService;
import gov.samhsa.c2s.dss.infrastructure.valueset.dto.ConceptCodeAndCodeSystemOidDto;
import gov.samhsa.c2s.dss.infrastructure.valueset.dto.ValueSetCategoryMapResponseDto;
import gov.samhsa.c2s.dss.service.document.*;
import gov.samhsa.c2s.dss.service.document.dto.RedactedDocument;
import gov.samhsa.c2s.dss.service.dto.ClinicalDocumentValidationResult;
import gov.samhsa.c2s.dss.service.dto.DSSRequest;
import gov.samhsa.c2s.dss.service.dto.DSSResponse;
import gov.samhsa.c2s.dss.service.dto.SegmentDocumentResponse;
import gov.samhsa.c2s.dss.service.exception.DocumentSegmentationException;
import gov.samhsa.c2s.dss.service.exception.InvalidOriginalClinicalDocumentException;
import gov.samhsa.c2s.dss.service.exception.InvalidSegmentedClinicalDocumentException;
import gov.samhsa.c2s.dss.service.metadata.AdditionalMetadataGeneratorForSegmentedClinicalDocument;
import org.apache.axiom.attachments.ByteArrayDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DocumentSegmentationImpl implements DocumentSegmentation {

    /**
     * The Constant C32_CDA_XSD_PATH.
     */
    public static final String C32_CDA_XSD_PATH = "schema/cdar2c32/infrastructure/cda/";

    /**
     * The Constant C32_CDA_XSD_NAME.
     */
    public static final String C32_CDA_XSD_NAME = "C32_CDA.xsd";
    public static final Charset DEFAULT_ENCODING = StandardCharsets.UTF_8;
    private static final String CCDA_PREFIX = "CCDA";

    private final Logger logger = LoggerFactory
            .getLogger(this.getClass());

    /**
     * The rule execution web service client.
     */
    @Autowired
    private RuleExecutionService ruleExecutionService;

    /**
     * The document editor.
     */
    @Autowired
    private DocumentEditor documentEditor;

    /**
     * The marshaller.
     */
    @Autowired
    private SimpleMarshaller marshaller;

    /**
     * The document tagger.
     */
    @Autowired
    private DocumentTagger documentTagger;

    /**
     * The document fact model extractor.
     */
    @Autowired
    private DocumentFactModelExtractor documentFactModelExtractor;

    /**
     * The document redactor.
     */
    @Autowired
    private DocumentRedactor documentRedactor;

    /**
     * The embedded clinical document extractor.
     */
    @Autowired
    private EmbeddedClinicalDocumentExtractor embeddedClinicalDocumentExtractor;

    /**
     * The value set service.
     */
    @Autowired
    private ValueSetService valueSetService;

    /**
     * The additional metadata generator for segmented clinical document.
     */
    @Autowired
    private AdditionalMetadataGeneratorForSegmentedClinicalDocument additionalMetadataGeneratorForSegmentedClinicalDocument;

    @Autowired
    private ClinicalDocumentValidation clinicalDocumentValidation;

    @Autowired
    private DocumentValidatorClient documentValidatorClient;

    public DocumentSegmentationImpl() {
    }

    /**
     * Instantiates a new document processor impl.
     *
     * @param ruleExecutionService                                    the rule execution service
     * @param documentEditor                                          the document editor
     * @param marshaller                                              the marshaller
     * @param documentRedactor                                        the document redactor
     * @param documentTagger                                          the document tagger
     * @param documentFactModelExtractor                              the document fact model extractor
     * @param embeddedClinicalDocumentExtractor                       the embedded clinical document extractor
     * @param valueSetService                                         the value set service
     * @param additionalMetadataGeneratorForSegmentedClinicalDocument the additional metadata generator for segmented clinical
     *                                                                document
     */
    @Autowired
    public DocumentSegmentationImpl(
            RuleExecutionService ruleExecutionService,
            DocumentEditor documentEditor,
            SimpleMarshaller marshaller,
            DocumentRedactor documentRedactor,
            DocumentTagger documentTagger,
            DocumentFactModelExtractor documentFactModelExtractor,
            EmbeddedClinicalDocumentExtractor embeddedClinicalDocumentExtractor,
            ValueSetService valueSetService,
            AdditionalMetadataGeneratorForSegmentedClinicalDocument additionalMetadataGeneratorForSegmentedClinicalDocument) {
        this.ruleExecutionService = ruleExecutionService;
        this.documentEditor = documentEditor;
        this.marshaller = marshaller;
        this.documentRedactor = documentRedactor;
        this.documentTagger = documentTagger;
        this.documentFactModelExtractor = documentFactModelExtractor;
        this.embeddedClinicalDocumentExtractor = embeddedClinicalDocumentExtractor;
        this.valueSetService = valueSetService;
        this.additionalMetadataGeneratorForSegmentedClinicalDocument = additionalMetadataGeneratorForSegmentedClinicalDocument;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DSSResponse segmentDocument(DSSRequest dssRequest)
            throws XmlDocumentReadFailureException,
            InvalidSegmentedClinicalDocumentException, AuditException, InvalidOriginalClinicalDocumentException {
        final Charset charset = getCharset(dssRequest.getDocumentEncoding());
        String document = new String(dssRequest.getDocument(), charset);
        final String originalDocument = document;
        Assert.hasText(document);

        //Validate Original Document
        final ClinicalDocumentValidationResult originalClinicalDocumentValidationResult = validateOriginalClinicalDocument(dssRequest);

        Assert.notNull(dssRequest.getXacmlResult());
        final String enforcementPolicies = marshal(dssRequest.getXacmlResult());
        Assert.notNull(enforcementPolicies);

        RuleExecutionContainer ruleExecutionContainer = null;
        RedactedDocument redactedDocument = null;
        String rulesFired = null;
        final SegmentDocumentResponse segmentDocumentResponse = new SegmentDocumentResponse();
        FactModel factModel = null;

        try {

            document = documentEditor.setDocumentCreationDate(document);

            // extract factModel
            String factModelXml = documentFactModelExtractor.extractFactModel(
                    document, enforcementPolicies);
            // get clinical document with generatedEntryId elements
            document = embeddedClinicalDocumentExtractor
                    .extractClinicalDocumentFromFactModel(factModelXml);
            // remove the embedded c32 from factmodel before unmarshalling
            factModelXml = documentRedactor
                    .cleanUpEmbeddedClinicalDocumentFromFactModel(factModelXml);
            factModel = marshaller.unmarshalFromXml(FactModel.class,
                    factModelXml);

            // Get and set value set categories to clinical facts
            final List<ConceptCodeAndCodeSystemOidDto> conceptCodeAndCodeSystemOidDtoList =
                    factModel.getClinicalFactList().stream()
                            .map(fact -> new ConceptCodeAndCodeSystemOidDto(fact.getCode(), fact.getCodeSystem()))
                            .collect(Collectors.toList());

            // Get value set categories
            final List<ValueSetCategoryMapResponseDto> valueSetCategories = valueSetService
                    .lookupValueSetCategories(conceptCodeAndCodeSystemOidDtoList);
            factModel.getClinicalFactList()
                    .stream()
                    .forEach(fact -> valueSetCategories.stream()
                            .filter(dto -> fact.getCode().equals(dto.getCodedConceptCode()) && fact.getCodeSystem().equals(dto.getCodeSystemOid()))
                            .map(ValueSetCategoryMapResponseDto::getValueSetCategoryCodes)
                            .filter(Objects::nonNull)
                            .findAny().ifPresent(fact::setValueSetCategories));

            // get execution response container
            final AssertAndExecuteClinicalFactsResponse brmsResponse = ruleExecutionService
                    .assertAndExecuteClinicalFacts(factModel);
            String executionResponseContainer = brmsResponse
                    .getRuleExecutionResponseContainer();
            rulesFired = brmsResponse.getRulesFired();

            // unmarshall from xml to RuleExecutionContainer
            ruleExecutionContainer = marshaller.unmarshalFromXml(
                    RuleExecutionContainer.class, executionResponseContainer);

            logger.info("Fact model: " + factModelXml);
            logger.info("Rule Execution Container size: "
                    + ruleExecutionContainer.getExecutionResponseList().size());

            // redact document
            redactedDocument = documentRedactor.redactDocument(document,
                    ruleExecutionContainer, factModel);
            document = redactedDocument.getRedactedDocument();

            // set tryPolicyDocument in the response
            if (dssRequest.getEnableTryPolicyResponse().orElse(Boolean.FALSE)) {
                segmentDocumentResponse
                        .setTryPolicyDocumentXml(redactedDocument
                                .getTryPolicyDocument());
                logger.debug(() -> "Try Policy Document: " + segmentDocumentResponse.getTryPolicyDocumentXml());
            }

            // to get the itemActions from documentRedactor
            executionResponseContainer = marshaller
                    .marshal(ruleExecutionContainer);

            // tag document
            document = documentTagger.tagDocument(document,
                    executionResponseContainer);

            // clean up generatedEntryId elements from document
            document = documentRedactor.cleanUpGeneratedEntryIds(document);

            // clean up generatedServiceEventId elements from document
            document = documentRedactor
                    .cleanUpGeneratedServiceEventIds(document);

            // Set segmented document in response
            segmentDocumentResponse.setSegmentedDocumentXml(document);
            // Set execution response container in response
            segmentDocumentResponse
                    .setExecutionResponseContainerXml(executionResponseContainer);

        } catch (final JAXBException e) {
            logger.error(e.getMessage(), e);
            throw new DocumentSegmentationException(e.toString(), e);
        } catch (final Throwable e) {
            logger.error(e.getMessage(), e);
            throw new DocumentSegmentationException(e.toString(), e);
        }

        //Validate Segmented Document
        validateAndAuditeSegmentedClinicalDocument(originalClinicalDocumentValidationResult, charset, originalDocument, document, dssRequest,
                factModel, redactedDocument, rulesFired);

        DSSResponse dssResponse = new DSSResponse();
        dssResponse.setSegmentedDocument(segmentDocumentResponse.getSegmentedDocumentXml().getBytes(DEFAULT_ENCODING));
        dssResponse.setEncoding(DEFAULT_ENCODING.toString());
        dssResponse.setCCDADocument(isCCDADocument(originalClinicalDocumentValidationResult.getDocumentType()));
        if (dssRequest.getEnableTryPolicyResponse().orElse(Boolean.FALSE)) {
            dssResponse.setTryPolicyDocument(segmentDocumentResponse.getTryPolicyDocumentXml().getBytes(DEFAULT_ENCODING));
        }
        return dssResponse;
    }

    @Override
    public void setAdditionalMetadataForSegmentedClinicalDocument(
            SegmentDocumentResponse segmentDocumentResponse,
            String senderEmailAddress, String recipientEmailAddress,
            String xdsDocumentEntryUniqueId, XacmlResult xacmlResult) {
        final String additionalMetadataForSegmentedClinicalDocument = additionalMetadataGeneratorForSegmentedClinicalDocument
                .generateMetadataXml(xacmlResult.getMessageId(),
                        segmentDocumentResponse.getSegmentedDocumentXml(),
                        segmentDocumentResponse
                                .getExecutionResponseContainerXml(),
                        senderEmailAddress, recipientEmailAddress, xacmlResult
                                .getSubjectPurposeOfUse().getPurpose(),
                        xdsDocumentEntryUniqueId);

        segmentDocumentResponse
                .setPostSegmentationMetadataXml(additionalMetadataForSegmentedClinicalDocument);
    }

    @Override
    public void setDocumentPayloadRawData(
            SegmentDocumentResponse segmentDocumentResponse,
            boolean packageAsXdm, String senderEmailAddress,
            String recipientEmailAddress, XacmlResult xacmlResult)
            throws Exception {
        final ByteArrayDataSource rawData = documentEditor
                .setDocumentPayloadRawData(segmentDocumentResponse
                                .getSegmentedDocumentXml(), packageAsXdm,
                        senderEmailAddress, recipientEmailAddress, xacmlResult,
                        segmentDocumentResponse
                                .getExecutionResponseContainerXml(), null, null);
        segmentDocumentResponse.setDocumentPayloadRawData(new DataHandler(
                rawData));
    }

    private ClinicalDocumentValidationResult validateOriginalClinicalDocument(DSSRequest dssRequest) throws InvalidOriginalClinicalDocumentException {
        ValidationResponseDto responseDto = documentValidatorClient.validateClinicalDocument(ValidationRequestDto.builder()
                .document(dssRequest.getDocument())
                .documentEncoding(dssRequest.getDocumentEncoding())
                .build());

        if (!responseDto.isDocumentValid()) {
            responseDto.getValidationResultDetails()
                    .stream()
                    .filter(errorType -> errorType.getDiagnosticType().getTypeName().contains(ValidationDiagnosticType.CCDA_ERROR.getTypeName()))
                    .forEach(detail -> logger.error("Validation Error -- xPath: " + detail.getXPath() + ", Message: " + detail.getDescription()));
            throw new InvalidOriginalClinicalDocumentException("C-CDA validation failed for document type " + responseDto.getDocumentType());
        }

        return ClinicalDocumentValidationResult.builder()
                .documentType(responseDto.getDocumentType())
                .isValidDocument(responseDto.isDocumentValid())
                .build();
    }

    private void validateAndAuditeSegmentedClinicalDocument(ClinicalDocumentValidationResult originalClinicalDocumentValidationResult,
                                                            Charset charset,
                                                            String originalDocument,
                                                            String document,
                                                            DSSRequest dssRequest,
                                                            FactModel factModel,
                                                            RedactedDocument redactedDocument,
                                                            String rulesFired) {

    }

    private Charset getCharset(Optional<String> documentEncoding) {
        return documentEncoding.map(Charset::forName).orElse(DEFAULT_ENCODING);
    }

    private String marshal(Object o) {
        try {
            return marshaller.marshal(o);
        } catch (SimpleMarshallerException e) {
            throw new DocumentSegmentationException(e);
        }
    }

    private boolean isCCDADocument(String documentType) {
        return documentType.contains(CCDA_PREFIX);
    }
}
