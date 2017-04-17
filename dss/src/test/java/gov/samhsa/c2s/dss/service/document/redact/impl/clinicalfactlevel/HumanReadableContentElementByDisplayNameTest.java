package gov.samhsa.c2s.dss.service.document.redact.impl.clinicalfactlevel;

import gov.samhsa.c2s.brms.domain.ClinicalFact;
import gov.samhsa.c2s.brms.domain.FactModel;
import gov.samhsa.c2s.brms.domain.RuleExecutionContainer;
import gov.samhsa.c2s.common.document.accessor.DocumentAccessor;
import gov.samhsa.c2s.common.document.accessor.DocumentAccessorImpl;
import gov.samhsa.c2s.common.document.converter.DocumentXmlConverter;
import gov.samhsa.c2s.common.document.converter.DocumentXmlConverterImpl;
import gov.samhsa.c2s.common.filereader.FileReader;
import gov.samhsa.c2s.common.filereader.FileReaderImpl;
import gov.samhsa.c2s.common.marshaller.SimpleMarshaller;
import gov.samhsa.c2s.common.marshaller.SimpleMarshallerException;
import gov.samhsa.c2s.common.marshaller.SimpleMarshallerImpl;
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
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class HumanReadableContentElementByDisplayNameTest {

    public static final String TEST_PATH = "sampleC32-redactionHandlers/";
    public static final String FACTMODEL_PATH = "factmodel/";
    public static final String RULEEXECUTIONCONTAINER_PATH = "ruleexecutioncontainer/";

    private FileReader fileReader;
    private SimpleMarshaller marshaller;
    private DocumentAccessor documentAccessor;
    private DocumentXmlConverter documentXmlConverter;
    private EmbeddedClinicalDocumentExtractor embeddedClinicalDocumentExtractor;

    private static ValueSetService valueSetService;

    private HumanReadableContentElementByDisplayName sut;

    @Before
    public void setUp() throws Exception {
        fileReader = new FileReaderImpl();
        marshaller = new SimpleMarshallerImpl();
        documentAccessor = new DocumentAccessorImpl();
        documentXmlConverter = new DocumentXmlConverterImpl();
        embeddedClinicalDocumentExtractor = new EmbeddedClinicalDocumentExtractorImpl(documentXmlConverter, documentAccessor);
        valueSetService = new ValueSetServiceImplMock(fileReader);
        sut = new HumanReadableContentElementByDisplayName(documentAccessor);
    }

    @Test
    public void testExecute() throws IOException, SimpleMarshallerException, XPathExpressionException {
        // Arrange
        String c32FileName = "MIE_SampleC32.xml";
        String factmodelXml = fileReader.readFile(TEST_PATH + FACTMODEL_PATH + c32FileName);
        String c32 = embeddedClinicalDocumentExtractor.extractClinicalDocumentFromFactModel(factmodelXml);
        String ruleExecutionContainerXml = fileReader.readFile(TEST_PATH + RULEEXECUTIONCONTAINER_PATH + c32FileName);
        RuleExecutionContainer ruleExecutionContainer = marshaller.unmarshalFromXml(RuleExecutionContainer.class, ruleExecutionContainerXml);
        Document c32Document = documentXmlConverter.loadDocument(c32);
        Document factModelDocument = documentXmlConverter.loadDocument(factmodelXml);
        FactModel factModel = marshaller.unmarshalFromXml(FactModel.class, factmodelXml);
        ClinicalFact fact = factModel.getClinicalFactList().get(8);
        Set<String> valueSetCategories = new HashSet<>();
        valueSetCategories.add("HIV");
        fact.setValueSetCategories(valueSetCategories);
        fact.setDisplayName("HYPERTENSION");

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
        final RedactionHandlerResult response = sut.execute(c32Document, factModel.getXacmlResult(), factModel,
                factModelDocument, fact, ruleExecutionContainer, pdpObligationsComplementSetDto);

        // Assert
        assertEquals(2, response.getRedactNodeList().size());
        assertEquals(1, response.getRedactSectionCodesAndGeneratedEntryIds().size());
        assertEquals(1, response.getRedactCategorySet().size());
        assertEquals("content", response.getRedactNodeList().stream().filter(node -> node.getNodeType() == Node.ELEMENT_NODE).map(Node::getNodeName).findAny().orElseThrow(AssertionError::new));
        assertEquals("CondRefId_0_9", response.getRedactNodeList().stream().filter(node -> node.getNodeType() == Node.ELEMENT_NODE).map(Node::getAttributes).map(node -> node.getNamedItem("ID")).map(Node::getNodeValue).findAny().orElseThrow(AssertionError::new));
        assertEquals(Short.valueOf(Node.TEXT_NODE), response.getRedactNodeList().stream().filter(node -> node.getNodeType() == Node.TEXT_NODE).map(Node::getNodeType).findAny().orElseThrow(AssertionError::new));
        assertEquals(" , ", response.getRedactNodeList().stream().filter(node -> node.getNodeType() == Node.TEXT_NODE).map(Node::getNodeValue).findAny().orElseThrow(AssertionError::new));
        assertEquals("d1e282", response.getRedactSectionCodesAndGeneratedEntryIds().toArray()[0]);
        assertEquals("HIV", response.getRedactCategorySet().toArray()[0]);
    }
}
