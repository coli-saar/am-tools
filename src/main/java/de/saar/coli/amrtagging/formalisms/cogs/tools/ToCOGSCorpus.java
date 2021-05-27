package de.saar.coli.amrtagging.formalisms.cogs.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm;
import de.saar.coli.amrtagging.formalisms.cogs.LogicalFormConverter;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

import static org.apache.commons.lang3.math.NumberUtils.min;


/**
 * Converts an AM Dependency corpus (amconll) into COGS format (.tsv) (tokens, logical form, generalization type)
 *
 * Also reports exact match accuracy and average token-level edit distance if gold corpus is given.
 * @author pia (weissenh)
 */
public class ToCOGSCorpus {
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees")//, required = true)
    private String corpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/graphparsing/toy/train.amconll";

    @Parameter(names = {"--outFile", "-o"}, description = "Path for output tsv file")//, required = true)
    private String outPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/graphparsing/predictions.tsv";

    @Parameter(names={"--gold","-g"}, description = "Path to gold corpus. Make sure it contains exactly the same instances, in the same order.")//, required=true)
//    private final String goldCorpus = null; //"/PATH/TO/TSVFILE.tsv";
//    private final String goldCorpus = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/cogsdata/train_100.tsv";
//    private final String goldCorpus = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/cogsdata/train.tsv";
    private String goldCorpus = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/COGS/datasmall/train20.tsv";

    @Parameter(names = {"--verbose"}, description = "if this flag is set, prints more information (to the error stream")
    private boolean verbose=false;

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    public static void main(String[] args) throws IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        ToCOGSCorpus cli = new ToCOGSCorpus();
        JCommander commander = new JCommander(cli);
        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (cli.help) {
            commander.usage();
            return;
        }

        boolean excludePrimitives = true;  // todo make this a command line flag!!!!
        System.out.println("Excluding primitives? " + excludePrimitives);

        List<AmConllSentence> sents = AmConllSentence.readFromFile(cli.corpusPath);

        RawCOGSReader goldReader = null;
        if (cli.goldCorpus != null) {
            System.out.println("Gold file: " + cli.goldCorpus);
            goldReader = new RawCOGSReader(cli.goldCorpus);
        }
        PrintWriter graphWriter = null;
        if (cli.outPath != null) {
            System.out.println("Output file: " + cli.outPath);
            graphWriter = new PrintWriter(cli.outPath);
        }
        if (cli.verbose) {
            System.err.println("Verbosity flag has been set: " +
                    "will print information about gold-prediction mismatches to the error stream!");
        }

        // todo scorer?
        int totalSentencesSeen = 0;
        int totalExactMatches = 0;
        int totalEditDistance = 0;

        for (AmConllSentence amsent : sents) {
            totalSentencesSeen += 1;
            String id = amsent.getAttr("id") != null ? amsent.getAttr("id") : "NO-ID";
            //if (! id.startsWith("#")) id = "#" + id;
            List<String> tokens = amsent.words();  // todo any art-root I have to exclude?

            boolean read = false;
            /*try {*/
            // Step 1: Evaluate predicted AM dep tree to graph
            // AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(amsent);
            // SGraph evaluatedGraph = amdep.evaluate(true);
            // Step 2: Convert this graph to logical form
            COGSLogicalForm predictedLF;
            String predicedLFString = "";  // "INVALIDLF"
            try {
                predictedLF = LogicalFormConverter.toLogicalForm(amsent);
                predicedLFString = predictedLF.toString();
            }
            catch (IllegalArgumentException illegalArgumentException) {  // todo can I use a cogs specific conversion exception instead?
                // Exception in thread "main" java.lang.IllegalArgumentException: Ill-formed logical form? First argument of term can't be proper name.
                // todo maybe not raise exception there?
                System.err.println("Something went wrong during converting the graph back to a COGS logical form");
                System.err.println("Proceed with '"+predicedLFString+"' as dummy logical form output.");
                //System.err.println(illegalArgumentException.getMessage());
                //illegalArgumentException.printStackTrace();
                predictedLF = null;
            }
            // Graph outputSent = SGraphConverter.toSDPGraph(evaluatedGraph, sdpSent); //add edges

            // Step 3: if gold data available: get graph there too and evaluate
            String genType = "";  // used for writing 3rd column in the output file
            if (goldReader != null) {
                // (a) Input validation
                if (!goldReader.hasNext()) {
                    throw new RuntimeException("No more samples in gold reader (but still amconll sentences left!)");
                }
                // read = true;
                RawCOGSReader.CogsSample sample = goldReader.getNextSample();
                genType = sample.generalizationType;
                if (excludePrimitives && genType.equals("primitive")) {
                    if (!goldReader.hasNext()) {
                        throw new RuntimeException("No more samples in gold reader (but still amconll sentences left!)");
                    }
                    sample = goldReader.getNextSample();
                    genType = sample.generalizationType;
                }
                if (cli.verbose && !sample.src_tokens.equals(tokens)) {
                    System.err.println("--Currently processing the nth sentence, where n=" + totalSentencesSeen);
                    System.err.println("--Tokens from Gold:    " + sample.src_tokens);
                    System.err.println("--Tokens from amconll: " + tokens);
                    throw new RuntimeException("Sentence of amconll and gold file don't match! " +
                            "AmConLL sentence line no: " + amsent.getLineNr());
                }


                // (b) get logical form
                COGSLogicalForm goldLF = new COGSLogicalForm(sample.tgt_tokens);
                // MRInstance mr = LogicalFormConverter.toSGraph(lf, sample.src_tokens);
                // SGraph goldGraph = mr.getGraph();

                // (c) evaluate gold against predicted logical form
                // todo edit distance: where to get tokens from? toString and then whitespace split?
                // -- Levenshtein token-level edit distance
                String[] tokens1 = goldLF.toString().split(" ");
                String[] tokens2 = predicedLFString.split(" ");
                int editDistance = cli.getEditDistance(tokens1, tokens2);
                totalEditDistance += editDistance;

                // -- Exact match: todo no need to compare strings here, just rely on token-edit distance == 0 or not?
                boolean exactMatch = goldLF.toString().equals(predicedLFString);
                assert((editDistance == 0) == exactMatch);  // edit distance is 0 if and only if we have an exact match
                if (exactMatch) {
                    totalExactMatches += 1;
                }
                else if (cli.verbose) {
                    System.err.println("Not exact match for sentence number " + totalSentencesSeen + " at input file line "+ amsent.getLineNr());
                    System.err.println("Edit distance: " + editDistance);
                    System.err.println("Gold:   " + goldLF.toString());
                    System.err.println("System: " + predicedLFString);
                }
            }

            // Step 4: Write the graph (the one based on the amconll sentence) to file [if output path was provided]
            if (graphWriter != null) {
                // todo use string builder? check correctness?
                graphWriter.println(String.join(" ", tokens)+"\t"+predicedLFString+"\t"+genType);
            }
            /*} catch (Exception ex) {
                System.err.printf("In line %d, id=%s: ignoring exception.\n", amsent.getLineNr(), id);
                ex.printStackTrace();
                if (graphWriter != null) {
                    System.err.println("Writing dummy logical form instead\n");
                    graphWriter.println(String.join(" ", tokens) + "\tDUMMY\tERROR");
                }
                // if (!read && goldReader != null){ // update scores? }
            }*/
        }  // for each amconll sentence

