package de.saar.coli.amtools.astar;

public class Edge {
    private int from, to;
    private String label;

    // o[i,j]
    public static Edge parse(String s) {
        String[] parts = s.split("\\[|\\]|,");
        Edge ret = new Edge();
        ret.label = parts[0];
        ret.from = Integer.parseInt(parts[1]);
        ret.to = Integer.parseInt(parts[2]);
        return ret;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return "Edge{" + "from=" + from + ", to=" + to + ", label=" + label + '}';
    }
}
