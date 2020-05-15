/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.saar.coli.irtg.experimental.astar.Or;
import de.saar.coli.irtg.experimental.astar.SupertagWithType;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @author matthias
 */
public class AmConllSentence extends ArrayList<AmConllEntry> {

    private int lineNr;
    private Map<String, String> attributes = new HashMap<>();

    public void setAttr(String key, String val) {
        if (key.contains("\n")) {
            throw new IllegalArgumentException("Linebreak forbidden in attribute key");
        }
        if (val.contains("\n")) {
            throw new IllegalArgumentException("Linebreak forbidden in attribute value");
        }
        attributes.put(key, val);
    }

    public String getAttr(String key) throws IndexOutOfBoundsException{
        return attributes.get(key);
    }

    public int getLineNr() {
        return lineNr;
    }

    public void setLineNr(int n) {
        lineNr = n;
    }

    public List<String> words() {
        ArrayList<String> r = new ArrayList<>();
        for (AmConllEntry e : this) {
            r.add(e.getForm());
        }
        return r;
    }

    public List<String> lemmas() {
        ArrayList<String> r = new ArrayList<>();
        for (AmConllEntry e : this) {
            r.add(e.getLemma());
        }
        return r;
    }
    
   public List<TokenRange> ranges() {
        ArrayList<TokenRange> r = new ArrayList<>();
        for (AmConllEntry e : this) {
            r.add(e.getRange());
        }
        return r;
    }
   
   public <T> List<T> getFields(Function <AmConllEntry, T> f) {
        ArrayList<T> r = new ArrayList<>();
        for (AmConllEntry e : this) {
            r.add(f.apply(e));
        }
        return r;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (AmConllEntry aThi : this) {
            b.append(aThi);
            b.append("\n");
        }
        return b.toString();
    }

    /**
     * Turns the output of the AlignmentTrackingAutomaton into an AmConllSentence
     * with just the words for now. The AmConllSentence will be delexicalized
     * using the alignment info from the instance. To make the supertags
     * consistent, it looks the graph fragment up in the given
     * SupertagDictionary.
     *
     * @param indexedAM
     * @param instance
     * @param lookup
     * @return
     * @throws de.up.ling.tree.ParseException
     * @throws de.up.ling.irtg.algebra.ParserException
     */
    public static AmConllSentence fromIndexedAMTerm(Tree<String> indexedAM, MRInstance instance, SupertagDictionary lookup) throws ParseException, ParserException {
        Map<Integer, Set<String>> index2lexNodes = new HashMap(); //maps an index to a set of lexical nodes for delexicalization.
        for (Alignment al : instance.getAlignments()) {
            index2lexNodes.put(al.span.start, al.lexNodes);
        }

        AmConllSentence conllSent = new AmConllSentence();
        int index = 1; //one-based indexing
        for (String word : instance.getSentence()) {
            AmConllEntry ent = new AmConllEntry(index, word);
            ent.setAligned(true); //for now, change that in the future - ml
            ent.setHead(0);
            ent.setEdgeLabel(AmConllEntry.IGNORE);
            conllSent.add(ent);

            index++;
        }

        for (Tree<String> t : indexedAM.getAllNodes()) {

            if (t.getChildren().isEmpty()) {
                String[] graphAndType = t.getLabel().split(ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP);
                String[] positionAndFragment = graphAndType[0].split(AlignmentTrackingAutomaton.SEPARATOR);
                int position = Integer.parseInt(positionAndFragment[0]);
                conllSent.get(position).setAligned(true);
                conllSent.get(position).setSupertag(positionAndFragment[1], index2lexNodes.get(position), lookup); //set supertag and delexcalize it.
                conllSent.get(position).setType(new ApplyModifyGraphAlgebra.Type(graphAndType[1]));

                if (t.equals(indexedAM)) { //we are at the root (no term, just single constant)
                    conllSent.get(position).setEdgeLabel(AmConllEntry.ROOT_SYM);
                }

            } else if (t.getChildren().size() == 2) {

                String[] fromToAndOp = t.getLabel().split(AlignmentTrackingAutomaton.SEPARATOR);
                String[] fromTo = fromToAndOp[0].split(AlignmentTrackingAutomaton.FROM_TO_SEPARATOR);
                int from = Integer.parseInt(fromTo[0]);
                int to = Integer.parseInt(fromTo[1]);
                conllSent.get(to).setHead(from + 1); //within conll we use 1 addressing
                conllSent.get(to).setEdgeLabel(fromToAndOp[1]);

                if (t.equals(indexedAM)) {
                    conllSent.get(from).setEdgeLabel(AmConllEntry.ROOT_SYM);
                }
            }
        }
        return conllSent;
    }

    /*
    public Tree<String> toAMTerm() {
        throw new UnsupportedOperationException();
    }
    */

    public void addPos(List<String> pos) {
        if (pos.size() != this.size()) {
            throw new IllegalArgumentException("Size of pos list must be equal to sentence length");
        }
        for (int i = 0; i < pos.size(); i++) {
            this.get(i).setPos(pos.get(i));
        }
    }

