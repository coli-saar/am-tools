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


/**
 * Converts an AM Dependency corpus (amconll) into COGS format (.tsv) (tokens, logical form, generalization type)
 *
 * Also reports exact match accuracy if gold corpus is given.
 * todo: add token-level edit distance?
 * @author pia (weissenh)
 */
public class ToCOGSCorpus {
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees")//, required = true)
    private final String corpusPath = "/tmp/dm/dev_epoch_SDP-DM-2015_2.amconll";

    @Parameter(names = {"--outFile", "-o"}, description = "Path for output tsv file")//, required = true)
    private final String outPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/graphparsing/predictions.tsv";

    @Parameter(names={"--gold","-g"}, description = "Path to gold corpus. Make sure it contains exactly the same instances, in the same order.")//, required=true)
    private final String goldCorpus = null; //"/PATH/TO/TSVFILE.tsv";
//    private final String goldCorpus = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/cogsdata/train_100.tsv";
//    private final String goldCorpus = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/cogsdata/train.tsv";
//    private final String goldCorpus = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/COGS/datasmall/train20.tsv";

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

        // todo scorer?
        int totalSentencesSeen = 0;
        int totalExactMatches = 0;

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
            COGSLogicalForm predictedLF = LogicalFormConverter.toLogicalForm(amsent);
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
                if (!sample.src_tokens.equals(tokens)) {
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
                // todo evaluate gold against predicted logical form, currently just dummy
                if (goldLF.getFormulaType() == COGSLogicalForm.AllowedFormulaTypes.IOTA) {
                    totalExactMatches += 1;
                }
                // todo edit distance additionally?
            }

            // Step 4: Write the graph (the one based on the amconll sentence) to file [if output path was provided]
            if (graphWriter != null) {
                // todo use string builder? check correctness?
                graphWriter.println(String.join(" ", tokens)+"\t"+predictedLF.toString()+"\t"+genType);
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
            System.out.println("Exact match accuracy");
            double result = totalSentencesSeen > 0 ? (float) totalExactMatches / totalSentencesSeen : 0;
            result *= 100;  // 95% should be displayed as '95' instead of 0.95
            System.out.println(String.format(java.util.Locale.US,"%.2f", result));
            // 2 decimal places, use US locale to determine character for decimal point
            // todo add edit distance System.out.println("Average Token-level Edit distance");
        }


    }



}
