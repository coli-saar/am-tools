/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.up.ling.irtg.algebra.ParserException;

import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author matthias
 */
public class AmConllEntry {
    
    public static final String DEFAULT_NULL = "_";
    public static final String ATTRIBUTE_SEP = "|"; //TODO
    public static final String EQUALS = "=";

    public static final String TOKEN_RANGE_REPR = "TokenRange";
    
    public static final String IGNORE ="IGNORE";
    public static final String ROOT_SYM = "ROOT";
    
    public static final String LEX_MARKER = "--LEX--";



    private int id;
    private String form;
    private String replacement = DEFAULT_NULL;
    private String lemma=DEFAULT_NULL;
    private String pos=DEFAULT_NULL;
    private String ne = DEFAULT_NULL;
    private String delexSupertag = DEFAULT_NULL;
    private String lexLabel = DEFAULT_NULL;
    private Type type = null;
    private int head;
    private String edgeLabel = DEFAULT_NULL;
    private boolean aligned;
    
    private TokenRange range = null;

    private Map<String,String> furtherAttributes = new HashMap<>(); //TODO
    
    
    
    
    public static final int NOID = -2;
    
    
    public AmConllEntry(int id, String form){
        this.id = id;
        this.form = form;
    }
    
        /**
     * Delexicalizes the supertag and stores the information in the two columns.
     * @param supertagGraph
     * @param lexNodes 
     * @param lookup 
     * @throws de.up.ling.irtg.algebra.ParserException 
     */
    public void setSupertag(String supertagGraph, Set<String> lexNodes, SupertagDictionary lookup) throws ParserException{
        GraphAlgebra ga = new GraphAlgebra();
        SGraph supertag = ga.parseString(supertagGraph);
        String label = DEFAULT_NULL;
        if (lexNodes.size() > 1){
            throw new IllegalArgumentException("Currently only one lexical node per blob allowed");
        }
        for (String nodeName : lexNodes){
            label = supertag.getNode(nodeName).getLabel();
            supertag.getNode(nodeName).setLabel(LEX_MARKER);
        }
        this.setDelexSupertag(lookup.getRepr(supertag));
        this.setLexLabel(label);
        
    }
    
    public SGraph delexGraph() throws ParserException{
        GraphAlgebra ga = new GraphAlgebra();
        SGraph supertag = ga.parseString(this.getDelexSupertag());
        return supertag;
    }
    
    /**
     * Returns the relexicalized graph.
     * @return
     * @throws ParserException 
     */
    public SGraph relex() throws ParserException{
        SGraph supertag = delexGraph();
        for (String node : supertag.getAllNodeNames()){
            if (supertag.getNode(node) != null  && supertag.getNode(node).getLabel() != null && supertag.getNode(node).getLabel().equals(LEX_MARKER)){
                String reLex = Util.fixPunct(this.getReLexLabel()); //unfortunately, we have to fix punctuation :(
                supertag.getNode(node).setLabel(reLex); //Util.isiAMREscape(reLex) 
            }
        }
        return supertag;
    }

    public TokenRange getRange() {
        return range;
    }

    public void setRange(TokenRange range) {
        this.range = range;
    }
    
    
    

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getForm() {
        return form;
    }

    public void setForm(String form) {
        String pre = this.getReLexLabel();
        this.form = form;
        this.setLexLabel(pre); //updating the form might require to update the delexalized label
    }

    public String getLemma() {
        return lemma;
    }

    public void setLemma(String lemma) {
        String pre = this.getReLexLabel();
        this.lemma = lemma;
        this.setLexLabel(pre); //updating the lemma might require to update the delexalized label
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }

    public int getHead() {
        return head;
    }

    public void setHead(int head) {
        this.head = head;
    }

    /**
     * @return the replacement
     */
    public String getReplacement() {
        return replacement;
    }

    /**
     * @param replacement the replacement to set
     */
    public void setReplacement(String replacement) {
        String pre = this.getReLexLabel();
        this.replacement = replacement;
        this.setLexLabel(pre); //updating the replacement might require to update the delexalized label
    }

    /**
     * @return the ne
     */
    public String getNe() {
        return ne;
    }

    /**
     * @param ne the ne to set
     */
    public void setNe(String ne) {
        this.ne = ne;
    }

    /**
     * @return the delexSupertag
     */
    public String getDelexSupertag() {
        return delexSupertag;
    }

    /**
     * @param delexSupertag the delexSupertag to set
     */
    public void setDelexSupertag(String delexSupertag) {
        this.delexSupertag = delexSupertag;
    }
    

    

