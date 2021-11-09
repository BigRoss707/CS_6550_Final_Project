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

	@Override
	public ScoredDocument[] execute(Node arg0, Parameters arg1) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}
}