    public void addLemmas(List<String> lemmas) {
        if (lemmas.size() != this.size()) {
            throw new IllegalArgumentException("Size of lemma list must be equal to sentence length");
        }
        for (int i = 0; i < lemmas.size(); i++) {
            this.get(i).setLemma(lemmas.get(i));
        }
    }

    public void addReplacement(List<String> replacements) {
        addReplacement(replacements, true);
    }
    
    /**
     * Adds replacement tokens
     * @param replacements
     * @param caseSensitive whether or not tokens count as equal if they're equal up to case
     */
    public void addReplacement(List<String> replacements, boolean caseSensitive){
        if (replacements.size() != this.size()) {
            throw new IllegalArgumentException("Size of replacement list must be equal to sentence length");
        }
        for (int i = 0; i < replacements.size(); i++) {
            if (!this.get(i).getForm().toLowerCase().equals(replacements.get(i).toLowerCase())) {
                this.get(i).setReplacement(replacements.get(i));
            } else if (caseSensitive && !this.get(i).getForm().equals(replacements.get(i))){
                this.get(i).setReplacement(replacements.get(i));
            }
        }
    }

    public void addNEs(List<String> nes) {
        if (nes.size() != this.size()) {
            throw new IllegalArgumentException("Size of NE tag list must be equal to sentence length");
        }
        for (int i = 0; i < nes.size(); i++) {
            this.get(i).setNe(nes.get(i));
        }
    }
    
    /**
     * Sets token ranges.
     * @param ranges 
     */
    public void addRanges(List<TokenRange> ranges) {
        if (ranges.size() != this.size()) {
            throw new IllegalArgumentException("Size of TokenRange list must be equal to sentence length");
        }
        for (int i = 0; i < ranges.size(); i++) {
            this.get(i).setRange(ranges.get(i));
        }
    }
    
    

    /**
     * Writes a list of ConllSentences to a file.
     * 
     * @see #write(java.io.Writer, java.util.List) 
     *
     * @param filename
     * @param sents
     * @throws IOException
     */
    public static void writeToFile(String filename, List<AmConllSentence> sents) throws IOException {
        write(new FileWriter(filename), sents);
    }
    
    /**
     * Writes a list of ConllSentences to a writer.<p>
     * 
     * TODO: might want to set the
     * line of the objects to where it was written to file.
     *
     * @param writer
     * @param sents
     * @throws IOException
     */
    public static void write(Writer writer, List<AmConllSentence> sents) throws IOException {
        BufferedWriter bw = new BufferedWriter(writer);
        
        for (AmConllSentence s : sents) {
            for (String key : s.attributes.keySet()) {
                bw.write("#");
                bw.write(key);
                bw.write(":");
                bw.write(s.getAttr(key));
                bw.write("\n");
            }
            bw.write(s.toString());
            bw.write("\n");
        }
        bw.close();
    }
    
    /**
     * Reads a CoNLL corpus from a Reader and returns the list of instances.
     * 
     * @param reader
     * @return
     * @throws IOException
     * @throws ParseException 
     */
    public static List<AmConllSentence> read(Reader reader) throws IOException, ParseException {
        BufferedReader br = new BufferedReader(reader);
        String l = "";
        ArrayList<AmConllSentence> sents = new ArrayList<>();
        AmConllSentence sent = new AmConllSentence();
        int lineNr = 1;
        sent.setLineNr(lineNr);
        while ((l = br.readLine()) != null) {
            if (l.startsWith("#")) {
                if (l.contains(":")) {
                    int index = l.indexOf(":");
                    sent.setAttr(l.substring(1, index), l.substring(index + 1));
                }
            } else if (l.replaceAll("\t", "").length() > 0) {
                String[] attr = l.split("\t");
                AmConllEntry c = new AmConllEntry(Integer.parseInt(attr[0]), attr[1]);
                c.setReplacement(attr[2]);
                c.setLemma(attr[3]);
                c.setPos(attr[4]);
                c.setNe(attr[5]);
                c.setDelexSupertag(attr[6]);
                c.setLexLabelWithoutReplacing(attr[7]);
                if (!attr[8].equals("_")) {
                    c.setType(new ApplyModifyGraphAlgebra.Type(attr[8]));
                }

                c.setHead(Integer.parseInt(attr[9]));
                c.setEdgeLabel(attr[10]);
                c.setAligned(Boolean.valueOf(attr[11]));
                if (attr.length > 12){
                    //we have a last column
                    //try if this is a token range
                    try {
                        c.setRange(TokenRange.fromString(attr[12]));
                    } catch (IllegalArgumentException ex){
                        //apparently, it's not a token range
                        for (String keyVal : attr[12].split(Pattern.quote(AmConllEntry.ATTRIBUTE_SEP))){
                            String[] keyVals = keyVal.split(Pattern.quote(AmConllEntry.EQUALS));
                            if (keyVals.length == 0) throw new IllegalArgumentException("Illegal further attribute "+attr[12]+" in sentence with id "+sent.getId());

                            String key = keyVals[0];
                            String value = Arrays.stream(keyVals).skip(1).collect(Collectors.joining(AmConllEntry.EQUALS));
                            // special treatment of token ranges
                            if (key.equals(AmConllEntry.TOKEN_RANGE_REPR)){
                                c.setRange(TokenRange.fromString(value));
                            } else {
                                c.setFurtherAttribute(key, value);
                            }

                        }
                    }

                }

                //System.out.println(c);
                sent.add(c);
            } else {
                sents.add(sent);
                sent = new AmConllSentence();
                sent.setLineNr(lineNr);
            }
            lineNr++;
        }
        if (!sent.isEmpty()) {
            sents.add(sent);
        }
        br.close();
        return sents;
    }

