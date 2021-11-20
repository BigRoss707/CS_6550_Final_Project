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

public class PositionalRelevanceModel implements ExpansionModel{
	protected Retrieval retrieval;
	int defaultFbDocs, defaultFbTerms;
	double defaultFbOrigWeight;
    Set <String> exclusionTerms;
    Stemmer stemmer;
	
    public PositionalRelevanceModel(Retrieval r) throws IOException {
    	retrieval = r;
        defaultFbDocs = (int) Math.round(r.getGlobalParameters().get("fbDocs", 10.0));
        defaultFbTerms = (int) Math.round(r.getGlobalParameters().get("fbTerm", 100.0));
        defaultFbOrigWeight = r.getGlobalParameters().get("fbOrigWeight", 0.2);
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
	
	public Node generateExpansionQuery(List<WeightedTerm> weightedTerms, int fbTerms) throws IOException, Exception {
        Node expNode = new Node("combine");
        System.err.println("Feedback Terms:");
        for (int i = 0; i < Math.min(weightedTerms.size(), fbTerms); i++) {
          Node expChild = new Node("text", weightedTerms.get(i).getTerm());
          expNode.addChild(expChild);
          expNode.getNodeParameters().set("" + i, weightedTerms.get(i).getWeight());
        }
        return expNode;
    }
	
	public Node interpolate (Node root, Node expandedQuery, Parameters queryParameters) throws Exception{
        queryParameters.set("defaultFbOrigWeight", defaultFbOrigWeight);
        queryParameters.set("fbOrigWeight", queryParameters.get("fbOrigWeight", defaultFbOrigWeight));
        return linearInterpolation(root, expandedQuery, queryParameters);
    }
	
	public Node linearInterpolation (Node root, Node expNode, Parameters parameters) throws Exception{
        double defaultFbOrigWeight = parameters.get("defaultFbOrigWeight", -1.0);
        if (defaultFbOrigWeight < 0)
            throw new Exception ("There is not defaultFbOrigWeight parameter value");
        double fbOrigWeight = parameters.get("fbOrigWeight", defaultFbOrigWeight);
        if (fbOrigWeight == 1.0) {
            return root;
        }
        Node result = new Node("combine");
        result.addChild(root);
        result.addChild(expNode);
        result.getNodeParameters().set("0", fbOrigWeight);
        result.getNodeParameters().set("1", 1.0 - fbOrigWeight);
        return result;
    }
	
	public int getFbDocCount (Node root, Parameters queryParameters) throws Exception{
        int fbDocs = (int)Math.round(root.getNodeParameters().get("fbDocs", queryParameters.get("fbDocs", (double) defaultFbDocs)));
        if (fbDocs <= 0)
            throw new Exception ("Invalid number of feedback documents!");
        return fbDocs;
    }
	
	public int getFbTermCount (Node root, Parameters queryParameters) throws Exception{
        int fbTerms = (int) Math.round(root.getNodeParameters().get("fbTerm", queryParameters.get("fbTerm", (double) defaultFbTerms)));
        if (fbTerms <= 0)
            throw new Exception ("Invalid number of feedback terms!");
        return fbTerms;
    }
	
	public Double getDocumentCount(FeedbackData feedbackData, String term, ScoredDocument doc)
    {
		
    	Map<String, Map<ScoredDocument, Integer>> tc = feedbackData.getTermCounts();
    	
    	//The term does not exist in any document
    	if(!tc.containsKey(term))
    	{
    		return (double)0;
    	}
    	
    	Map<ScoredDocument, Integer> tct = tc.get(term);    	
    	Map<ScoredDocument, Integer> dl = feedbackData.getDocLength();
    	
    	//The term does not exist in the document we want
    	if(!tct.containsKey(doc))
    	{
    		return (double)0;
    	}
    	
    	double termCount = (double)tct.get(doc);
    	double docLength = (double)dl.get(doc);
    	return termCount/docLength;
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

    public Parameters getFbParameters (Node root, Parameters queryParameters) throws Exception{
        Parameters fbParams = Parameters.create();
        fbParams.set("requested", getFbDocCount(root, queryParameters));
        fbParams.set("passageQuery", false);
        fbParams.set("extentQuery", false);
        fbParams.setBackoff(queryParameters);
        return fbParams;
    }
    
	@Override
	public Node expand(Node root, Parameters queryParameters) throws Exception {
		int fbTerms = getFbTermCount(root, queryParameters); 
		//transform the query to ensure it will run
		Parameters fbParams = getFbParameters(root, queryParameters); //Fb params just coalesces the default parameters
        Node transformed = retrieval.transformQuery(root.clone(), fbParams);
		
        // get some initial results
        List<ScoredDocument> initialResults = collectInitialResults(transformed, fbParams);
        
        // extract grams from results
        Set<String> queryTerms = getTerms(stemmer, StructuredQuery.findQueryTerms(transformed));
        FeedbackData feedbackData = new FeedbackData(retrieval, exclusionTerms, initialResults, fbParams);
        List <WeightedTerm> weightedTerms = computeWeights(feedbackData, fbParams, queryParameters, queryTerms);
        Collections.sort(weightedTerms);
        Node expNode = generateExpansionQuery(weightedTerms, fbTerms);
        
        return interpolate(root, expNode, queryParameters);
	}
	
	public List <WeightedTerm> computeWeights (FeedbackData feedbackData, Parameters fbParam, Parameters queryParameters, Set <String> queryTerms) throws Exception{
		//TODO Implement to use the positional relevance model
		
		List<WeightedTerm> resultTerms = new ArrayList<WeightedTerm>();
		List<ScoredDocument> initialResults = feedbackData.getInitialResults();		
		Set<String> terms = getTerms(stemmer, feedbackData.getTermCounts().keySet());
		
		//Query Likelihood
        for(String term : terms)
        {
        	double pt = 0;
	        //RM 1 Evaluation
	        for(int i = 0; i < initialResults.size(); i++)
	        {
	        	//Note, I did not remove stop words from orderedTerms, the above list of terms "terms" does remove stop words
	        	List<String> docTerms = feedbackData.orderedTerms.get(initialResults.get(i));
	        	for(int j = 0; j < docTerms.size(); j++)
	        	{
	        		if(docTerms.get(j).equals(term))
	        		{
		        		double pqdi = GetPQDI(); //TODO Implement as a method
		        		double pwdi = 1;
		        		double docSize = docTerms.size();
		        		
		        		pt = pt + (pqdi * pwdi) / docSize;
	        		}
	        	}
	        }
	        resultTerms.add((WeightedTerm)(new WeightedUnigram(term, pt)));
        }
        
        return resultTerms;
	}
	
	//Positional Query likelihood score
	public Double GetPQDI()
	{
		double result = 0;
		
		//TODO
		//See section 3.2.3 for implementation details
		//Parameters will need to be added such as a running word count
		
		return result;
	}
}
