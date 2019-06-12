/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds;

import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * This file contains utilities that handle subtleties in predicting the lexical label for EDS. In order to make this prediction as easy as possible, we would like to represent many lexical labels by their lemma.
 * This file helps to cover more.
 * @author matthias
 */
public class PostprocessLemmatize {
        public static final HashMap<String,String> lemmatize = new HashMap<String,String>() {{
            put("Corp.","corporation");
            put("U.S.","US");
            put("U.K.","UK");
            put("Mr.","mister");
            put("Dr.","doctor");
            put("%","percent");
            put("$","dollar");
            put("Inc.","inc");
            put("Co.","company");
            put("trading","trade");
            put("related","relate");
            put("January","Jan");
            put("February","Feb");
            put("March","Mar");
            put("April","Apr");
            put("June","Jun");
            put("August","Aug");
            put("September","Sep");
            put("October","Oct");
            put("November","Nov");
            put("December","Dec");
            put("Monday","Mon");
            put("Tuesday","Tue");
            put("Wednesday","Wed");
            put("Thursday","Thu");
            put("Friday","Fri");
            put("Saturday","Sat");
            put("Sunday","Sun");
            put("a","1");
            put("one","1");
            put("two","2");
            put("three","3");
            put("four","4");
            put("five","5");
            put("six","6");
            put("seven","7");
            put("eight","9");
            put("nine","9");
            put("ten","10");
            put("eleven","11");
            put("twelve","12");
            put("hundred","100");
            put("thousand","1000");
            put("million","1000000");
            put("billion","1000000000");
            put("trillion","1000000000000");
    }};
    
    public static final HashSet<String> lyExceptions = new HashSet<String>() {{
        add("daily");
        add("directly");
        add("drastically");
        add("early");
        add("especially");
        add("exactly");
        add("extremely");
        add("fairly");
        add("fully");
        add("genetically");
        add("hardly");
        add("largely");
        add("lately");
        add("likely");
        add("mainly");
        add("marginally");
        add("monthly");
        add("mostly");
        add("nearly");
        add("only");
        add("overly");
        add("particularly");
        add("partly");
        add("possibly");
        add("precisely");
        add("previously");
        add("primarily");
        add("probably");
        add("really");
        add("relatively");
        add("roughly");
        add("seasonally");
        add("sharply");
        add("shortly");
        add("solely");
        add("substantially");
        add("unfairly");
        add("unusually");
        add("virtually");
    }};
    
    
     /**
     * Adds words to the replacement column of the ConllSentence. These make it easier to predict the lexical label.
     * @param sent
     * @return 
     */
    public static void edsLemmaPostProcessing(ConllSentence sent){
        ArrayList<String> r = new ArrayList<>();
        for (int i = 0; i < sent.size(); i++){
            String lemma = sent.get(i).getLemma();
            if (lemmatize.containsKey(lemma)){
               sent.get(i).setReplacement(lemmatize.get(lemma));
            } else if (lemmatize.containsKey(lemma.toLowerCase())) {
                sent.get(i).setReplacement(lemmatize.get(lemma.toLowerCase()));
            }else if(sent.get(i).getPos().equals("RB") && lemma.endsWith("ly") && ! lyExceptions.contains(lemma) ) { //Adverbs that end with -ly but are not in the list of exceptions
                 String minusLy = lemma.replaceAll("ly$", "").replaceAll("i$", "y");
                 sent.get(i).setReplacement(minusLy);
            } else if (sent.get(i).getPos().equals("JJR") && lemma.endsWith("er")){ //longer, stronger, ...
                sent.get(i).setReplacement(lemma.replaceAll("er$",""));
            } else if (sent.get(i).getPos().equals("JJS") && lemma.endsWith("est")){ //longest, strongest
                sent.get(i).setReplacement(lemma.replaceAll("est$",""));
            }
        }
    }
    
    
}
