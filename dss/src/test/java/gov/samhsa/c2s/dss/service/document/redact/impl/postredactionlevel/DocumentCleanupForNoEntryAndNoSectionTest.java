package gov.samhsa.c2s.dss.service.document.redact.impl.postredactionlevel;

import gov.samhsa.c2s.brms.domain.FactModel;
import gov.samhsa.c2s.brms.domain.RuleExecutionContainer;
import gov.samhsa.c2s.common.document.accessor.DocumentAccessor;
import gov.samhsa.c2s.common.document.accessor.DocumentAccessorImpl;
import gov.samhsa.c2s.common.document.converter.DocumentXmlConverter;
import gov.samhsa.c2s.common.document.converter.DocumentXmlConverterImpl;
import gov.samhsa.c2s.common.filereader.FileReader;
import gov.samhsa.c2s.common.filereader.FileReaderImpl;
import gov.samhsa.c2s.common.log.Logger;
import gov.samhsa.c2s.common.marshaller.SimpleMarshaller;
import gov.samhsa.c2s.common.marshaller.SimpleMarshallerException;
import gov.samhsa.c2s.common.marshaller.SimpleMarshallerImpl;
import gov.samhsa.c2s.dss.config.DssProperties;
import gov.samhsa.c2s.dss.infrastructure.valueset.ValueSetService;
import gov.samhsa.c2s.dss.infrastructure.valueset.ValueSetServiceImplMock;
import gov.samhsa.c2s.dss.infrastructure.valueset.dto.ValueSetCategoryResponseDto;
import gov.samhsa.c2s.dss.service.document.EmbeddedClinicalDocumentExtractor;
import gov.samhsa.c2s.dss.service.document.EmbeddedClinicalDocumentExtractorImpl;
import gov.samhsa.c2s.dss.service.document.dto.RedactionHandlerResult;
import gov.samhsa.c2s.dss.service.document.redact.dto.PdpObligationsComplementSetDto;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DocumentCleanupForNoEntryAndNoSectionTest {

    public static final String DOCUMENT_TYPE_HITSP_C32 = "HITSP_C32";
    public static final String TEST_PATH = "sampleC32-redactionHandlers/";
    public static final String FACTMODEL_PATH = "factmodel/";
    public static final String RULEEXECUTIONCONTAINER_PATH = "ruleexecutioncontainer/";
    private static ValueSetService valueSetService;
    private FileReader fileReader;
    private SimpleMarshaller marshaller;
    private DocumentAccessor documentAccessor;
    private DocumentXmlConverter documentXmlConverter;
    private EmbeddedClinicalDocumentExtractor embeddedClinicalDocumentExtractor;
    private DssProperties dssProperties;
    private DocumentCleanupForNoEntryAndNoSection sut;

    @Before
    public void setUp() throws Exception {
        fileReader = new FileReaderImpl();
        marshaller = new SimpleMarshallerImpl();
        documentAccessor = new DocumentAccessorImpl();
        documentXmlConverter = new DocumentXmlConverterImpl();
        embeddedClinicalDocumentExtractor = new EmbeddedClinicalDocumentExtractorImpl();
        ReflectionTestUtils.setField(embeddedClinicalDocumentExtractor, "documentXmlConverter", documentXmlConverter);
        ReflectionTestUtils.setField(embeddedClinicalDocumentExtractor, "documentAccessor", documentAccessor);
        valueSetService = new ValueSetServiceImplMock(fileReader);
        dssProperties = new DssProperties();
        DssProperties.Redact redact = new DssProperties.Redact();
        final List<String> requiredSections = Collections.emptyList();
        DssProperties.DocumentTypeDetail documentTypeDetail = new DssProperties.DocumentTypeDetail();
        documentTypeDetail.setRequiredSections(requiredSections);
        redact.getDocumentTypes().put(DOCUMENT_TYPE_HITSP_C32, documentTypeDetail);
        dssProperties.setRedact(redact);
        sut = new DocumentCleanupForNoEntryAndNoSection(documentAccessor, dssProperties);
    }

    @Test
    public void testExecute_AddEmptySectionComponentIfNoneExists()
            throws IOException, SimpleMarshallerException,
            XPathExpressionException {
        // Arrange
        final String c32FileName = "MIE_SampleC32-sectionWithNoEntriesAndNoOtherSections.xml";
        final String factmodelXml = fileReader.readFile(TEST_PATH
                + FACTMODEL_PATH + c32FileName);
        final String c32 = embeddedClinicalDocumentExtractor
                .extractClinicalDocumentFromFactModel(factmodelXml);
        final String ruleExecutionContainerXml = fileReader.readFile(TEST_PATH
                + RULEEXECUTIONCONTAINER_PATH + c32FileName);
        final RuleExecutionContainer ruleExecutionContainer = marshaller
                .unmarshalFromXml(RuleExecutionContainer.class,
                        ruleExecutionContainerXml);
        final Document c32Document = documentXmlConverter.loadDocument(c32);
        final Document factModelDocument = documentXmlConverter
                .loadDocument(factmodelXml);
        final FactModel factModel = marshaller.unmarshalFromXml(
                FactModel.class, factmodelXml);
        final PdpObligationsComplementSetDto pdpObligationsComplementSetDto = new PdpObligationsComplementSetDto();
        RedactionHandlerResult preRedactionResults = new RedactionHandlerResult();

        // Act
        sut.execute(c32Document, factModel.getXacmlResult(), factModel,
                factModelDocument, ruleExecutionContainer, preRedactionResults, pdpObligationsComplementSetDto, DOCUMENT_TYPE_HITSP_C32);

        // Assert
        assertEquals(1,
                documentAccessor.getNodeList(c32Document, "//hl7:section")
                        .getLength());
        assertEquals(0,
                documentAccessor.getNodeList(c32Document, "//hl7:section")
                        .item(0).getChildNodes().getLength());
        assertEquals(
                0,
                documentAccessor.getNodeList(c32Document,
                        "//hl7:section[hl7:code[@code='11450-4']]").getLength());
        assertEquals(
                0,
                documentAccessor.getNodeList(c32Document,
                        "//hl7:section[hl7:code[@code='30954-2']]").getLength());
    }

    @Test
    public void testExecute_CleanUpSectionComponentsWithNoEntries()
            throws IOException, SimpleMarshallerException,
            XPathExpressionException {
        // Arrange
        final String c32FileName = "MIE_SampleC32-sectionWithNoEntries.xml";
        final String factmodelXml = fileReader.readFile(TEST_PATH
                + FACTMODEL_PATH + c32FileName);
        final String c32 = embeddedClinicalDocumentExtractor
                .extractClinicalDocumentFromFactModel(factmodelXml);
        final String ruleExecutionContainerXml = fileReader.readFile(TEST_PATH
                + RULEEXECUTIONCONTAINER_PATH + c32FileName);
        final RuleExecutionContainer ruleExecutionContainer = marshaller
                .unmarshalFromXml(RuleExecutionContainer.class,
                        ruleExecutionContainerXml);
        final Document c32Document = documentXmlConverter.loadDocument(c32);
        final Document factModelDocument = documentXmlConverter
                .loadDocument(factmodelXml);
        final FactModel factModel = marshaller.unmarshalFromXml(
                FactModel.class, factmodelXml);
        final PdpObligationsComplementSetDto pdpObligationsComplementSetDto = new PdpObligationsComplementSetDto();
        RedactionHandlerResult preRedactionResults = new RedactionHandlerResult();

        // Act
        sut.execute(c32Document, factModel.getXacmlResult(), factModel,
                factModelDocument, ruleExecutionContainer, preRedactionResults, pdpObligationsComplementSetDto, DOCUMENT_TYPE_HITSP_C32);

        // Assert
        assertEquals(1,
                documentAccessor.getNodeList(c32Document, "//hl7:section")
                        .getLength());
        assertEquals(
                0,
                documentAccessor.getNodeList(c32Document,
                        "//hl7:section[hl7:code[@code='11450-4']]").getLength());
        assertEquals(
                1,
                documentAccessor.getNodeList(c32Document,
                        "//hl7:section[hl7:code[@code='30954-2']]").getLength());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecute_CleanUpSectionComponentsWithNoEntries_EmptySectionComponents_ParentNull()
            throws IOException, SimpleMarshallerException,
            XPathExpressionException {
        // Arrange
        final Logger loggerMock = mock(Logger.class);
        final DocumentAccessor documentAccessorMock = mock(DocumentAccessor.class);
        sut = new DocumentCleanupForNoEntryAndNoSection(documentAccessorMock, dssProperties);
        ReflectionTestUtils.setField(sut, "logger", loggerMock);
        final String c32FileName = "MIE_SampleC32-sectionWithNoEntries.xml";
        final String factmodelXml = fileReader.readFile(TEST_PATH
                + FACTMODEL_PATH + c32FileName);
        final String c32 = embeddedClinicalDocumentExtractor
                .extractClinicalDocumentFromFactModel(factmodelXml);
        final String ruleExecutionContainerXml = fileReader.readFile(TEST_PATH
                + RULEEXECUTIONCONTAINER_PATH + c32FileName);
        final RuleExecutionContainer ruleExecutionContainer = marshaller
                .unmarshalFromXml(RuleExecutionContainer.class,
                        ruleExecutionContainerXml);
        final Document c32Document = documentXmlConverter.loadDocument(c32);
        final Document factModelDocument = documentXmlConverter
                .loadDocument(factmodelXml);
        final FactModel factModel = marshaller.unmarshalFromXml(
                FactModel.class, factmodelXml);
        final Node nodeMock = mock(Node.class);
        final Node parentNodeMock = mock(Node.class);
        final PdpObligationsComplementSetDto pdpObligationsComplementSetDto = new PdpObligationsComplementSetDto();

        when(nodeMock.getParentNode()).thenReturn(parentNodeMock);
        doThrow(NullPointerException.class).when(parentNodeMock).removeChild(nodeMock);
        when(documentAccessorMock.getNodeListAsStream(eq(c32Document), anyString()))
                .thenReturn(Stream.of(nodeMock));
        when(documentAccessorMock.getNode(eq(c32Document), anyString()))
                .thenReturn(Optional.empty());
        RedactionHandlerResult preRedactionResults = new RedactionHandlerResult();

        // Act
        sut.execute(c32Document, factModel.getXacmlResult(), factModel,
                factModelDocument, ruleExecutionContainer, preRedactionResults, pdpObligationsComplementSetDto, DOCUMENT_TYPE_HITSP_C32);

        // Assert
        assertEquals(2,
                documentAccessor.getNodeList(c32Document, "//hl7:section")
                        .getLength());
        assertEquals(
                1,
                documentAccessor.getNodeList(c32Document,
                        "//hl7:section[hl7:code[@code='11450-4']]").getLength());
        assertEquals(
                1,
                documentAccessor.getNodeList(c32Document,
                        "//hl7:section[hl7:code[@code='30954-2']]").getLength());
        verify(loggerMock, times(1)).info(any(Supplier.class),
                any(Throwable.class));
    }

    @Test
    public void testExecute_CleanUpSectionComponentsWithNoEntries_EmptySectionComponentsNull()
            throws IOException, SimpleMarshallerException,
            XPathExpressionException {
        // Arrange
        final DocumentAccessor documentAccessorMock = mock(DocumentAccessor.class);
        sut = new DocumentCleanupForNoEntryAndNoSection(documentAccessorMock, dssProperties);
        final String c32FileName = "MIE_SampleC32-sectionWithNoEntries.xml";
        final String factmodelXml = fileReader.readFile(TEST_PATH
                + FACTMODEL_PATH + c32FileName);
        final String c32 = embeddedClinicalDocumentExtractor
                .extractClinicalDocumentFromFactModel(factmodelXml);
        final String ruleExecutionContainerXml = fileReader.readFile(TEST_PATH
                + RULEEXECUTIONCONTAINER_PATH + c32FileName);
        final RuleExecutionContainer ruleExecutionContainer = marshaller
                .unmarshalFromXml(RuleExecutionContainer.class,
                        ruleExecutionContainerXml);
        final Document c32Document = documentXmlConverter.loadDocument(c32);
        final Document factModelDocument = documentXmlConverter
                .loadDocument(factmodelXml);
        final FactModel factModel = marshaller.unmarshalFromXml(
                FactModel.class, factmodelXml);
        when(documentAccessorMock.getNodeListAsStream(eq(c32Document), anyString()))
                .thenReturn(Stream.empty());
        when(documentAccessorMock.getNode(eq(c32Document), anyString()))
                .thenReturn(Optional.empty());
        RedactionHandlerResult preRedactionResults = new RedactionHandlerResult();

        Set<ValueSetCategoryResponseDto> allValueSetCategoryDtosSet = new HashSet<>(valueSetService.getAllValueSetCategories());
        Set<String> xacmlPdpObligations = new HashSet<>(factModel.getXacmlResult().getPdpObligations());

        Set<String> pdpObligationsComplementSet = new HashSet<>();

        // Calculate the set difference (i.e. complement set)
        pdpObligationsComplementSet.addAll(allValueSetCategoryDtosSet.stream()
                .map(ValueSetCategoryResponseDto::getCode)
                .filter(valSetCatCode -> !xacmlPdpObligations.contains(valSetCatCode))
                .collect(Collectors.toList()));

        PdpObligationsComplementSetDto pdpObligationsComplementSetDto = new PdpObligationsComplementSetDto(pdpObligationsComplementSet);

        // Act
        sut.execute(c32Document, factModel.getXacmlResult(), factModel,
                factModelDocument, ruleExecutionContainer, preRedactionResults, pdpObligationsComplementSetDto, DOCUMENT_TYPE_HITSP_C32);

        // Assert
        assertEquals(2,
                documentAccessor.getNodeList(c32Document, "//hl7:section")
                        .getLength());
        assertEquals(
                1,
                documentAccessor.getNodeList(c32Document,
                        "//hl7:section[hl7:code[@code='11450-4']]").getLength());
        assertEquals(
                1,
                documentAccessor.getNodeList(c32Document,
                        "//hl7:section[hl7:code[@code='30954-2']]").getLength());
    }
}
