package eu.europa.esig.dss.jades.signature;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.jose4j.json.JsonUtil;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.JWSSerializationType;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.jades.JAdESHeaderParameterNames;
import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.JAdESTimestampParameters;
import eu.europa.esig.dss.jades.JAdESUtils;
import eu.europa.esig.dss.jades.JWSConstants;
import eu.europa.esig.dss.jades.validation.JAdESSignature;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.Policy;
import eu.europa.esig.dss.model.SignaturePolicyStore;
import eu.europa.esig.dss.model.SpDocSpecification;
import eu.europa.esig.dss.signature.DocumentSignatureService;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.AdvancedSignature;

public class JAdESLevelTWithSignaturePolicyStoreTest extends AbstractJAdESTestSignature {

	private static final String HTTP_SPURI_TEST = "http://spuri.test";
	private static final String SIGNATURE_POLICY_ID = "1.2.3.4.5.6";
	private static final String SIGNATURE_POLICY_DESCRIPTION = "Test description";
	private static final DSSDocument SIGNATURE_POLICY_CONTENT = new InMemoryDocument("Hello world".getBytes());
	private static final String[] DOCUMENTATION_REFERENCES = new String[] { "http://docref.com/ref1", "http://docref.com/ref2" };

	private JAdESService service;
	private DSSDocument documentToSign;
	private JAdESSignatureParameters signatureParameters;

	@BeforeEach
	public void init() {
		service = new JAdESService(getCompleteCertificateVerifier());
		service.setTspSource(getGoodTsa());

		documentToSign = new FileDocument(new File("src/test/resources/sample.json"));
		signatureParameters = new JAdESSignatureParameters();
		signatureParameters.setSigningCertificate(getSigningCert());
		signatureParameters.setCertificateChain(getCertificateChain());
		signatureParameters.setSignaturePackaging(SignaturePackaging.ENVELOPING);
		signatureParameters.setJwsSerializationType(JWSSerializationType.JSON_SERIALIZATION);
		signatureParameters.setSignatureLevel(SignatureLevel.JAdES_BASELINE_T);
		
		Policy signaturePolicy = new Policy();
		signaturePolicy.setId("urn:oid:" + SIGNATURE_POLICY_ID);
		signaturePolicy.setDescription(SIGNATURE_POLICY_DESCRIPTION);
		signaturePolicy.setDigestAlgorithm(DigestAlgorithm.SHA256);
		signaturePolicy.setDigestValue(DSSUtils.digest(DigestAlgorithm.SHA256, SIGNATURE_POLICY_CONTENT));
		signaturePolicy.setSpuri(HTTP_SPURI_TEST);
		
		signatureParameters.bLevel().setSignaturePolicy(signaturePolicy);
	}
	
	@Override
	protected DSSDocument sign() {
		DSSDocument signedDocument = super.sign();
		
		SignaturePolicyStore signaturePolicyStore = new SignaturePolicyStore();
		signaturePolicyStore.setSignaturePolicyContent(SIGNATURE_POLICY_CONTENT);
		SpDocSpecification spDocSpec = new SpDocSpecification();
		spDocSpec.setId("urn:oid:" + SIGNATURE_POLICY_ID);
		spDocSpec.setDescription(SIGNATURE_POLICY_DESCRIPTION);
		spDocSpec.setDocumentationReferences(DOCUMENTATION_REFERENCES);
		signaturePolicyStore.setSpDocSpecification(spDocSpec);
		
		DSSDocument signedDocumentWithSignaturePolicyStore = service.addSignaturePolicyStore(signedDocument, signaturePolicyStore);
		assertNotNull(signedDocumentWithSignaturePolicyStore);
		
		return signedDocumentWithSignaturePolicyStore;
	}
	
	@Override
	protected void verifyDiagnosticData(DiagnosticData diagnosticData) {
		super.verifyDiagnosticData(diagnosticData);
		
		SignatureWrapper signature = diagnosticData.getSignatureById(diagnosticData.getFirstSignatureId());
		assertEquals(HTTP_SPURI_TEST, signature.getPolicyUrl());
		assertEquals(SIGNATURE_POLICY_ID, signature.getPolicyId());
		assertEquals(SIGNATURE_POLICY_DESCRIPTION, signature.getPolicyDescription());
	}
	
	@Override
	protected void onDocumentSigned(byte[] byteArray) {
		super.onDocumentSigned(byteArray);
		
		DSSDocument signedDocumentWithSignaturePolicyStore = new InMemoryDocument(byteArray);
		assertRequirementsValid(signedDocumentWithSignaturePolicyStore);
	}
	
