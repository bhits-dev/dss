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

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class HumanReadableTableRowByDisplayNameTest {

    public static final String TEST_PATH = "sampleC32-redactionHandlers/";
    public static final String FACTMODEL_PATH = "factmodel/";
    public static final String RULEEXECUTIONCONTAINER_PATH = "ruleexecutioncontainer/";

    private FileReader fileReader;
    private SimpleMarshaller marshaller;
    private DocumentAccessor documentAccessor;
    private DocumentXmlConverter documentXmlConverter;
    private EmbeddedClinicalDocumentExtractor embeddedClinicalDocumentExtractor;

    private static ValueSetService valueSetService;

    private HumanReadableTableRowByDisplayName sut;

    @Before
    public void setUp() throws Exception {
        fileReader = new FileReaderImpl();
        marshaller = new SimpleMarshallerImpl();
        documentAccessor = new DocumentAccessorImpl();
        documentXmlConverter = new DocumentXmlConverterImpl();
        embeddedClinicalDocumentExtractor = new EmbeddedClinicalDocumentExtractorImpl(documentXmlConverter, documentAccessor);
        valueSetService = new ValueSetServiceImplMock(fileReader);
        sut = new HumanReadableTableRowByDisplayName(documentAccessor);
    }

    @Test
    public void testExecute() throws IOException, SimpleMarshallerException, XPathExpressionException {
        // Arrange
        String c32FileName = "SMART_C32_Sample.xml";
        String factmodelXml = fileReader.readFile(TEST_PATH + FACTMODEL_PATH + c32FileName);
        String c32 = embeddedClinicalDocumentExtractor.extractClinicalDocumentFromFactModel(factmodelXml);
        String ruleExecutionContainerXml = fileReader.readFile(TEST_PATH + RULEEXECUTIONCONTAINER_PATH + c32FileName);
        RuleExecutionContainer ruleExecutionContainer = marshaller.unmarshalFromXml(RuleExecutionContainer.class, ruleExecutionContainerXml);
        Document c32Document = documentXmlConverter.loadDocument(c32);
        Document factModelDocument = documentXmlConverter.loadDocument(factmodelXml);
        FactModel factModel = marshaller.unmarshalFromXml(FactModel.class, factmodelXml);
        ClinicalFact fact = factModel.getClinicalFactList().get(1);
        Set<String> valueSetCategories = new HashSet<>();
        valueSetCategories.add("HIV");
        fact.setValueSetCategories(valueSetCategories);
        fact.setDisplayName("TYPHOID");

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
        assertEquals(1, response.getRedactNodeList().size());
        assertEquals(1, response.getRedactSectionCodesAndGeneratedEntryIds().size());
        assertEquals(1, response.getRedactCategorySet().size());
        assertEquals("tr", response.getRedactNodeList().get(0).getNodeName());
        assertEquals("d15e350", response.getRedactNodeList().get(0).getAttributes().getNamedItem("ID").getNodeValue());
        assertEquals("d1e130", response.getRedactSectionCodesAndGeneratedEntryIds().toArray()[0]);
        assertEquals("HIV", response.getRedactCategorySet().toArray()[0]);
    }
}
