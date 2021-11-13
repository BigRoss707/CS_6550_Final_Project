import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lemurproject.galago.core.parse.stem.Stemmer;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.Retrieval;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.prf.ExpansionModel;
import org.lemurproject.galago.core.retrieval.prf.WeightedTerm;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.retrieval.query.StructuredQuery;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.utility.Parameters;

public class RelevanceModel1 extends org.lemurproject.galago.core.retrieval.processing.ProcessingModel{
	protected Retrieval retrieval;
    Set <String> exclusionTerms;
    Stemmer stemmer;
	
    public RelevanceModel1(Retrieval r) throws IOException {
       exclusionTerms = WordLists.getWordList(r.getGlobalParameters().get("rmstopwords", "rmstop"));
       Parameters gblParms = r.getGlobalParameters();
       this.stemmer = getStemmer(gblParms, retrieval);
	}
	
    public List<ScoredDocument> collectInitialResults(Node transformed, Parameters fbParams) throws Exception {
        Results results = retrieval.executeQuery(transformed, fbParams);
        List<ScoredDocument> res = results.scoredDocuments;
        if (res.isEmpty())
            throw new Exception("No feedback documents found!");
        return res;
    }
    
	public static Stemmer getStemmer(Parameters p, Retrieval ret) {
        Stemmer stemmer;
        if (ret.getGlobalParameters().isString("rmStemmer")) {
            String rmstemmer = ret.getGlobalParameters().getString("rmStemmer");
            try {
                stemmer = (Stemmer) Class.forName(rmstemmer).getConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            stemmer = null;
        }
        return stemmer;
    }

	public static Set<String> getTerms(Stemmer stemmer, Set<String> terms) {
      if (stemmer == null)
          return terms;

      Set<String> stems = new HashSet<String>(terms.size());
      for (String t : terms) {
        String s = stemmer.stem(t);
        stems.add(s);
      }
      return stems;
    }
	
	@Override
	public ScoredDocument[] execute(Node root, Parameters queryParameters) throws Exception {
		//transform the query to ensure it will run
		//Parameters fbParams = getFbParameters(root, queryParameters);
        Node transformed = retrieval.transformQuery(root.clone(), queryParameters);
		
        // get some initial results
        List<ScoredDocument> initialResults = collectInitialResults(transformed, queryParameters);
        
        // extract grams from results
        Set<String> queryTerms = getTerms(stemmer, StructuredQuery.findQueryTerms(transformed));
        FeedbackData feedbackData = new FeedbackData(retrieval, exclusionTerms, initialResults, queryParameters);

        
        //Return the list of re-ranked results
        List<ScoredDocument> rankedResults = new ArrayList<ScoredDocument>();
        
        //Query Likelihood
        for(String term : queryTerms)
        {
        	double pt = 0;
	        //RM 1 Evaluation
	        for(int i = 0; i < initialResults.size(); i++)
	        {
	        	//estimate of term frequency in document
	        	double ptd = getDocumentCount(feedbackData, term, initialResults.get(i));
	        	
	        	//normalized term weights?
	        	for(String term_p : queryTerms)
	        	{
	        		ptd = ptd * getDocumentCount(feedbackData, term_p, initialResults.get(i));
	        	}
	        	
	        	pt = pt + ptd;
	        }
        }
        	
        ScoredDocument[] results = new ScoredDocument[rankedResults.size()];
        rankedResults.toArray(results);
		return results;
	}
	
	public Double getDocumentCount(FeedbackData feedbackData, String term, ScoredDocument doc)
    {
    	Map<String, Map<ScoredDocument, Integer>> tc = feedbackData.getTermCounts();
    	Map<ScoredDocument, Integer> tct = tc.get(term);    	
    	Map<ScoredDocument, Integer> dl = feedbackData.getDocLength();
    	
    	return ((double)tct.get(doc)/(double)dl.get(doc));
    }
    
    public Double getCorpusCount(FeedbackData feedbackData, String term){
    	Map<String, Map<ScoredDocument, Integer>> tc = feedbackData.getTermCounts();
    	Map<ScoredDocument, Integer> tct = tc.get(term);    	
    	Map<ScoredDocument, Integer> dl = feedbackData.getDocLength();
    	
    	double termCount = 0;
    	for(ScoredDocument doc : tct.keySet())
    	{
    		termCount += tct.get(doc);
    	}
    	
    	double docCount = 0;
    	for(Integer i : dl.values())
    	{
    		docCount += i;
    	}	
    	
    	return termCount/docCount;
    }
}
