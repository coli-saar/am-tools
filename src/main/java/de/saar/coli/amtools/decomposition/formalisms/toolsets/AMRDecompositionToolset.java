package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.NamedEntityRecognizer;
import de.saar.coli.amtools.decomposition.AMRDecompositionPackage;
import de.saar.coli.amtools.decomposition.AMRDecompositionPackageLegacy;
import de.saar.coli.amtools.decomposition.DecompositionPackage;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An abstract GraphbankDecompositionToolset baseclass for the SDP corpora. Handles reading the corpus
 */
public class AMRDecompositionToolset extends GraphbankDecompositionToolset {

    private final AMRBlobUtils blobUtils = new AMRBlobUtils();

    /**
     * @param useStanfordTagger If useStanfordTagger is true, the decomposition packages will fill the empty NE/lemma/POS slots of
     *                          the amconll file with the Stanford NLP solution. Else, the slots remain empty.
     */
    public AMRDecompositionToolset(Boolean useStanfordTagger) {
        super(useStanfordTagger);
    }

    @Override
    public List<MRInstance> readCorpus(String filePath) throws IOException {
        // load data
        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation(new Interpretation<>(new GraphAlgebra(), new Homomorphism(dummySig, dummySig), "repgraph"));
        loaderIRTG.addInterpretation(new Interpretation<>(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "repstring"));
        loaderIRTG.addInterpretation(new Interpretation<>(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "string"));
        loaderIRTG.addInterpretation(new Interpretation<>(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "spanmap"));
        loaderIRTG.addInterpretation(new Interpretation<>(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "id"));
        loaderIRTG.addInterpretation(new Interpretation<>(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "repalignment"));



        //get automata for training set
        Corpus corpus;
        try {
            corpus = Corpus.readCorpusWithStrictFormatting(new FileReader(filePath), loaderIRTG);
        } catch (CorpusReadingException e) {
            throw new IOException(e);
        }



        List<MRInstance> ret = new ArrayList<>();

        for (Instance corpusInstance : corpus) {
            List<String> sentence = (List<String>)corpusInstance.getInputObjects().get("repstring");
            SGraph graph = (SGraph)corpusInstance.getInputObjects().get("repgraph");
            List<String> alignmentStrings =  (List<String>)corpusInstance.getInputObjects().get("repalignment");
            List<Alignment> alignments = alignmentStrings.stream().map(s -> Alignment.read(s)).collect(Collectors.toList());

            MRInstance mrInstance = new MRInstance(sentence, graph, alignments);
            mrInstance.setExtra("id", ((List<String>)corpusInstance.getInputObjects().get("id")).get(0));
            mrInstance.setExtra("origSent", corpusInstance.getInputObjects().get("string"));
            mrInstance.setExtra("spanmap", corpusInstance.getInputObjects().get("spanmap"));

            ret.add(mrInstance);
        }

        return ret;
    }

    @Override
    public DecompositionPackage makeDecompositionPackage(MRInstance instance) {
        return new AMRDecompositionPackage(instance, getEdgeHeuristics(), true, useStanfordTagger);
    }

    @Override
    public AMRBlobUtils getEdgeHeuristics() {
        return new AMRBlobUtils();
    }


}
