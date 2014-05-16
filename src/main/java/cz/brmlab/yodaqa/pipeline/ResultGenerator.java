package cz.brmlab.yodaqa.pipeline;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.AbstractCas;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.fit.component.JCasMultiplier_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCopier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.brmlab.yodaqa.model.SearchResult.ResultInfo;
import cz.brmlab.yodaqa.provider.solr.SolrNamedSource;

/**
 * Take a question CAS and multiply it to a CAS instance for each SolrResult
 * featureset of the Search view. */

public class ResultGenerator extends JCasMultiplier_ImplBase {
	final Logger logger = LoggerFactory.getLogger(ResultGenerator.class);

	JCas questionView, searchView;

	/* Prepared list of results to return. */
	FSIterator results;
	int i;

	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		try {
			questionView = jcas.getView(CAS.NAME_DEFAULT_SOFA);
			searchView = jcas.getView("Search");
		} catch (Exception e) {
			throw new AnalysisEngineProcessException(e);
		}

		results = searchView.getJFSIndexRepository().getAllIndexedFS(ResultInfo.type);
		i = 0;
	}

	@Override
	public boolean hasNext() throws AnalysisEngineProcessException {
		return results.hasNext();
	}

	@Override
	public AbstractCas next() throws AnalysisEngineProcessException {
		ResultInfo ri = (ResultInfo) results.next();

		JCas jcas = getEmptyJCas();
		try {
			jcas.createView("Question");
			CasCopier qcopier = new CasCopier(questionView.getCas(), jcas.getView("Question").getCas());
			copyQuestion(qcopier, questionView, jcas.getView("Question"));

			jcas.createView("Result");
			CasCopier rcopier = new CasCopier(searchView.getCas(), jcas.getView("Result").getCas());
			fillResult(rcopier, ri, jcas.getView("Result"), !results.hasNext());
		} catch (Exception e) {
			jcas.release();
			throw new AnalysisEngineProcessException(e);
		}
		i++;
		return jcas;
	}


	protected void copyQuestion(CasCopier copier, JCas src, JCas jcas) throws Exception {
		copier.copyCasView(src.getCas(), jcas.getCas(), true);
	}

	protected void fillResult(CasCopier copier, ResultInfo ri, JCas jcas, boolean isLast) throws Exception {
		String title = ri.getDocumentTitle();
		logger.info(" ** SearchResultCAS: " + ri.getDocumentId() + " " + (title != null ? title : ""));

		String text;
		try {
			text = SolrNamedSource.get(ri.getSource()).getDocText(ri.getDocumentId());
		} catch (SolrServerException e) {
			e.printStackTrace();
			return;
		}
		// System.err.println("--8<-- " + text + " --8<--");
		jcas.setDocumentText(text);
		jcas.setDocumentLanguage("en"); // XXX

		ResultInfo ri2 = (ResultInfo) copier.copyFs(ri);
		ri2.setIsLast(isLast);
		ri2.addToIndexes();
	}
}