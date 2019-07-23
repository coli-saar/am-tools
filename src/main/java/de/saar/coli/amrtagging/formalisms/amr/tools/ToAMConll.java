/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.*;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.tree.ParseException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.simple.Sentence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static de.saar.coli.amrtagging.formalisms.amr.tools.DependencyExtractorCLI.LITERAL_JOINER;
import static de.saar.coli.amrtagging.formalisms.amr.tools.PrepareTestDataFromFiles.readFile;
import static edu.illinois.cs.cogcomp.core.datastructures.ViewNames.NER_CONLL;

/**
 * Tool to create amconll file from nnData/train
 *
 * @author matthias
 */
public class ToAMConll {
    @Parameter(names = {"--corpusPath", "-c"}, description = "Path to the input corpus") //required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR/2017/train/";

    @Parameter(names = {"--outPath", "-o"}, description = "Prefix for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR-toy/2017/";

    @Parameter(names = {"--companion"}, description = "Path to MRP companion data (will disable builtin lemmatization", required = false)
    private String companionDataFile = null;

    @Parameter(names = {"--stanford-ner-model"}, description = "Filename of Stanford NER model english.conll.4class.distsim.crf.ser.gz; if argument is not given, use UIUC NER tagger")
    private String stanfordNerFilename = null;

    @Parameter(names = {"--uiuc-ner-tagset"}, description = "Tagset to use for UIUC NER tagger; options: NER_CONLL (default), NER_ONTONOTES")
    private String uiucNerTagset = NER_CONLL;

    @Parameter(names = {"--help", "-?"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;

    @Parameter(names = {"--disable-ner"}, description = "disables NER for debugging purposes")
    private boolean ner_disabled = false;

    /**
     * Command line interface for the DependencyExtractor class; call with --help to see options.
     *
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws CorpusReadingException
     * @throws IllegalArgumentException
     * @throws ParseException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, IllegalArgumentException, ClassNotFoundException, PreprocessingException {
        ToAMConll cli = new ToAMConll();
        JCommander commander = new JCommander(cli);
        commander.setProgramName("constraint_extractor");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occurred: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (cli.help) {
            commander.usage();
            return;
        }

        List<List<String>> sentences = readFile(cli.corpusPath + "/sentences.txt");
        List<List<String>> literals = readFile(cli.corpusPath + "/literal.txt");
        List<List<String>> posTags = readFile(cli.corpusPath + "/pos.txt");
        List<List<String>> ops = readFile(cli.corpusPath + "/ops.txt");
        List<List<String>> tags = readFile(cli.corpusPath + "/tags.txt");
        List<List<String>> labels = readFile(cli.corpusPath + "/labels.txt");
        List<List<String>> ids = readFile(cli.corpusPath + "/graphIDs.txt");

        if (sentences.size() != literals.size() || literals.size() != posTags.size()) {
            System.err.println("Inputs seem to have different lengths: " + sentences.size() + " " + literals.size() + posTags.size());
            return;
        }

        ApplyModifyGraphAlgebra ga = new ApplyModifyGraphAlgebra();
        List<AmConllSentence> output = new ArrayList<>();

        PreprocessedData preprocData = null;
        NamedEntityRecognizer neRecognizer = null;

        if( cli.companionDataFile != null ) {
            preprocData = new MrpPreprocessedData(new File(cli.companionDataFile));
        } else {
            // It would be nice to encapsulate the choice between MRP preproc data and
            // Stanford preproc data as neatly as elsewhere, but as I read the code below,
            // the Stanford lemmatizer is applied to the expandedWords, in which tokens
            // have been split apart at underscores. Constructing a StanfordPreprocData
            // up here would only duplicate the code. - AK, July 2019.
        }

        if (!cli.ner_disabled){
            if( cli.stanfordNerFilename != null ) {
                neRecognizer = new StanfordNamedEntityRecognizer(new File(cli.stanfordNerFilename));
            } else {
                neRecognizer = new UiucNamedEntityRecognizer(cli.uiucNerTagset);
            }
        }




        sents:
        for (int i = 0; i < sentences.size(); i++) {
            if (i % 1000 == 0) {
                System.err.println(i);
            }

            String id = ids.get(i).get(0);

            AmConllSentence o = new AmConllSentence();
            o.setAttr("git", AMToolsVersion.GIT_SHA);
            o.setId(id);
            o.setAttr("framework", "amr");
            o.setAttr("flavor", "2");

            List<String> expandedWords = new ArrayList<>();
            List<Integer> origPositions = new ArrayList<>();
            List<String> ners = new ArrayList<>();

            for (int positionInSentence = 0; positionInSentence < sentences.get(i).size(); positionInSentence++) {
                String wordForm = literals.get(i).get(positionInSentence).replace(LITERAL_JOINER, " ");
                AmConllEntry e = new AmConllEntry(positionInSentence + 1, wordForm);
                String tag = tags.get(i).get(positionInSentence).replaceAll("__ALTO_WS__", " ");

                if (!tag.equals("NULL")) {
                    String[] infos = tag.split("--TYPE--");
                    e.setDelexSupertag(infos[0]);

                    try {
                        e.setType(ga.parseString(tag).getRight());
                    } catch (ParserException ex) {
                        System.err.println("Couldn't parse tag " + tag);
                        System.err.println("Will skip the sentence");
                        continue sents;
                    }
                }

                String label = labels.get(i).get(positionInSentence);

                if (!label.equals("NULL")) {
                    e.setLexLabel(label);
                }

                o.add(e);
                ners.add("O");

                for (String w : literals.get(i).get(positionInSentence).split(LITERAL_JOINER)) {
                    expandedWords.add(w);
                    origPositions.add(positionInSentence);
                }
            }

            // At this point, expandedWords is a list of tokens. Potentially, |expandedWords| > |sentences(i)|,
            // because tokens may have been split at underscores. Thus origPositions maps each position in
            // expandedWords to the position in the original sentence from which it came.

            
            List<CoreLabel> nerTags = null;
            List<String> ourLemmas = new ArrayList<>(o.words());
            List<String> lemmas;


            if( preprocData != null ) {
                lemmas = preprocData.getLemmas(id);
                if (!cli.ner_disabled) {
                    nerTags = neRecognizer.tag(preprocData.getTokens(id));
                }
            } else {
                Sentence stanfSent = new Sentence(expandedWords);
                lemmas = stanfSent.lemmas();
                if (!cli.ner_disabled) {
                    nerTags = neRecognizer.tag(Util.makeCoreLabelsForTokens(expandedWords));
                }
            }

            for (int j = 0; j < lemmas.size(); j++) {
                if (!cli.ner_disabled) {
                    ners.set(origPositions.get(j), nerTags.get(j).ner());
                }
                ourLemmas.set(origPositions.get(j), lemmas.get(j));
            }


            o.addReplacement(sentences.get(i),false);
            o.addPos(posTags.get(i));
            o.addLemmas(ourLemmas);
            if (!cli.ner_disabled) {
                o.addNEs(ners);
            }

            //now we add the edges
            HashSet<Integer> hasOutgoing = new HashSet<>();
            HashSet<Integer> hasIncoming = new HashSet<>();
            for (String edge : ops.get(i)) {
                if (edge.equals("")) {
                    break;
                }
                String[] infos = edge.split("\\[");
                String label = infos[0];
                String fromTo[] = infos[1].replace("]", "").split(",");
                int from = Integer.parseInt(fromTo[0]);
                int to = Integer.parseInt(fromTo[1]);
                hasOutgoing.add(from);
                hasIncoming.add(to);
                o.get(to).setHead(from + 1); //1-based indexing
                o.get(to).setEdgeLabel(label);
            }

            hasOutgoing.removeAll(hasIncoming);

            if (hasOutgoing.isEmpty()) {
                int found = 0;
                for (AmConllEntry e : o) {
                    if (!e.getDelexSupertag().equals(AmConllEntry.DEFAULT_NULL)) {
                        found++;
                        e.setEdgeLabel("ROOT");
                    }
                }
                if (found != 1) {
                    System.err.println("Sentence has no unique root?");
                }
            } else if (hasOutgoing.size() == 1) {
                int rootIndex = hasOutgoing.iterator().next();
                o.get(rootIndex).setEdgeLabel("ROOT");
            } else {
                System.err.println("Sentence has multiple roots?");
            }
            for (AmConllEntry e : o) {
                if (e.getEdgeLabel().equals(AmConllEntry.DEFAULT_NULL)) {
                    e.setEdgeLabel("IGNORE");
                }
            }
            try {
                AMDependencyTree amdep = AMDependencyTree.fromSentence(o);
                amdep.evaluate(false); //sanity check

                output.add(o);
            } catch (Exception e) {
                System.err.println("Ignoring problem:");
                System.err.println(o);
                e.printStackTrace();
            }

        }

        AmConllSentence.writeToFile(cli.outPath + "/corpus.amconll", output);
    }


}
