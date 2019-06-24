/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.graphs;

import java.util.Objects;

/**
 *
 * @author matthias
 */
public class MRPEdge {
    public int source;
    public int target;
    public String label;
    public String normal; //normalized label
    
    public MRPEdge(){ //need constructor without arguments for JSON parsing
        
    }

    public MRPEdge(int source, int target, String label, String normal) {
        this.source = source;
        this.target = target;
        this.label = label;
        this.normal = normal;
    }
    
    public MRPEdge(int source, int target, String label) {
        this(source,target,label,null);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + this.source;
        hash = 53 * hash + this.target;
        hash = 53 * hash + Objects.hashCode(this.label);
        hash = 53 * hash + Objects.hashCode(this.normal);
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
        final MRPEdge other = (MRPEdge) obj;
        if (this.source != other.source) {
            return false;
        }
        if (this.target != other.target) {
            return false;
        }
        if (!Objects.equals(this.label, other.label)) {
            return false;
        }
        if (!Objects.equals(this.normal, other.normal)) {
            return false;
        }
        return true;
    }
    
    
    
    
}
