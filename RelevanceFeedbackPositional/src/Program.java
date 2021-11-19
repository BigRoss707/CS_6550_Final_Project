import org.lemurproject.galago.core.index.LengthsReader;
import org.lemurproject.galago.core.index.disk.DiskLengthsReader;
import org.lemurproject.galago.core.retrieval.Retrieval;
import org.lemurproject.galago.core.retrieval.RetrievalFactory;
import org.lemurproject.galago.core.retrieval.processing.ScoringContext;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.retrieval.query.StructuredQuery;
import org.lemurproject.galago.utility.Parameters;
//import org.lemurproject.galago.core.retrieval.RetrievalFactory;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.index.disk.DiskIndex;
import org.lemurproject.galago.core.index.IndexPartReader;
import org.lemurproject.galago.core.index.KeyIterator;
import org.lemurproject.galago.core.retrieval.iterator.CountIterator;
import org.lemurproject.galago.utility.ByteUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.io.File;


public class Program {

	public static void main(String[] args) throws Exception {
		String indexPath = "\\\\wsl$\\Ubuntu\\home\\jkramer\\CS_6550_galago_tutorial\\robust04-complete-index";
        String outputFileName = "\\\\wsl$\\Ubuntu\\home\\jkramer\\CS_6550_galago_tutorial\\batchseachoutput";
        String queryInputFile = "\\\\wsl$\\Ubuntu\\home\\jkramer\\CS_6550_galago_tutorial\\query.titles.tsv";
        int requested = 1000; // number of documents to retrieve at base, We're going to use Galago to do all of our retrieval and then
        //mostly re-rank the retrieved results ourselves. Later on we can see if we can incorporate the index more directly
        boolean append = false;
        
		//OUR CODE
		//TODO RETRIEVE LIST OF FILES IN INDEX
		//TODO RETRIEVE LIST OF TERMS IN FILE WITH INDICES
		//TODO IMPLEMENT RETRIEVAL USING OUR OWN RM1(OR RM3) ALGORITHM
		//TODO IMPLEMENT THE POSITIONAL MODEL USING GALAGO
		// open index
        
        Retrieval retrieval = RetrievalFactory.instance(indexPath, Parameters.create());
		
        //load queries
        //TODO LOAD THESE FROM FILES
        List <Parameters> queries = new ArrayList <> ();
        BufferedReader reader = new BufferedReader(new FileReader(queryInputFile));
        String line = reader.readLine();
        while(line != null)
        {
        	String[] splitLine = line.split("\t");
            queries.add(Parameters.parseString(String.format("{\"number\":\"%s\", \"text\":\"%s\"}", splitLine[0], splitLine[1])));
            //queries.add(Parameters.parseString("{" + line + "}"));

        	line = reader.readLine();
        }
        reader.close();
        //queries.add(Parameters.parseString(String.format("{\"number\":\"%s\", \"text\":\"%s\"}", "301", "International Organized Crime")));
        
        // open output file
        ResultWriter resultWriter = new ResultWriter(outputFileName, append);

        // for each query, run it, get the results, print in TREC format
        for (Parameters query : queries) {
            String queryNumber = query.getString("number");
            String queryText = query.getString("text");
            queryText = queryText.toLowerCase(); // option to fold query cases -- note that some parameters may require upper case
            
            org.lemurproject.galago.core.tools.apps.BatchSearch.logger.info("Processing query #" + queryNumber + ": " + queryText);
            
            query.set("requested", requested);

            Node root = StructuredQuery.parse(queryText);
            Node transformed = retrieval.transformQuery(root, query);
            
            RelevanceModel1 model = new RelevanceModel1(retrieval);
          
            try{
                query.set("fbOrigWeight", 0.5);
                query.set("fbTerm", 100.0);
                Node expandedQuery = model.expand(root.clone(), query.clone());  
                transformed = retrieval.transformQuery(expandedQuery, query);
            } catch (Exception ex){
                ex.printStackTrace();
            }
            
            // run query
            List<ScoredDocument> results = retrieval.executeQuery(transformed, query).scoredDocuments;
            
            // print results
            resultWriter.write(queryNumber, results);
        }
        resultWriter.close();
    }
   
}
