/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.datascript;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.formalisms.amr.tools.MakeNETypeLookup;
import de.saar.coli.amrtagging.formalisms.amr.tools.RareWordsAnnotator;
import de.saar.coli.amrtagging.formalisms.amr.tools.aligner.Aligner;
import de.saar.coli.amrtagging.formalisms.amr.tools.aligner.FixUnalignedNodes;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.tree.ParseException;
import java.io.File;
import java.io.IOException;

/**
 * Converts a raw AMR corpus to a training corpus in Alto format, including pre-
 * processing. Run with --help for options.
 *
 * @author JG
 */
public class RawAMRCorpus2TrainingData {

    @Parameter(names = {"--inputPath", "-i"}, description = "Path to folder containing the original AMR files")
    private String inputPath = "/home/mego/Documents/datasets/little-prince/corpus/training";

    @Parameter(names = {"--outputPath", "-o"}, description = "Path to output folder")
    private String outputPath = "/home/mego/Documents/datasets/little-prince/alto/training";
    ;
    
    @Parameter(names = {"--grammarFile", "-g"}, description = "Path to Stanford grammar file englishPCFG.txt. If none is provided, no trees are produced in the corpus", required = false)
    private String grammarFile;

    @Parameter(names = {"--corefSplit"}, description = "Removes reentrant edges that the AM algebra can't handle.")
    private boolean corefSplit = false;

    @Parameter(names = {"--maxNodes", "-m"}, description = "maximum number of nodes for instances to be kept in the final corpus")
    private int maxNodes = -1;

    @Parameter(names = {"--threads", "-t"}, description = "max number of threads used")
    private int threads = 1;

    @Parameter(names = {"--minutes"}, description = "number of minutes for which corefSplit is allowed to run")
    private int minutes = 60;

    @Parameter(names = {"--step"}, description = "First step to be executed (default is from the start, step 0). Steps are: 0:altoFormat 1:corefSplit 2:align 3:namesAndDates 4:fixAlignments 5:sortAndFilter")
    private int step = 0;

    @Parameter(names = {"--wordnet", "-w"}, description = "Path to Wordnet dictionary (folder 'dict')")
    private String wordnetPath = "/home/mego/PycharmProjects/am-parser-my-fork/downloaded_models/wordnet3.0/dict";

    @Parameter(names = {"--conceptnet"}, description = "Path to ConceptNet csv.gz file", required = false)
    private String conceptnetPath = null;

    @Parameter(names = {"--posmodel", "-pos"}, description = "Path to POS tagger model")
    private String posModelPath = "/home/mego/PycharmProjects/am-parser-my-fork/downloaded_models/stanford/english-bidirectional-distsim.tagger";

    @Parameter(names = {"--trees", "-trees"}, description = "Boolean flag saying whether we're using syntactic parse trees", required = false)
    private boolean useTrees;

    @Parameter(names = {"--companion"}, description = "Path to MRP companion data (will disable builtin tokenization and POS tagging", required = false)
    private String companionDataPath = null;

    @Parameter(names = {"--help", "-?"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;

    /**
     * Converts a raw AMR corpus to a training corpus in Alto format, including
     * pre- processing. Run with --help for options.
     *
     * @param args
     * @throws IOException
     * @throws ParseException
     * @throws InterruptedException
     * @throws CorpusReadingException
     * @throws ParserException
     */
    public static void main(String[] args) throws IOException, ParseException, InterruptedException, CorpusReadingException, ParserException {

        RawAMRCorpus2TrainingData r2t = new RawAMRCorpus2TrainingData();

        JCommander commander = new JCommander(r2t);
        commander.setProgramName("viz");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (r2t.help) {
            commander.usage();
            return;
        }

        String path = r2t.outputPath;
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        new File(path).mkdirs();

        String treeString = "";
        if (r2t.useTrees) {
            treeString = " --trees";
        }

        //Step 0: Convert raw AMR corpus into a corpus in Alto format
        if (r2t.step <= 0) {
            FullProcess fp = new FullProcess();
            fp.setAmrCorpusPath(r2t.inputPath);
            fp.setOutputPath(path);
            fp.setStanfordGrammarFile(r2t.grammarFile);
            fp.setCompanionDataFile(r2t.companionDataPath);
            fp.fullProcess();
        }

        //Optional Step 1: split coref
        String corpusFileName = "finalAlto";
        if (r2t.corefSplit) {
            if (r2t.step <= 1) {
                System.err.println("\nRunning coref split");
                SplitCoref.splitCoref(r2t.outputPath + corpusFileName + ".corpus", r2t.outputPath + "raw.amr", r2t.outputPath + "corefSplit.corpus", r2t.threads, r2t.minutes);
            }
            corpusFileName = "corefSplit";
        }

        //Step 2: Alignments
        if (r2t.step <= 2) {
            System.err.println("\nRunning aligner (basic)");
//            String alignerArgs = "-c "+path+corpusFileName+".corpus -o "+path+corpusFileName+".align -w "+r2t.wordnetPath+" -pos "+r2t.posModelPath+" -m p";
//            Aligner.main(alignerArgs.split(" "));
            Aligner al = new Aligner();
            al.setCorpusPath(path + corpusFileName + ".corpus");
            al.setAlignmentPath(path + corpusFileName + ".align");
            al.setWordnetPath(r2t.wordnetPath);
            al.setConceptnetPath(r2t.conceptnetPath);
            al.setPosModelPath(r2t.posModelPath);
            al.setCompanionDataFile(r2t.companionDataPath);
            al.setMode("p");
            al.align();

            System.err.println("\nRunning aligner (all probabilities)");
//            String pAlignerArgs = "-c "+path+corpusFileName+".corpus -o "+path+corpusFileName+".palign -w "+r2t.wordnetPath+" -pos "+r2t.posModelPath+" -m ap";
//            Aligner.main(pAlignerArgs.split(" "));
            al = new Aligner();
            al.setCorpusPath(path + corpusFileName + ".corpus");
            al.setAlignmentPath(path + corpusFileName + ".palign");
            al.setWordnetPath(r2t.wordnetPath);
            al.setConceptnetPath(r2t.conceptnetPath);
            al.setPosModelPath(r2t.posModelPath);
            al.setCompanionDataFile(r2t.companionDataPath);
            al.setMode("ap");
            al.align();
        }

        //Step 3: Replacing names, dates and numbers
        if (r2t.step <= 3) {
            System.err.println("\nRunning RareWordsAnnotator");
            String rareWordsArgs = "-c " + path + corpusFileName + ".corpus -o " + path + "namesDatesNumbers.corpus -a "
                    + path + corpusFileName + ".align -pa " + path + corpusFileName + ".palign -t 0" + treeString;
            RareWordsAnnotator.main(rareWordsArgs.split(" "));
            MakeNETypeLookup.main(new String[]{path + "namesDatesNumbers.corpus", path + "nameTypeLookup.txt"});
        }

        //Step 4: fix alignments
        if (r2t.step <= 4) {
            System.err.println("\nFixing unaligned words");
            FixUnalignedNodes.fixUnalignedNodes(path + "namesDatesNumbers.corpus", 5);
        }

        //Step 5: sort and filter corpus
        if (r2t.step <= 5) {
            String sortArgs = path + "namesDatesNumbers_AlsFixed.corpus -m " + r2t.maxNodes + " -gi repgraph";
            SortAndFilterAMRCorpus.main(sortArgs.split(" "));
        }

    }

}
