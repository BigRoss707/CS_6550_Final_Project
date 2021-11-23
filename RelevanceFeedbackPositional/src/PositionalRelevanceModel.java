import java.io.IOException;
import java.lang.Math;
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
	double sigma, lambda;
    Set <String> exclusionTerms;
    Stemmer stemmer;
	
    public PositionalRelevanceModel(Retrieval r) throws IOException {
    	retrieval = r;
        defaultFbDocs = (int) Math.round(r.getGlobalParameters().get("fbDocs", 10.0));
        defaultFbTerms = (int) Math.round(r.getGlobalParameters().get("fbTerm", 100.0));
        sigma = r.getGlobalParameters().get("sigma", 2000.0);
        lambda = r.getGlobalParameters().get("lambda", 0.5);
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
    	//get corpus size
    	Map<ScoredDocument, Integer> dl = feedbackData.getDocLength();
    	double docCount = 0;
    	for(Integer i : dl.values())
    	{
    		docCount += i;
    	}	
    	
    	Map<String, Map<ScoredDocument, Integer>> tc = feedbackData.getTermCounts();
    	
    	//The term does not exist in any document
    	if(!tc.containsKey(term))
    	{
    		return (double)1/docCount; //Handling the cases where the term does not exist in documents
    	}
    	
    	Map<ScoredDocument, Integer> tct = tc.get(term);    	  	
    	
    	//The term does not exist in the document we want
    	if(tct == null)
    	{
    		return (double)1/docCount; //Handling the cases where the term does not exist in documents
    	}
    	
    	double termCount = 0;
    	for(ScoredDocument doc : tct.keySet())
    	{
    		termCount += tct.get(doc);
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
		List<WeightedTerm> resultTerms = new ArrayList<WeightedTerm>();
		List<ScoredDocument> initialResults = feedbackData.getInitialResults();		
		Set<String> terms = getTerms(stemmer, feedbackData.getTermCounts().keySet());
		List<String> listQueryTerms = new ArrayList<String>();
		for(String q : queryTerms)
		{
			listQueryTerms.add(q);
		}
		
		//Query Likelihood
        for(String term : terms)
        {
        	//we don't want to calculate the weights for query terms
        	if(queryTerms.contains(term) || exclusionTerms.contains(term))
        	{
        		continue;
        	}
        	
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
		        		double pqdi = GetPQDI(j,docTerms,listQueryTerms,feedbackData,lambda,sigma);
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
	public Double GetPQDI(int i, List<String> docTerms, List<String> queryTerms, FeedbackData data, double lambda, double sigma)
	{
		double result = 1.0;
		
		
		for (int j = 0; j < queryTerms.size(); j++)
		{
			if(exclusionTerms.contains(queryTerms.get(j)))
			{
				continue;
			}
			
			result = result * lambdaPQDI(i, queryTerms.get(j), docTerms, data, lambda, sigma);
		}
		
		return result;
	}
	
	private double lambdaPQDI(int i, String queryTerm, List<String> docTerms, FeedbackData data, double lambda, double sigma) {
		return (1.0 - lambda) * PQDI(i, queryTerm, docTerms, sigma) + lambda * getCorpusCount(data, queryTerm);
	}
	
	private double PQDI(int i, String queryTerm, List<String> docTerms, double sigma) {
		return CpQI(i, queryTerm, docTerms, sigma) / (Math.sqrt(2 * Math.PI * Math.pow(sigma,2)));
	}
	
	private double CpQI(int i, String queryTerm, List<String> docTerms, double sigma){
		double score = 0.0;
		
		for (int j = 0; j < docTerms.size(); j++){
			if(CQJ(j,queryTerm,docTerms)){
				score += Math.exp((-Math.pow((double)i - (double) j, 2)/ (2 * Math.pow(sigma,2))));
			}
		}
		
		return score;
	}
	
	//if query term exists in index i in document docTerms
	private boolean CQJ(int i, String queryTerm, List<String> docTerms){
		return queryTerm.equals(docTerms.get(i));
	}
}
