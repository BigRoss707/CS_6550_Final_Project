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
        queries.add(Parameters.parseString(String.format("{\"number\":\"%s\", \"text\":\"%s\"}", "301", "International Organized Crime")));
        
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
          
            // run query
            //List<ScoredDocument> results = retrieval.executeQuery(transformed, query).scoredDocuments;
            List<ScoredDocument> results = new ArrayList<ScoredDocument>(Arrays.asList(model.execute(transformed, query)));
            
            // print results
            resultWriter.write(queryNumber, results);
        }
        resultWriter.close();
    }

	
	private static void count_word(String pathIndexBase, String term, String model,String docno)throws Exception {
		 File pathPosting = new File( new File( pathIndexBase ), model);
		 DiskIndex index = new DiskIndex( pathIndexBase );
		 IndexPartReader posting = DiskIndex.openIndexPart( pathPosting.getAbsolutePath() );
		 KeyIterator vocabulary = posting.getIterator();
//		 System.out.println(term);
		 if ( vocabulary.skipToKey( ByteUtil.fromString( term ) ) && term.equals( vocabulary.getKeyString() ) ) {
			    // get an iterator for the term's posting list
			    CountIterator iterator = (CountIterator) vocabulary.getValueIterator();
			    ScoringContext sc = new ScoringContext();
			    
			    while ( !iterator.isDone() ) {
			        // Get the current entry's document id.
			        // Note that you need to assign the value of sc.document,
			        // otherwise count(sc) and others will not work correctly.
			        sc.document = iterator.currentCandidate();
			        
			        String docno_cur = index.getName( sc.document ); // get the docno (external ID) of the current document
			        if (docno_cur.equals(docno))// check if current document is the retrieved document or not. 
					{
			        int lenghth=index.getLength(sc.document);// return the lenghth of retrieved document
			        int freq = iterator.count( sc ); // return the lenghth of term count in retrieved document
			        System.out.printf( "%-20s%-15s%-15s%-10s\n", term, lenghth, docno, freq );

			       break;
			    }
			    iterator.movePast( iterator.currentCandidate() ); }// jump to next document until we find the retrieved document}
			}		 
			posting.close();
			index.close();}
   private static void runQuery(Parameters p, String qid, String query, Retrieval retrieval, BufferedWriter writer, String model, String smoothing,String pathIndexBase) throws Exception {
       //Parameters p = new Parameters();
       //p.set("startAt", 0); // set the start point for document retrieval. 0 means retrieving from all documents.
       p.set("requested", 2); // set the maximum number of document retrieved for each query.
       //        p.set(key, value);
       p.set("scorer", "jm");  // set JM smoothing method
       p.set("lambda", 0.5);   // set the parameters in JM method.

       String query_orig=query;
       if (model.length() > 0) {
           if (smoothing.length() > 0) {
               String[] terms = query.split(" ");
               query = "";
               for (String t : terms) {
                   query +="#extents:part="+model+":"+ t + "() ";
               }
           }
           query = "#combine" + "(" + query + ")"; // apply the retrieval model to the query if exists
       }
       Node root = StructuredQuery.parse(query);       // turn the query string into a query tree
       System.out.println(root.toString());
       Node transformed = retrieval.transformQuery(root, p);  // apply traversals
       System.out.println(transformed.toString());
       List<ScoredDocument> results = retrieval.executeQuery(transformed, p).scoredDocuments; // issue the query!
       System.out.println("****************");
       for(ScoredDocument sd:results){ // print results

       	String docno=sd.getName();  // get current retrieved document's DOCNO
       	String[] terms = query_orig.split(" ");
       	System.out.println("*************");
       	System.out.println(sd.toString());// can print the score from the model.
       	System.out.printf( "%-20s%-15s%-15s%-10s\n", "Word","DOC_Length", "DOCNO", "FREQ" );
       	for (String t : terms){
       	count_word(pathIndexBase,t,model,docno);  // print the term count for each retrieved document.
       	}                                           
       	System.out.println("*************");  // You can check calculate the score by yourself with term count, document lenghth.
												  //Please check it matches the score from galago matches your calculation or not.
           writer.write(sd.toTRECformat(qid));
           writer.write("\n");
       }
   }
   
}
