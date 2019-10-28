package eu.europa.esig.dss.xades.extension;

import eu.europa.esig.dss.spi.x509.tsp.TSPSource;

public class XAdESExtensionTToLTSelfSignedTSATest extends XAdESExtensionTToLTTest {

	@Override
	protected TSPSource getUsedTSPSourceAtSignatureTime() {
		return getSelfSignedTsa();
	}

}