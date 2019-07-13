/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.datascript;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.up.ling.tree.ParseException;
import java.io.IOException;

/**
 * A script that turns a raw AMR corpus into alto format.
 * @author Jonas
 */
public class FullProcess {
    // command-line parameters
    @Parameter(names = {"--amrcorpus", "-c"}, description = "Path to the original AMR corpus files", required = true)
    private String amrCorpusPath = null;

    @Parameter(names = {"--output", "-o"}, description = "Path to the output folder", required = true)
    private String outputPath = null;

    @Parameter(names = {"--grammar"}, description = "Path to Stanford parser grammar englishPCFG.txt", required = false)
    private String stanfordGrammarFile = null;

    @Parameter(names = {"--companion"}, description = "Path to MRP companion data (will disable builtin tokenization and POS tagging", required = false)
    private String companionDataPath = null;

    @Parameter(names = {"--help", "-?"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;


    /**
     * A script that turns a raw AMR corpus into alto format.
     * Takes three arguments: first folder that contains the original AMR corpus
     * files; Second the output folder; third the path to the stanford parser
     * grammar file englishPCFG.txt.
     * @param args
     * @throws IOException 
     * @throws de.up.ling.tree.ParseException 
     */
    public static void main(String[] args) throws IOException, ParseException {
        FullProcess fp = new FullProcess();

        JCommander commander = new JCommander(fp);
        commander.setProgramName("FullProcess");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occurred: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (fp.help) {
            commander.usage();
            return;
        }

        fp.fullProcess();



/*
        // String[] onlyOutput = new String[]{args[1], args[2]};
        
        // the third argument is the PCFG, which is not always given
        if (args.length > 2) {
            fullProcess(args[0], args[1], args[2]);
        } else if (args.length == 2) {
            fullProcess(args[0], args[1], null);
        } else {
            System.err.println("FullProcess error: 2 or 3 arguments needed"
                    + ", 0 or 1 given");
        }
        */
    }
    
    /**
     * A script that turns a raw AMR corpus into alto format.
     * 
     * Final output is called finalAlto.corpus
     *
     * @throws IOException 
     * @throws de.up.ling.tree.ParseException 
     */
    public void fullProcess() throws IOException, ParseException {
        boolean trees = stanfordGrammarFile != null;
        
        System.err.println("\nJoining data...");
        StripSemevalData.stripSemevalData(amrCorpusPath, outputPath);

        System.err.println("\nBuilding raw corpus...");
        Stripped2Corpus.stripped2Corpus(outputPath, stanfordGrammarFile);

        System.err.println("\nFixing corpus...");
        FixAMRAltoCorpus.fixAMRCorpus(outputPath, trees);

        System.err.println("\nDone corpus preprocessing\n\n");
    }

    public void setAmrCorpusPath(String amrCorpusPath) {
        this.amrCorpusPath = amrCorpusPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public void setStanfordGrammarFile(String stanfordGrammarFile) {
        this.stanfordGrammarFile = stanfordGrammarFile;
    }

    public void setCompanionDataPath(String companionDataPath) {
        this.companionDataPath = companionDataPath;
    }
}
