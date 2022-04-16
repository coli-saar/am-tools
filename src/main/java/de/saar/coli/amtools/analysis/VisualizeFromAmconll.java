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

    public static void main(String[] args) throws IOException, ParseException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, ParserException, AlignedAMDependencyTree.ConllParserException, InterruptedException {

        EvaluateAMConll amConllEvaluator = new EvaluateAMConll();

        amConllEvaluator.readCommandLineArguments(args);
        if(!amConllEvaluator.continueBeyondCommandLineArgumentReading) {
            return;
        }

        List<AmConllSentence> inputAMConllSentences = amConllEvaluator.readAMConllFile();

        EvaluationToolset evaluationToolset = amConllEvaluator.loadEvaluationToolset();

        List<MRInstance> outputCorpus = amConllEvaluator.evaluteAMCorpus(inputAMConllSentences, evaluationToolset);

        int i = 0;
        for (MRInstance mrInstance : outputCorpus) {
            giveAlignmentsColors(mrInstance.getAlignments());
            AlignVizAMR.visualize(false, true, amConllEvaluator.outPath, i, mrInstance.getGraph(),
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
