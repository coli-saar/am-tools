package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AmConllSentence;
import de.up.ling.tree.ParseException;
import org.eclipse.collections.impl.factory.Sets;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AMExampleFinder {

    // amconll files (i.e. AM dependency trees)
    @Parameter(names = {"--amconllDM", "-amdm"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathDM = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\dm\\gold-dev\\gold-dev.amconll";

    @Parameter(names = {"--amconllPAS", "-ampas"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPAS = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\pas\\gold-dev\\gold-dev.amconll";

    @Parameter(names = {"--amconllPSD", "-ampsd"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPSD = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\new_psd_preprocessing\\gold-dev\\gold-dev.amconll";

    @Parameter(names = {"--pattern", "-p"}, description = "Pattern triple")
    private String pattern = "566";

    @Parameter(names = {"--pos", "-pos"}, description = "POS tag")
    private String pos = "RB";

    @Parameter(names = {"--lemma", "-l"}, description = "Lemma")
    private String lemma = null;

    @Parameter(names = {"--range", "-r"}, description = "how many words around the match to print")
    private int range = 3;

    @Parameter(names = {"--printDM"}, description = "print DM example")
    private boolean printDM=false;

    @Parameter(names = {"--printPAS"}, description = "print PAS example")
    private boolean printPAS=true;

    @Parameter(names = {"--printPSD"}, description = "print PSD example")
    private boolean printPSD=false;


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;

    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException {
        //just getting command line args
        AMExampleFinder cli = new AMExampleFinder();
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

        System.err.println("reading amDM");
        List<AmConllSentence> amDM = AmConllSentence.read(new InputStreamReader(
                new FileInputStream(cli.amconllPathDM), StandardCharsets.UTF_8));
        System.err.println("reading amPAS");
        List<AmConllSentence> amPAS = AmConllSentence.read(new InputStreamReader(
                new FileInputStream(cli.amconllPathPAS), StandardCharsets.UTF_8));
        System.err.println("reading amPSD");
        List<AmConllSentence> amPSD = AmConllSentence.read(new InputStreamReader(
                new FileInputStream(cli.amconllPathPSD), StandardCharsets.UTF_8));
        System.err.println("done reading");

        Map<String, AmConllSentence> id2amDM = new HashMap<>();
        amDM.stream().forEach(sent -> id2amDM.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPAS = new HashMap<>();
        amPAS.stream().forEach(sent -> id2amPAS.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPSD = new HashMap<>();
        amPSD.stream().forEach(sent -> id2amPSD.put(sent.getId(), sent));
        Set<String> decomposedIDs = Sets.intersectAll(id2amDM.keySet(), id2amPAS.keySet(), id2amPSD.keySet());

        for (String sentenceID : decomposedIDs) {
            //ignore 0 in next loop, since it is the artificial root of the SDP graph
            AmConllSentence dmDep = id2amDM.get(sentenceID);
            AmConllSentence pasDep = id2amPAS.get(sentenceID);
            AmConllSentence psdDep = id2amPSD.get(sentenceID);

            for (int index = 0; index < dmDep.size(); index++) {
                String pattern = FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, index+1);
                String pos = dmDep.get(index).getPos();
                String lemma = psdDep.get(index).getLemma();

                if ((cli.pattern == null || cli.pattern.equals(pattern)) &&
                        (cli.pos == null || cli.pos.equals(pos)) &&
                        (cli.lemma == null || cli.lemma.equals(lemma))) {
                    //then we have a match
                    System.out.println(index+1);
                    if (cli.printDM) {
                        System.out.println("DM");
                        cli.printExample(dmDep, index+1, cli.range);
                    }
                    if (cli.printPAS) {
                        System.out.println("PAS");
                        cli.printExample(pasDep, index+1, cli.range);
                    }
                    if (cli.printPSD) {
                        System.out.println("PSD");
                        cli.printExample(psdDep, index+1, cli.range);
                    }
                }

            }
        }


    }

    public static void printExample(AmConllSentence dep, int id, int range) {
        for (int i = Math.max(0, id-1-range); i<Math.min(dep.size(), id+range); i++) {
            System.out.println(dep.get(i).toString());
        }
    }

}
