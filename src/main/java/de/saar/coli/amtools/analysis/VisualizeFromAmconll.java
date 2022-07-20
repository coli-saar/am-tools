package de.saar.coli.amtools.analysis;

import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amtools.evaluation.EvaluateAMConll;
import de.saar.coli.amtools.evaluation.toolsets.EvaluationToolset;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

public class VisualizeFromAmconll {
    
    /**
    Parameters are as in de.saar.coli.amtools.evaluation.EvaluateAMConll. However, the --gold parameter is not used. The --evaluationToolset parameter
    is not required, unless you want to apply postprocessing.
    
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees", required = true)
    private String corpusPath = null;

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files", required = true)
    public String outPath = null;

    @Parameter(names = {"--gold", "-g"}, description = "Path to gold corpus. Usually expected to contain the same instances in the same order as " +
            "the --corpus file (unless the evaluation toolset says otherwise). Giving the gold corpus here is optional, and only works if the evaluation" +
            "toolset has the compareToGold function implemented. Alternatively, use an external evaluation tool after this program has run (such as" +
            "the Smatch script for AMR graphs).")
    private String goldCorpus = null;

    @Parameter(names = {"--evaluationToolset", "-et"}, description = "Classname of the EvaluationToolset class to be used. Default applies no postprocessing and writes the files in ISI AMR format")
    private String evaluationToolsetName = "de.saar.coli.amtools.evaluation.toolsets.EvaluationToolset";

    @Parameter(names = {"--extras", "-e"}, description = "Additional parameters to the constructor of the Evaluation toolset, as a single string. Optional." +
            " Note that using this parameter causes a different constructor of the evaluation toolset to be called.")
    private String toolsetExtras = null;

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
    
    **/

    public static void main(String[] args) throws IOException, ParseException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, ParserException, AlignedAMDependencyTree.ConllParserException, InterruptedException {

        EvaluateAMConll amConllEvaluator = new EvaluateAMConll();

        amConllEvaluator.readCommandLineArguments(args);
        if(!amConllEvaluator.continueBeyondCommandLineArgumentReading) {
            return;
        }

        List<AmConllSentence> inputAMConllSentences = amConllEvaluator.readAMConllFile();

        EvaluationToolset evaluationToolset = amConllEvaluator.loadEvaluationToolset();

        List<MRInstance> outputCorpus = EvaluateAMConll.evaluteAMCorpus(inputAMConllSentences, evaluationToolset);

        AlignVizAMR viz = new AlignVizAMR(true, false, false);

        int i = 0;
        for (MRInstance mrInstance : outputCorpus) {
            giveAlignmentsColors(mrInstance.getAlignments());
            viz.visualize(amConllEvaluator.outPath, i, mrInstance.getGraph(),
                    mrInstance.getSentence(), mrInstance.getAlignments());
            i++;
        }


    }

    private static void giveAlignmentsColors(Collection<Alignment> alignments) {
        int i = 0;
        for (Alignment al : alignments) {
            al.color = i;
            i = (i + 1) % AlignVizAMR.COLORS.length;
        }
    }



}