        if (graphWriter != null) {
            graphWriter.close();
        }
        if (goldReader != null) {
            double result;

            System.out.println("Exact match accuracy");
            result = totalSentencesSeen > 0 ? (float) totalExactMatches / totalSentencesSeen : 0;
            result *= 100;  // 95% should be displayed as '95' instead of 0.95
            System.out.println(String.format(java.util.Locale.US,"%.2f", result));
            // 2 decimal places, use US locale to determine character for decimal point

            System.out.println("Average token-level edit distance");
            result = totalSentencesSeen > 0 ? (float) totalEditDistance / totalSentencesSeen : 0;
            System.out.println(String.format(java.util.Locale.US,"%.2f", result));
        }


    }

    // todo test this implementation (including whether the loops are off-by-one)
    public int getEditDistance(String[] tokens1, String[] tokens2) {
        Objects.requireNonNull(tokens1);
        Objects.requireNonNull(tokens2);
        // todo can I use a default implementation of Levenshtein distance?
        // often, it's about a String of characters, like here:
        // https://commons.apache.org/proper/commons-text/apidocs/org/apache/commons/text/similarity/LevenshteinDistance.html
        // but we need to compare an array of Strings. The current implementation is adapted from the algorithm here
        // https://en.wikipedia.org/wiki/Levenshtein_distance#Iterative_with_two_matrix_rows  (it's using char[] )
        // String[] tokens1  // s  char s[0..m-1],
        // String[] tokens2  // t  char t[0..n-1]
        int length1 = tokens1.length;  // m
        int length2 = tokens2.length;  // n
        // create two work vectors of integer distances
        int[] v0 = new int[length2+1];  // declare int v0[n + 1]
        int[] v1 = new int[length2+1];  // declare int v1[n + 1]
        // initialize v0 (the previous row of distances)
        // this row is A[0][i]: edit distance for an empty s
        // the distance is just the number of characters to delete from t
        for (int i=0; i <= length2; ++i) {  // for i from 0 to n:
            v0[i] = i;  // v0[i] = i
        }
        for (int i=0; i <= length1-1; ++i) {  // for i from 0 to m-1:
            // calculate v1 (current row distances) from the previous row v0
            // first element of v1 is A[i+1][0]
            //   edit distance is delete (i+1) chars from s to match empty t
            v1[0] = i + 1;  // v1[0] = i + 1
            // use formula to fill in the rest of the row
            for (int j=0; j <= length2-1; ++j) {  // for j from 0 to n -1:
                // calculating costs for A[i+1][j+1]
                int deletionCost = v0[j + 1] + 1;  // deletionCost:=v0[j + 1] + 1
                int insertionCost = v1[j] + 1; // insertionCost:=v1[j] + 1
                int substitutionCost;
                if (tokens1[i].equals(tokens2[j])) { // if s[i] = t[j]:
                    substitutionCost = v0[j];  // substitutionCost:=v0[j]
                }
                else {  // else
                    substitutionCost = v0[j] + 1; // substitutionCost:=v0[j] + 1
                }

            v1[j + 1] = min(deletionCost, insertionCost, substitutionCost);
            // v1[j + 1]  = minimum(deletionCost, insertionCost, substitutionCost);
            }
            // copy v1 (current row) to v0 (previous row) for next iteration
            // since data in v1 is always invalidated, a swap without copy could be more efficient

            // swap v0 with v1  todo does this work in java? by reference vs by value?
            int[] tmp = v0;
            v0 = v1;
            v1 = tmp;
        }
        // after the last swap, the results of v1 are now in v0
        // return v0[n]
        return v0[length2];
    }



}