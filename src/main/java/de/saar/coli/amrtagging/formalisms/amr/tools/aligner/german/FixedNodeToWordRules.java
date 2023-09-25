/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.aligner.german;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A set of rules mapping AMR labels to corresponding words.
 * @author Jonas
 */
public class FixedNodeToWordRules {
    
    private final static Map<String, String[]> FIXED_RULES = new HashMap<>();
    private final static Map<String, String[]> FIXED_SECONDARY_RULES = new HashMap<>();
    
    static {
        FIXED_RULES.put("-", new String[]{"nein", "nicht", "non"});
        FIXED_RULES.put("+", new String[]{"bitte"});
        FIXED_RULES.put("1",new String[]{"eins", "erste", "erster", "erstes", "Montag", "Januar"});
        FIXED_RULES.put("2",new String[]{"zwei", "zweite", "zweiter", "zweites", "Dienstag", "Februar"});
        FIXED_RULES.put("3",new String[]{"drei", "dritte", "dritter", "drittes", "Mittwoch", "Maerz"});
        FIXED_RULES.put("4",new String[]{"vier", "vierte", "vierter", "viertes", "Donnerstag", "April"});
        FIXED_RULES.put("5",new String[]{"fuenf", "fuenfte", "fuenfter", "fuenftes", "Freitag", "Mai"});
        FIXED_RULES.put("6",new String[]{"sechs", "sechste", "sechster", "sechstes", "Samstag", "Juni"});
        FIXED_RULES.put("7",new String[]{"sieben", "siebte", "siebter", "siebtes", "Sonntag", "Juli"});
        FIXED_RULES.put("8",new String[]{"acht", "achte", "achter", "achtes", "August"});
        FIXED_RULES.put("9",new String[]{"neun", "neunte", "neunter", "neuntes", "September"});
        FIXED_RULES.put("10",new String[]{"zehn", "zehnte", "zehnter", "zehntes", "Oktober"});
        FIXED_RULES.put("11",new String[]{"elf", "elfte", "elfter", "elftes", "November"});
        FIXED_RULES.put("12",new String[]{"zwoelf", "zwoelfte", "zwoelfter", "zwoelftes", "Dezember", "duzend"});
        FIXED_RULES.put("13",new String[]{"dreizehn"});
        FIXED_RULES.put("14",new String[]{"vierzehn"});
        FIXED_RULES.put("15",new String[]{"fuenfzehn"});
        FIXED_RULES.put("16",new String[]{"sechzehn"});
        FIXED_RULES.put("17",new String[]{"siebzehn"});
        FIXED_RULES.put("18",new String[]{"achtzehn"});
        FIXED_RULES.put("19",new String[]{"neunzehn"});
        FIXED_RULES.put("20",new String[]{"zwanzig"});
        FIXED_RULES.put("30",new String[]{"dreissig"});
        FIXED_RULES.put("40",new String[]{"vierzig"});
        FIXED_RULES.put("50",new String[]{"fuenfzig"});
        FIXED_RULES.put("60",new String[]{"sechzig"});
        FIXED_RULES.put("70",new String[]{"siebzig"});
        FIXED_RULES.put("80",new String[]{"achtzig"});
        FIXED_RULES.put("90",new String[]{"neunzig"});
        FIXED_RULES.put("100",new String[]{"hundert"});
        FIXED_RULES.put("1000",new String[]{"tausend"});
        FIXED_RULES.put("possible-01",new String[]{"kann", "moeglich", "koennte", "vielleicht"});//be able to, been able to
        FIXED_RULES.put("recommend-01",new String[]{"sollte"});
        FIXED_RULES.put("cause-01",new String[]{"weil", "wegen", "Grund", "deshalb", "deswegen", "daher", "Ursache", "so", "also"});
        FIXED_RULES.put("infer-01",new String[]{"daher", "deshalb", "deswegen"});
        FIXED_RULES.put("contrast-01",new String[]{"aber", "jedoch"});
        FIXED_RULES.put("have-concession-91",new String[]{"obwohl", "trotzdem", "trotz", "obgleich"});
        FIXED_RULES.put("verpflichten-01",new String[]{"muss"});
        FIXED_RULES.put("fehlen-01",new String[]{"ohne"});
        FIXED_RULES.put("multi-sentence",new String[]{".", ";"});
        FIXED_RULES.put("bedeuten-01",new String[]{":", "also"});
        FIXED_RULES.put("sagen-01",new String[]{"nach"});
        FIXED_RULES.put("expressive",new String[]{"!","..", "...", "...."});
        FIXED_RULES.put("interrogative",new String[]{"?"});
        FIXED_RULES.put("slash",new String[]{"/"});
        FIXED_RULES.put("percentage-entity",new String[]{"%"});
        FIXED_RULES.put("dollar",new String[]{"$"});
        FIXED_RULES.put("seismic-quanity",new String[]{"magnitude"});
        FIXED_RULES.put("date-entity",new String[]{"o'clock"});
        FIXED_RULES.put("amr-unknown",new String[]{"wer", "wieso", "weshalb", "warum", "wo", "wann", "welche", "welcher", "welches", "wessen", "wen", "wem"});
        FIXED_RULES.put("et-cetera", new String[]{"etc", "etc.", "etcetera"});
        FIXED_RULES.put("rate-entity-91", new String[]{"pro", "jeden", "am"});
        FIXED_RULES.put("have-condition-91", new String[]{"falls", "wenn"});
        FIXED_RULES.put("jemals", new String[]{"nie"});
        FIXED_RULES.put("aehneln-01", new String[]{"wie"});
        
        FIXED_SECONDARY_RULES.put("multi-sentence", new String[]{","});
        FIXED_SECONDARY_RULES.put("ich",new String[]{"ich", "mir", "mich", "mein", "meine", "meiner", "meines", "meins"});
        FIXED_SECONDARY_RULES.put("du",new String[]{"du", "dir", "dich", "dein", "deine", "deiner", "deines", "deins"});
        FIXED_SECONDARY_RULES.put("sie",new String[]{"sie", "ihnen", "ihr", "ihre", "ihrer", "ihres", "ihrs"});// includes words for 2nd person singular (polite), 3rd person singular and 3rd person plural
        FIXED_SECONDARY_RULES.put("er",new String[]{"er", "ihm", "ihn", "sein", "seine", "seiner", "seines", "seins"});
        FIXED_SECONDARY_RULES.put("es",new String[]{"es", "sein", "seine", "seiner", "seines", "seins"});
        FIXED_SECONDARY_RULES.put("wir",new String[]{"wir", "uns", "unser", "unsere", "unseres", "userer", "unsers"});
        FIXED_SECONDARY_RULES.put("ihr",new String[]{"ihr", "euch", "euer", "eure", "eures", "eurer", "eurs"});

        FIXED_SECONDARY_RULES.put("1",new String[]{"ein", "eine"});
        FIXED_SECONDARY_RULES.put("interrogative",new String[]{"ob"});
        FIXED_SECONDARY_RULES.put("person",new String[]{"sie"});
        FIXED_SECONDARY_RULES.put("stammen-01",new String[]{"sein", "kommen"});
    }
    
    
    /**
     * Gets directly related words for a node label.
     * @param nodeLabel
     * @return 
     */
    static Set<String> getDirectWords(String nodeLabel) {
        Set<String> ret = new HashSet<>();
        
        //e.g. for run-01, add run
        String word = "";
        try {
            word = (nodeLabel.matches(Util.VERB_PATTERN))? nodeLabel.substring(0, nodeLabel.lastIndexOf("-")) : nodeLabel;
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        ret.add(word.toLowerCase());
        
        //add result of manual rules defined above
        if (FIXED_RULES.containsKey(nodeLabel)) {
            ret.addAll(Arrays.asList(FIXED_RULES.get(nodeLabel)));
        }
        
        if (nodeLabel.matches("[a-z]+-[a-z].*")) {
            // e.g. for look-up-01, add look.
            if (nodeLabel.matches(Util.VERB_PATTERN)) {
                ret.add(nodeLabel.split("-")[0]);
            }
        }
        
        if (nodeLabel.matches("[0-9]+")) {
            String withoutZeroes = nodeLabel;
            while (withoutZeroes.endsWith("0")) {
                withoutZeroes = withoutZeroes.substring(0, withoutZeroes.length()-1);
            }
            ret.add(withoutZeroes);
        }
        
        return ret;
    }
    
    /**
     * Gets indirectly related words for a node label.
     * Contains the direct words. For now the direct words + pronouns.
     * @param nodeLabel
     * @return 
     */
    static Set<String> getIndirectWords(String nodeLabel) {
        Set<String> ret = getDirectWords(nodeLabel);
        if (FIXED_SECONDARY_RULES.containsKey(nodeLabel)) {
            ret.addAll(Arrays.asList(FIXED_SECONDARY_RULES.get(nodeLabel)));
        }
        if (nodeLabel.matches("[a-z]+-[a-z].*")) {
            if (!nodeLabel.matches(Util.VERB_PATTERN)) {
                String[] parts = nodeLabel.split("-");
                int max = Arrays.stream(parts).map(part -> part.length()).collect(Collectors.maxBy(Comparator.naturalOrder())).get();
                for (String part : parts) {
                    if (part.length() >= Math.min(max, 4)) {
                        ret.add(part);
                    }
                }
            }
        }
        if (nodeLabel.matches("[1-2][0-9][0-9][0-9]")) {
            ret.add(nodeLabel.substring(2));//years
        }
        return ret;
    }
    
}