    /**
     * @return the raw lexLabel
     */
    public String getLexLabel() {
        return lexLabel;
    }

    
    public void setLexLabelWithoutReplacing(String lexLabel){
        this.lexLabel = lexLabel;
    }
    /**
     * @param lexLabel the lexLabel to set
     */
    public void setLexLabel(String lexLabel) {
        this.lexLabel = lexLabel;
        if (!lemma.equals(DEFAULT_NULL) && lexLabel.contains(this.lemma)){
            this.lexLabel = this.lexLabel.replace(this.lemma, "$LEMMA$");
        } else if (!form.equals(DEFAULT_NULL)  && lexLabel.contains(this.form)){
            this.lexLabel = this.lexLabel.replace(this.form, "$FORM$");
        } else if (!replacement.equals(DEFAULT_NULL)  && lexLabel.contains(this.replacement)){
            this.lexLabel = this.lexLabel.replace(this.replacement, "$REPL$");
        }
        
        if (!pos.equals(DEFAULT_NULL) && lexLabel.contains(pos)){
            this.lexLabel = this.lexLabel.replace(pos, "$POS$");
        }
    }
    
     /**
     * Returns the relexicalized lexical label
     * @return 
     */
    public String getReLexLabel() {
        String label = this.lexLabel;
        
        if (!lemma.equals(DEFAULT_NULL) && label.contains("$LEMMA$")){
            label = label.replace("$LEMMA$", this.lemma);
        }
        if (!form.equals(DEFAULT_NULL)  && label.contains("$FORM$")){
            label = label.replace("$FORM$", this.form);
        }
        if (!replacement.equals(DEFAULT_NULL)  && label.contains("$REPL$")){
            label = label.replace("$REPL$", this.replacement);
        }
        if (!pos.equals(DEFAULT_NULL)  && label.contains("$POS$")){
            label = label.replace("$POS$", this.pos);
        }
        
        return label;
    }

    /**
     * @return the type
     */
    public Type getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * @return the edgeLabel
     */
    public String getEdgeLabel() {
        return edgeLabel;
    }

    /**
     * @param edgeLabel the edgeLabel to set
     */
    public void setEdgeLabel(String edgeLabel) {
        this.edgeLabel = edgeLabel;
    }

    /**
     * @return the aligned
     */
    public boolean isAligned() {
        return aligned;
    }

    /**
     * @param aligned the aligned to set
     */
    public void setAligned(boolean aligned) {
        this.aligned = aligned;
    }

    /**
     * Sets (possibly overwrites) a further attribute
     * @param name
     * @param value
     */
    //TODO
    public void setFurtherAttribute(String name, String value){
        furtherAttributes.put(name,value);
    }

    //TODO
    public String getFurtherAttribute(String name){
        return  furtherAttributes.get(name);
    }

    
    @Override
    public String toString(){
        StringBuilder b = new StringBuilder();
        b.append(this.getId());
        b.append("\t");
        b.append(this.getForm());
        b.append("\t");
        b.append(this.getReplacement());
        b.append("\t");
        b.append(this.getLemma());
        b.append("\t");
        b.append(this.getPos());
        b.append("\t");
        b.append(this.getNe());
        b.append("\t");
        b.append(this.getDelexSupertag());
        b.append("\t");
        b.append(this.getLexLabel());
        b.append("\t");
        if (this.type == null){
            b.append(DEFAULT_NULL);
        } else {
            b.append(this.getType().toString());
        }
        b.append("\t");
        b.append(this.getHead());
        b.append("\t");
        b.append(this.getEdgeLabel());
        b.append("\t");
        b.append(this.isAligned());

        if (this.range != null && furtherAttributes.isEmpty()){
            b.append("\t");
            b.append(range.toString());
        }

        Map<String,String> attributes = new HashMap<>(furtherAttributes);
        if (this.range != null){
            attributes.put(TOKEN_RANGE_REPR,this.range.toString());
        }

        //create sorted list of further attributes to make this testable
        List<Map.Entry<String,String>> entries = new ArrayList<>(attributes.entrySet());
        entries.sort((Map.Entry<String,String> e1, Map.Entry<String,String> e2) -> e1.getKey().compareTo(e2.getKey()));

        if (! entries.isEmpty()){
            b.append("\t");
        }

        b.append(entries.stream().map((Map.Entry<String,String> entry) -> entry.getKey()+EQUALS+entry.getValue()).collect(Collectors.joining(ATTRIBUTE_SEP)));

        return b.toString();
        
    }
    

}
