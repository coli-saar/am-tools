package de.saar.coli.amrtagging.formalisms.cogs.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.cogs.COGSBlobUtils;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm;
import de.saar.coli.amrtagging.formalisms.cogs.LogicalFormConverter;
import de.saar.coli.amtools.decomposition.COGSDecompositionPackage;

import java.io.IOException;
import java.util.ArrayList;


// todo: what about the lexical labels that makeBaseAmConllSentence inserts? get rid of them?
/** Prepares Dev Data for COGS: basically amconll with only tokens and other columns contain dummy values. </br>
 *
 * Took inspiration from <code>amrtagging/formalisms/sdp/tools/PrepareDevData.java</code>, but since the files produced
 * actually mirror the <code>makeBaseAmConllSentence</code> of the respective (COGS) <code>DecompositionPackage</code>,
 * we will use the COGS decomposition package this time (it wasn't used for SDP for historic reasons maybe?).</br>
 *
 * Output is needed as system_input in the validation_evaluator of the task model in the am-parser.
 *
 * @author pia (weissenh)
 * */
public class PrepareDevData {
    @Parameter(names = {"--corpus", "-c"}, description = "Points to the input corpus (tsv) or subset thereof")//, required = true)
    private String corpusPath = "/home/wurzel/HiwiAK/cogs2021/small/dev100.tsv";
//    private String corpusPath = "/home/wurzel/HiwiAK/cogs2021/COGS/data/dev.tsv";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/wurzel/HiwiAK/cogs2021/amconll/";

    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "dp_dev"; // "test_like";

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    public static void main(String[] args) throws IOException {
        PrepareDevData cli = new PrepareDevData();
        JCommander commander = new JCommander(cli);
        //commander.setProgramName("prepare dev data for cogs");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex);
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (cli.help) {
            commander.usage();
            return;
        }
        System.out.println("Input file: " + cli.corpusPath);
        System.out.println("Output path: " + cli.outPath);
        System.out.println("Prefix/filename of output file: " + cli.prefix);

        RawCOGSReader reader = new RawCOGSReader(cli.corpusPath);
        MRInstance mrInstance;
        COGSLogicalForm lf;
        AMRBlobUtils blobUtils = new COGSBlobUtils();

        ArrayList<AmConllSentence> out = new ArrayList<>();
        while (reader.hasNext()){  // for each sample
            RawCOGSReader.CogsSample sample = reader.getNextSample();

            // convert logical form to a mrInstance (involves parsing the logical form and to graph conversion)
            // todo I should check whether the logical form could be built
            lf = new COGSLogicalForm(sample.tgt_tokens);
            // todo what happens if the conversion to grpah fails? do I need try/catch statements here?
            mrInstance = LogicalFormConverter.toSGraph(lf, sample.src_tokens);

            // use the decomposition package o get a base amconll sentence
            // TODO: decompositionpackage: can I prevent the need for graph conversion?
            // one could create a new MRInstance(sample.src_tokens, dummygraph, dummyalignments) ASSUMING
            // makeBaseAmconllSentence wouldn't use graph and alignment (so far true, but doesn't have to stay like this?)
            // todo do I need to care about primitives (in general and decomposition of them in particular?)
            // dev doesn't contain primitives, test probably also not.
            COGSDecompositionPackage decompositionPackage = new COGSDecompositionPackage(mrInstance, blobUtils);
            AmConllSentence currentSent = decompositionPackage.makeBaseAmConllSentence();

            // removing lex labels: todo should I remove lex labels?
            // if this sentence is used for prediction the lex label shouldn't be given but rather predicted
            for (AmConllEntry entry: currentSent) {
                entry.setLexLabelWithoutReplacing(AmConllEntry.DEFAULT_NULL);
            }

            out.add(currentSent);
        }
        AmConllSentence.writeToFile(cli.outPath+"/"+cli.prefix+".amconll", out);
    }
}
