/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.graphs;

import de.saar.coli.amrtagging.TokenRange;

/**
 *
 * @author matthias
 */
public class MRPAnchor {
    public int from;
    public int to;
    
    public MRPAnchor(){ //need constructor without arguments for JSON parsing
        
    }
    
    public MRPAnchor(int from, int to){
        this.from = from;
        this.to = to;
    }
    
    public static MRPAnchor fromTokenRange(TokenRange tr){
        return new MRPAnchor(tr.getFrom(),tr.getTo());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + this.from;
        hash = 71 * hash + this.to;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MRPAnchor other = (MRPAnchor) obj;
        if (this.from != other.from) {
            return false;
        }
        if (this.to != other.to) {
            return false;
        }
        return true;
    }
    
    
    
     
    
}
