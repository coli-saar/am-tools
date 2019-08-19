/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.saar.coli.amrtagging.mrp.graphs.MRPAnchor;

/**
 * Represents a span of tokens within a string. 0-based.
 * @author matthias
 */
public class TokenRange {

    private final int from;
    private final int to;
    
    
    public TokenRange(int from, int to){
        this.from = from;
        this.to = to;
    }
    
    public static TokenRange fromAnchor(MRPAnchor anchor){
        return new TokenRange(anchor.from, anchor.to);
    }
    
    @Override
    public boolean equals(Object r2obj){
        if (! r2obj.getClass().equals(this.getClass())){
            return false;
        }
        TokenRange r2 = (TokenRange) r2obj;
        return  from == r2.from && to == r2.to;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + this.from;
        hash = 97 * hash + this.to;
        return hash;
    }
    
    public static TokenRange fromString(String s){
        String[] parts = s.split(":");
        if (parts.length != 2){
            throw new IllegalArgumentException(s+ " is not a valid TokenRange");
        }
        try {
            return new TokenRange(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]));
        } catch (NumberFormatException ex){
            throw new IllegalArgumentException(s+ " is not a valid TokenRange");
        }

    }
    
    @Override
    public String toString(){
        return from + ":" + to;
    }
    
    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }
    
    
        /**
     * check if s1 is a subspan of s2
     * @param s1
     * @param s2
     * @return 
     */
    public static boolean isSubSpan(TokenRange s1, TokenRange s2){
        return s1.getFrom() >= s2.getFrom() && s1.getTo() <= s2.getTo();
    }
    
    public int length(){
        return to - from;
    }
    
    
    
}
