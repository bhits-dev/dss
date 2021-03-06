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
package gov.samhsa.c2s.dss.service.document;

import gov.samhsa.c2s.common.document.transformer.XmlTransformer;
import gov.samhsa.c2s.common.log.Logger;
import gov.samhsa.c2s.common.log.LoggerFactory;
import gov.samhsa.c2s.common.marshaller.SimpleMarshaller;
import gov.samhsa.c2s.common.util.StringURIResolver;
import gov.samhsa.c2s.dss.config.DocumentTaggerConfig;
import gov.samhsa.c2s.dss.service.exception.DocumentSegmentationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.transform.URIResolver;
import java.util.Optional;

/**
 * The Class DocumentTaggerImpl.
 */
@Service
public class DocumentTaggerImpl implements DocumentTagger {

    /**
     * The Constant URI_RESOLVER_HREF_RULE_EXECUTION_RESPONSE_CONTAINER.
     */
    private static final String URI_RESOLVER_HREF_RULE_EXECUTION_RESPONSE_CONTAINER = "ruleExecutionResponseContainer";

    /**
     * The Constant TAG_XSL.
     */
    private static final String TAG_XSL = "tag.xsl";

    /**
     * The logger.
     */
    private final Logger logger = LoggerFactory
            .getLogger(this.getClass());

    /**
     * The xml transformer.
     */
    @Autowired
    private XmlTransformer xmlTransformer;

    @Autowired
    private SimpleMarshaller marshaller;

    @Autowired
    private DocumentTaggerConfig documentTaggerConfig;

    @Override
    public String tagDocument(String document, String executionResponseContainer) {
        try {
            executionResponseContainer = executionResponseContainer.replace(
                    "<ruleExecutionContainer>",
                    "<ruleExecutionContainer xmlns=\"urn:hl7-org:v3\">");
            final String xslUrl = Thread.currentThread().getContextClassLoader()
                    .getResource(TAG_XSL).toString();
            final StringURIResolver stringURIResolver = new StringURIResolver();
            stringURIResolver.put(
                    URI_RESOLVER_HREF_RULE_EXECUTION_RESPONSE_CONTAINER,
                    executionResponseContainer);
            final String additionalCustomSections = marshaller.marshal(documentTaggerConfig.getAdditionalSectionsAsCustomSectionList());
            stringURIResolver.put("customSectionList", additionalCustomSections);
            final Optional<URIResolver> uriResolver = Optional
                    .of(stringURIResolver);
            final String taggedDocument = xmlTransformer.transform(document,
                    xslUrl, Optional.empty(), uriResolver);
            logger.debug("Tagged Document:");
            logger.debug(taggedDocument);
            return taggedDocument;
        } catch (final Exception e) {
            throw new DocumentSegmentationException(e.getMessage(), e);
        }
    }
}
