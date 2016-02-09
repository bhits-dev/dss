/*******************************************************************************
 * Open Behavioral Health Information Technology Architecture (OBHITA.org)
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * * Neither the name of the <organization> nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package gov.samhsa.mhc.dss.service.document.redact.impl.postredactionlevel;

import java.util.List;
import java.util.Set;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import gov.samhsa.mhc.brms.domain.FactModel;
import gov.samhsa.mhc.brms.domain.RuleExecutionContainer;
import gov.samhsa.mhc.brms.domain.XacmlResult;
import gov.samhsa.mhc.common.document.accessor.DocumentAccessor;
import gov.samhsa.mhc.dss.service.document.redact.base.AbstractPostRedactionLevelRedactionHandler;

/**
 * The Class DocumentCleanupForDocumentationOfNoServiceEvent.
 */
public class DocumentCleanupForDocumentationOfNoServiceEvent extends
        AbstractPostRedactionLevelRedactionHandler {

    /**
     * The Constant XPATH_DOCUMENTATIONOF_WITH_NO_SERVICE_EVENT.
     */
    public static final String XPATH_DOCUMENTATIONOF_WITH_NO_SERVICE_EVENT = "/hl7:ClinicalDocument/hl7:documentationOf[not(hl7:serviceEvent)]";

    /**
     * Instantiates a new document cleanup for documentation of no service
     * event.
     *
     * @param documentAccessor the document accessor
     */
    public DocumentCleanupForDocumentationOfNoServiceEvent(
            DocumentAccessor documentAccessor) {
        super(documentAccessor);
    }

    /*
     * (non-Javadoc)
     *
     * @see gov.samhsa.mhc.dss.service.document.redact.base.
     * AbstractPostRedactionLevelRedactionHandler#execute(org.w3c.dom.Document,
     * gov.samhsa.mhc.brms.domain.XacmlResult,
     * gov.samhsa.mhc.brms.domain.FactModel, org.w3c.dom.Document,
     * gov.samhsa.mhc.brms.domain.RuleExecutionContainer, java.util.List,
     * java.util.Set)
     */
    @Override
    public void execute(Document xmlDocument, XacmlResult xacmlResult,
                        FactModel factModel, Document factModelDocument,
                        RuleExecutionContainer ruleExecutionContainer,
                        List<Node> listOfNodes,
                        Set<String> redactSectionCodesAndGeneratedEntryIds)
            throws XPathExpressionException {
        NodeList documentationOfElements = documentAccessor.getNodeList(
                xmlDocument, XPATH_DOCUMENTATIONOF_WITH_NO_SERVICE_EVENT);
        if (documentationOfElements != null) {
            for (int i = 0; i < documentationOfElements.getLength(); i++) {
                Node documentationOfElement = documentationOfElements.item(i);
                documentationOfElement.getParentNode().removeChild(
                        documentationOfElement);
            }
        }
    }
}