    /**
     * Reads a CoNLL corpus from a file and returns the list of instances.
     *
     * @param filename
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static List<AmConllSentence> readFromFile(String filename) throws FileNotFoundException, IOException, ParseException {
        return read(new FileReader(filename));
    }

    /**
     * Converts the given AM term into an AM-CoNLL dependency tree. The AM term is a tree with two types
     * of node labels. Leaves are labeled with supertags ({@link SupertagWithType}); all other nodes are
     * labeled with strings representing operations of the AM algebra (e.g. "APP_o").
     *
     * @param amTerm
     * @param leafOrderToStringOrder
     */
    public void setDependenciesFromAmTerm(Tree<Or<String, SupertagWithType>> amTerm, List<Integer> leafOrderToStringOrder) {
        MutableInteger nextLeafPosition = new MutableInteger(0);
        
        // all tokens that are not mentioned in the term will be ignored
        for( AmConllEntry e : this ) {
            e.setHead(0);
            e.setEdgeLabel(AmConllEntry.IGNORE);
            e.setDelexSupertag(AmConllEntry.DEFAULT_NULL);
            e.setType(null);
        }
        
        // perform left-to-right DFS over term and assign incoming edges
        int rootPos = amTerm.dfs((Tree<Or<String,SupertagWithType>> node, List<Integer> childrenValues) -> {
            if( childrenValues.isEmpty() ) {
                // leaf
                int leafPosition = nextLeafPosition.incValue();
                int stringPosition = leafOrderToStringOrder.get(leafPosition);
                
                AmConllEntry entry = this.get(stringPosition);
                assert ! node.getLabel().isLeft();
                SupertagWithType stt = node.getLabel().getRightValue();
                entry.setDelexSupertag(stt.getGraph().toIsiAmrStringWithSources());
                entry.setType(stt.getType());
                
                return stringPosition;
            } else {
                assert childrenValues.size() == 2;
                int headStringPosition = childrenValues.get(0);
                int secondaryStringPosition = childrenValues.get(1);
                assert node.getLabel().isLeft();
                String edgeLabel = node.getLabel().getLeftValue();
                AmConllEntry childEntry = this.get(secondaryStringPosition);
                
                childEntry.setEdgeLabel(edgeLabel);
                childEntry.setHead(headStringPosition+1);  // convert 0-based (in array) to 1-based (in CoNLL file)
                
                return headStringPosition;
            }
        });
        
        // mark root token as root
        AmConllEntry rootEntry = this.get(rootPos);
        rootEntry.setHead(0);
        rootEntry.setEdgeLabel(AmConllEntry.ROOT_SYM);
    }

    public String getId() {
        if( getAttr("id") != null ) {
            return getAttr("id");
        } else {
            return "#NO-ID";
        }
    }
    
    public void setId(String newId){
        setAttr("id", newId);
    }

    /**
     * Given an index i, returns all entries that have head i. Current implementation is somewhat inefficient,
     * might need reimplementation if used in runtime-sensitive locations.
     * @param i the index of the word who's children to get, 0-based
     * @return the children of entry i (which have i as head), in sentence order
     */
    public List<AmConllEntry> getChildren(int i) {
        //when called for all indices, this is quadratic. Caching and running through all entries once could make it linear, but should be ok for now.
        return this.stream().filter(entry -> entry.getHead()-1 == i).collect(Collectors.toList());
    }

    /**
     * Returns the AMConllEntry that is the head of i (i is 0-based).
     * @param i 0-based
     * @return 
     */
    public AmConllEntry getParent(int i) {
        int parentPosition = this.get(i).getHead()-1; //getHead is 1-based, but need 0-based for this.get
        if (parentPosition < this.size() && parentPosition >= 0) {
            return this.get(parentPosition);
        } else {
            return null;
        }
    }

    /**
     * clones this sentence by cloning each entry and copying each attribute.
     * @Author JG
     * @return
     */
    @Override
    public Object clone() {
        AmConllSentence clone = new AmConllSentence();
        // clone each entry
        clone.addAll(this.stream().map(entry -> (AmConllEntry)entry.clone()).collect(Collectors.toList()));
        for (String key : this.attributes.keySet()) {
            clone.setAttr(key, this.attributes.get(key));
        }
        clone.lineNr = this.lineNr;
        //modCount is some weird list thing, may as well copy I suppose
        clone.modCount = this.modCount;
        return clone;
    }
}