	@Override
	protected void checkAdvancedSignatures(List<AdvancedSignature> signatures) {
		super.checkAdvancedSignatures(signatures);
		
		assertEquals(1, signatures.size());
		JAdESSignature jadesSignature = (JAdESSignature) signatures.get(0);
		SignaturePolicyStore extractedSPS = jadesSignature.getSignaturePolicyStore();
		assertNotNull(extractedSPS);
		assertNotNull(extractedSPS.getSpDocSpecification());
		assertEquals(SIGNATURE_POLICY_DESCRIPTION, extractedSPS.getSpDocSpecification().getDescription());
		assertArrayEquals(DOCUMENTATION_REFERENCES, extractedSPS.getSpDocSpecification().getDocumentationReferences());
		assertArrayEquals(DSSUtils.toByteArray(SIGNATURE_POLICY_CONTENT), DSSUtils.toByteArray(extractedSPS.getSignaturePolicyContent()));
	}
	
	private void assertRequirementsValid(DSSDocument documentWithPolicyStore) {
		try {
			Map<String, Object> jsonMap = JsonUtil.parseJson(new String(DSSUtils.toByteArray(documentWithPolicyStore)));
			List<?> signaturesList = (List<?>) jsonMap.get("signatures");
			assertEquals(1, signaturesList.size());
			Map<?, ?> signature = (Map<?, ?>) signaturesList.get(0);

			String jsonString = new String(JAdESUtils.fromBase64Url((String) signature.get(JWSConstants.PROTECTED)));
			Map<String, Object> protectedHeaderMap = JsonUtil.parseJson(jsonString);
			
			Map<?, ?> unprotectedHeaderMap = (Map<?, ?>) signature.get(JWSConstants.HEADER);
			List<?> etsiU = (List<?>) unprotectedHeaderMap.get(JAdESHeaderParameterNames.ETSI_U);
			
			Map<?, ?> sigPSt = null;
			for (Object etsiUItem : etsiU) {
				sigPSt = (Map<?, ?>) ((Map<?, ?>) etsiUItem).get("sigPSt");
				if (sigPSt != null) {
					break;
				}
			}
			assertNotNull(sigPSt);
			
			Map<?, ?> sigPId = (Map<?, ?>) protectedHeaderMap.get("sigPId");
			assertNotNull(sigPId);
			assertNotNull(sigPId.get("id"));
			Map<?, ?> hashAV = (Map<?, ?>) sigPId.get("hashAV");
			if (hashAV != null) {
				String digAlg = (String) hashAV.get("digAlg");
				assertNotNull(digAlg);
				String digVal = (String) hashAV.get("digVal");
				assertNotNull(digVal);
			}
			
		} catch (JoseException e) {
			fail(e);
		}
	}
	
	@Override
	protected void checkSignaturePolicyIdentifier(DiagnosticData diagnosticData) {
		super.checkSignaturePolicyIdentifier(diagnosticData);

		SignatureWrapper signature = diagnosticData.getSignatureById(diagnosticData.getFirstSignatureId());
		assertTrue(signature.isPolicyPresent());
		
		assertEquals(HTTP_SPURI_TEST, signature.getPolicyUrl());
		assertEquals(SIGNATURE_POLICY_ID, signature.getPolicyId());
		assertEquals(SIGNATURE_POLICY_DESCRIPTION, signature.getPolicyDescription());
		
		assertFalse(signature.isPolicyAsn1Processable());
		assertTrue(signature.isPolicyIdentified());
		assertTrue(signature.isPolicyStatus());
		assertTrue(signature.isPolicyDigestAlgorithmsEqual());
	}
	
	@Override
	protected void checkSignaturePolicyStore(DiagnosticData diagnosticData) {
		super.checkSignaturePolicyStore(diagnosticData);
		
		SignatureWrapper signature = diagnosticData.getSignatureById(diagnosticData.getFirstSignatureId());
		assertEquals(SIGNATURE_POLICY_ID, signature.getPolicyStoreId());
		assertEquals(SIGNATURE_POLICY_DESCRIPTION, signature.getPolicyStoreDescription());
		assertEquals(2, signature.getPolicyStoreDocumentationReferences().size());
		assertEquals(DOCUMENTATION_REFERENCES[0], signature.getPolicyStoreDocumentationReferences().get(0));
		assertEquals(DOCUMENTATION_REFERENCES[1], signature.getPolicyStoreDocumentationReferences().get(1));
		
		assertNotNull(signature.getPolicyStoreDigestAlgoAndValue());
		assertNotNull(signature.getPolicyStoreDigestAlgoAndValue().getDigestMethod());
		assertTrue(Utils.isArrayNotEmpty(signature.getPolicyStoreDigestAlgoAndValue().getDigestValue()));
	}

	@Override
	protected JAdESSignatureParameters getSignatureParameters() {
		return signatureParameters;
	}

	@Override
	protected DSSDocument getDocumentToSign() {
		return documentToSign;
	}

	@Override
	protected DocumentSignatureService<JAdESSignatureParameters, JAdESTimestampParameters> getService() {
		return service;
	}

	@Override
	protected String getSigningAlias() {
		return GOOD_USER;
	}

}
