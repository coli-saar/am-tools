/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.graphs;

import java.util.List;
import java.util.Objects;

/**
 *
 * @author matthias
 */
public class MRPNode {
    private int id;
    private String label;
    private List<String> properties;
    private List<String> values;
    private List<MRPAnchor> anchors;

    public MRPNode(){ //need constructor without arguments for JSON parsing
        
    }
    
    public MRPNode(int id, String label, List<String> properties, List<String> values, List<MRPAnchor> anchors){
        this.id = id;
        this.label = label;
        this.properties = properties;
        this.values = values;
        this.anchors = anchors;
    }
    
    /**
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @param label the label to set
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * @return the properties
     */
    public List<String> getProperties() {
        return properties;
    }

    /**
     * @param properties the properties to set
     */
    public void setProperties(List<String> properties) {
        this.properties = properties;
    }

    /**
     * @return the values
     */
    public List<String> getValues() {
        return values;
    }

    /**
     * @param values the values to set
     */
    public void setValues(List<String> values) {
        this.values = values;
    }

    /**
     * @return the anchors
     */
    public List<MRPAnchor> getAnchors() {
        return anchors;
    }

    /**
     * @param anchors the anchors to set
     */
    public void setAnchors(List<MRPAnchor> anchors) {
        this.anchors = anchors;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 43 * hash + this.id;
        hash = 43 * hash + Objects.hashCode(this.label);
        hash = 43 * hash + Objects.hashCode(this.properties);
        hash = 43 * hash + Objects.hashCode(this.values);
        hash = 43 * hash + Objects.hashCode(this.anchors);
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
        final MRPNode other = (MRPNode) obj;
        if (this.id != other.id) {
            return false;
        }
        if (!Objects.equals(this.label, other.label)) {
            return false;
        }
        if (!Objects.equals(this.properties, other.properties)) {
            return false;
        }
        if (!Objects.equals(this.values, other.values)) {
            return false;
        }
        if (!Objects.equals(this.anchors, other.anchors)) {
            return false;
        }
        return true;
    }  
       
}
